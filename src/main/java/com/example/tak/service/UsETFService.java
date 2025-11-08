package com.example.tak.service;

import com.example.tak.domain.ETF;
import com.example.tak.dto.response.CurrentPriceDataDTO;
import com.example.tak.repository.EtfRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class UsETFService {
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final EtfRepository etfRepository;

    @Value("${ACCESS_TOKEN}")
    private String accessToken;

    @Value("${APP_KEY}")
    private String appKey;

    @Value("${APP_SECRET}")
    private String appSecret;

    // 현재 체결가 가져오기 (비동기)
    public Mono<CurrentPriceDataDTO> getCurrentPriceData(String ticker) {
        // DB에서 ETF 정보를 먼저 조회
        ETF etf = etfRepository.findByTicker(ticker)
                .orElseThrow(() -> new RuntimeException("ETF 정보를 찾을 수 없습니다: " + ticker));

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("openapi.koreainvestment.com")
                        .port(9443)
                        .path("/uapi/overseas-price/v1/quotations/price")
                        .queryParam("AUTH", "")
                        .queryParam("EXCD", "AMS")
                        .queryParam("SYMB", ticker)
                        .build())
                .headers(h -> {
                    h.set("Content-Type", "application/json");
                    h.set("authorization", "Bearer " + accessToken);
                    h.set("appkey", appKey);
                    h.set("appsecret", appSecret);
                    h.set("tr_id", "HHDFS00000300");
                })
                .retrieve()
                .bodyToMono(String.class)
                .map(responseBody -> parseCurrentPrice(responseBody, etf))
                .onErrorMap(e -> new RuntimeException("US ETF 현재가 조회 실패: " + e.getMessage(), e));
    }

    // 1개월 전 가격 가져오기 (비동기)
    public Mono<Double> getOneMonthAgoPrice(String ticker) {
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String endDate = today.format(formatter);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("openapi.koreainvestment.com")
                        .port(9443)
                        .path("/uapi/overseas-price/v1/quotations/dailyprice")
                        .queryParam("AUTH", "")
                        .queryParam("EXCD", "AMS")
                        .queryParam("SYMB", ticker)
                        .queryParam("GUBN", "2")
                        .queryParam("BYMD", endDate)
                        .queryParam("MODP", "1")
                        .build())
                .headers(h -> {
                    h.set("Content-Type", "application/json");
                    h.set("authorization", "Bearer " + accessToken);
                    h.set("appkey", appKey);
                    h.set("appsecret", appSecret);
                    h.set("tr_id", "HHDFS76240000");
                })
                .retrieve()
                .bodyToMono(String.class)
                .map(responseBody -> parseOneMonthAgoPrice(responseBody, today))
                .onErrorMap(e -> new RuntimeException("US ETF 1개월 전 가격 조회 실패: " + e.getMessage(), e));
    }

    // 현재가 데이터 파싱
    private CurrentPriceDataDTO parseCurrentPrice(String responseBody, ETF etf) {
        try {
            JsonNode responseJson = objectMapper.readTree(responseBody);
            System.out.println("US API Response (현재가 데이터): " + responseJson);

            if (!"0".equals(responseJson.path("rt_cd").asText())) {
                String errorMessage = responseJson.path("msg1").asText("Unknown error");
                throw new RuntimeException("현재가 조회 실패: " + errorMessage);
            }

            JsonNode output = responseJson.get("output");
            if (output == null) {
                throw new RuntimeException("현재가 데이터가 없습니다.");
            }

            Double lastPrice = output.has("last") ? output.get("last").asDouble() : null;
            Double priceDiff = output.has("diff") ? output.get("diff").asDouble() : 0.0;
            Double changeRate = output.has("rate") ? Double.parseDouble(output.get("rate").asText()) : 0.0;
            String signCode = output.has("sign") ? output.get("sign").asText() : null;

            String priceSign = convertSignCode(signCode);

            // DB에서 NAV 가져와 병합
            return CurrentPriceDataDTO.builder()
                    .currentPrice(lastPrice)
                    .prdyVrss(priceDiff)
                    .prdyCtrt(changeRate)
                    .prdyVrssSign(priceSign)
                    .nav(etf.getINav() != null ? etf.getINav() : 0.0)
                    .navPrdyVrss(0.0) // US ETF는 NAV 변동 정보가 없음
                    .navPrdyVrssSign("N/A")
                    .navPrdyCtrt(0.0)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("현재가 데이터 파싱 오류 발생: " + e.getMessage(), e);
        }
    }

    // 1개월 전 가격 파싱
    private Double parseOneMonthAgoPrice(String responseBody, LocalDate today) {
        try {
            JsonNode responseJson = objectMapper.readTree(responseBody);
            System.out.println("US API Response (1개월 전 가격): " + responseJson);

            if (!"0".equals(responseJson.path("rt_cd").asText())) {
                String errorMessage = responseJson.path("msg1").asText("Unknown error");
                throw new RuntimeException("1개월 전 가격 조회 실패: " + errorMessage);
            }

            JsonNode output2 = responseJson.get("output2");
            if (output2 == null || !output2.isArray() || output2.isEmpty()) {
                throw new RuntimeException("1개월 전 가격 데이터가 없습니다.");
            }

            LocalDate oneMonthAgo = today.minusMonths(1);

            // 1개월 전 날짜에 가장 가까운 데이터를 가져옴
            for (JsonNode data : output2) {
                String dateStr = data.get("xymd").asText();
                LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
                if (!date.isAfter(oneMonthAgo)) {
                    Double oneMonthAgoPrice = data.get("clos").asDouble();
                    System.out.println("US ETF 1개월 전 가격 (파싱 후): " + oneMonthAgoPrice);
                    return oneMonthAgoPrice; // 종가
                }
            }

            throw new RuntimeException("1개월 전 가격 데이터가 없습니다.");

        } catch (Exception e) {
            throw new RuntimeException("1개월 전 가격 파싱 오류 발생: " + e.getMessage(), e);
        }
    }

    // 부호 코드 변환 메서드
    private String convertSignCode(String signCode) {
        if (signCode == null) {
            return "알 수 없음";
        }
        switch (signCode) {
            case "1":
            case "2":
                return "상승";
            case "3":
                return "보합";
            case "4":
            case "5":
                return "하락";
            default:
                return "알 수 없음";
        }
    }
}
