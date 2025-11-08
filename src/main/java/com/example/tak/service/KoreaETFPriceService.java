package com.example.tak.service;

import com.example.tak.dto.response.CurrentPriceDataDTO;
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
public class KoreaETFPriceService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${ACCESS_TOKEN}")
    private String accessToken;

    @Value("${APP_KEY}")
    private String appKey;

    @Value("${APP_SECRET}")
    private String appSecret;

    // 1개월 전 가격 가져오기 (비동기)
    public Mono<Double> getOneMonthAgoPrice(String etfNum) {
        LocalDate today = LocalDate.now();
        LocalDate oneMonthAgo = today.minusMonths(1);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String startDate = oneMonthAgo.format(formatter);
        String endDate = today.format(formatter);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("openapi.koreainvestment.com")
                        .port(9443)
                        .path("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
                        .queryParam("fid_cond_mrkt_div_code", "J")
                        .queryParam("fid_input_iscd", etfNum)
                        .queryParam("fid_input_date_1", startDate)
                        .queryParam("fid_input_date_2", endDate)
                        .queryParam("fid_period_div_code", "D")
                        .queryParam("fid_org_adj_prc", "1")
                        .build())
                .headers(h -> {
                    h.set("authorization", "Bearer " + accessToken);
                    h.set("appkey", appKey);
                    h.set("appsecret", appSecret);
                    h.set("tr_id", "FHKST03010100");
                    h.set("custtype", "P");
                })
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseOneMonthAgoPriceFromBody);
    }

    // 현재가 및 추가 데이터 가져오기 (비동기)
    public Mono<CurrentPriceDataDTO> getCurrentPriceData(String etfNum) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("openapi.koreainvestment.com")
                        .port(9443)
                        .path("/uapi/etfetn/v1/quotations/inquire-price")
                        .queryParam("fid_cond_mrkt_div_code", "J")
                        .queryParam("fid_input_iscd", etfNum)
                        .build())
                .headers(h -> {
                    h.set("authorization", "Bearer " + accessToken);
                    h.set("appkey", appKey);
                    h.set("appsecret", appSecret);
                    h.set("tr_id", "FHPST02400000");
                    h.set("custtype", "P");
                })
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseCurrentPriceDataFromBody);
    }

    // ===== 파싱 메서드 (String 본문 기반) =====
    private Double parseOneMonthAgoPriceFromBody(String body) {
        try {
            JsonNode responseJson = objectMapper.readTree(body);
            System.out.println("API Response (1개월 전 가격): " + responseJson);

            if (!"0".equals(responseJson.path("rt_cd").asText())) {
                String errorMessage = responseJson.path("msg1").asText("Unknown error");
                throw new RuntimeException("1개월 전 가격 조회 실패: " + errorMessage);
            }

            JsonNode output2 = responseJson.get("output2");
            if (output2 == null || !output2.isArray() || output2.isEmpty()) {
                throw new RuntimeException("1개월 전 가격 데이터가 없습니다.");
            }

            JsonNode oldestData = output2.get(output2.size() - 1);
            if (oldestData == null || !oldestData.has("stck_clpr")) {
                throw new RuntimeException("1개월 전 가격 데이터에 유효한 값이 없습니다.");
            }

            Double oneMonthAgoPrice = oldestData.get("stck_clpr").asDouble();
            System.out.println("1개월 전 가격 (파싱 후): " + oneMonthAgoPrice);
            return oneMonthAgoPrice;

        } catch (Exception e) {
            throw new RuntimeException("1개월 전 가격 파싱 오류 발생: " + e.getMessage(), e);
        }
    }

    private CurrentPriceDataDTO parseCurrentPriceDataFromBody(String body) {
        try {
            JsonNode responseJson = objectMapper.readTree(body);
            System.out.println("API Response (현재가 데이터): " + responseJson);

            if (!"0".equals(responseJson.path("rt_cd").asText())) {
                String errorMessage = responseJson.path("msg1").asText("Unknown error");
                throw new RuntimeException("현재가 조회 실패: " + errorMessage);
            }

            JsonNode output = responseJson.get("output");
            if (output == null) {
                throw new RuntimeException("현재가 데이터가 없습니다.");
            }

            Double stck_prpr = output.has("stck_prpr") ? output.get("stck_prpr").asDouble() : null;
            String prdy_vrss_sign_code = output.has("prdy_vrss_sign") ? output.get("prdy_vrss_sign").asText() : null;
            Double prdy_vrss = output.has("prdy_vrss") ? output.get("prdy_vrss").asDouble() : null;
            Double prdy_ctrt = output.has("prdy_ctrt") ? output.get("prdy_ctrt").asDouble() : null;
            Double nav = output.has("nav") ? output.get("nav").asDouble() : null;
            String nav_prdy_vrss_sign_code = output.has("nav_prdy_vrss_sign") ? output.get("nav_prdy_vrss_sign").asText() : null;
            Double nav_prdy_vrss = output.has("nav_prdy_vrss") ? output.get("nav_prdy_vrss").asDouble() : null;
            Double nav_prdy_ctrt = output.has("nav_prdy_ctrt") ? output.get("nav_prdy_ctrt").asDouble() : null;
            Double trc_errt = output.has("trc_errt") ? output.get("trc_errt").asDouble() : null; // 필요 시 사용

            String prdy_vrss_sign = convertSignCode(prdy_vrss_sign_code);
            String nav_prdy_vrss_sign = convertSignCode(nav_prdy_vrss_sign_code);

            return CurrentPriceDataDTO.builder()
                    .currentPrice(stck_prpr)
                    .prdyVrssSign(prdy_vrss_sign)
                    .prdyVrss(prdy_vrss)
                    .prdyCtrt(prdy_ctrt)
                    .nav(nav)
                    .navPrdyVrssSign(nav_prdy_vrss_sign)
                    .navPrdyVrss(nav_prdy_vrss)
                    .navPrdyCtrt(nav_prdy_ctrt)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("현재가 데이터 파싱 오류 발생: " + e.getMessage(), e);
        }
    }

    // 부호 코드 변환
    private String convertSignCode(String signCode) {
        if (signCode == null) return null;
        switch (signCode) {
            case "1":
            case "2":
                return "상승";
            case "3":
                return "동일";
            case "4":
            case "5":
                return "하락";
            default:
                return "알 수 없음";
        }
    }
}
