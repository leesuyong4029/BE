package com.example.tak.service;

import com.example.tak.common.Category;
import com.example.tak.common.Nation;
import com.example.tak.config.response.code.resultCode.ErrorStatus;
import com.example.tak.config.response.exception.handler.EtfHandler;
import com.example.tak.domain.ETF;
import com.example.tak.dto.response.CurrentPriceDataDTO;
import com.example.tak.dto.response.EtfResponseDTO;
import com.example.tak.repository.EtfDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ETFGetService {
    private final UsETFService usETFService;
    private final KoreaETFPriceService koreaETFPriceService;
    private final EtfDataRepository etfDataRepository;
    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;

    public Mono<Page<EtfResponseDTO.CompareEtfDto>> getEtfsByFilter(String filter, Pageable pageable) {
        final Page<ETF> etfs = getEtfsByFilterType(filter, pageable);

        if (etfs.isEmpty()) {
            return Mono.just(Page.empty(pageable));
        }

        // 각 ETF를 비동기적으로 변환하고 결과를 모음
        return Flux.fromIterable(etfs.getContent())
                .flatMap(this::toCompareEtfDto)
                .collectList()
                .map(dtoList -> new PageImpl<>(dtoList, pageable, etfs.getTotalElements()));
    }

    private Page<ETF> getEtfsByFilterType(String filter, Pageable pageable) {
        try {
            Category category = Category.fromName(filter);
            return etfDataRepository.findByCategory(category, pageable);
        } catch (IllegalArgumentException e) {
            try {
                Nation nation = Nation.fromName(filter);

                if (nation == Nation.KOREA) {
                    return etfDataRepository.findByTickerIsNull(pageable);
                } else if (nation == Nation.US) {
                    return etfDataRepository.findByTickerIsNotNull(pageable);
                } else {
                    return Page.empty(pageable);
                }
            } catch (IllegalArgumentException ex) {
                return Page.empty(pageable);
            }
        }
    }

    private Mono<EtfResponseDTO.CompareEtfDto> toCompareEtfDto(ETF etf) {
        return getCurrentPrice(etf)
                .map(priceData -> {
                    double prdyCtrt = priceData.getPrdyCtrt() == null ? 0.0 : priceData.getPrdyCtrt();
                    String profitRate = String.format("%.2f%%", prdyCtrt);
                    boolean isPositive = prdyCtrt >= 0;

                    Long priceLong = priceData.getCurrentPrice() == null
                            ? null
                            : priceData.getCurrentPrice().longValue();

                    return EtfResponseDTO.CompareEtfDto.builder()
                            .etfId(etf.getId())
                            .category(etf.getCategory())
                            .sector(etf.getSector())
                            .name(etf.getName())
                            .fee(etf.getFee())
                            .ticker(etf.getTicker())
                            .etfNum(etf.getEtfNum())
                            .price(priceLong)
                            .profitRate(profitRate)
                            .isPositive(isPositive)
                            .build();
                })
                .onErrorReturn(createDefaultCompareEtfDto(etf)); // 에러 발생 시 기본값 반환
    }

    private Mono<CurrentPriceDataDTO> getCurrentPrice(ETF etf) {
        // 캐시 키 생성 (한국 : etfNum, 미국 : ticker)
        String identifier = etf.getNation() == Nation.KOREA ? etf.getEtfNum() : etf.getTicker();
        if (identifier == null) {
            return Mono.just(createDefaultCurrentPriceData());
        }

        String cacheKey = "etf:price:" + etf.getNation().name().toLowerCase() + ":" + identifier;

        System.out.println("ETF Nation: " + etf.getNation());
        System.out.println("ETF Identifier: " + identifier);

        // 캐시에서 먼저 조회 시도
        return reactiveRedisTemplate.opsForValue().get(cacheKey)
                .cast(CurrentPriceDataDTO.class)
                .doOnNext(cached -> System.out.println("캐시에서 조회됨: " + identifier))

                // 캐시 미스 시 API 호출하고 캐시에 저장
                .switchIfEmpty(
                fetchPrice(etf)
                        .doOnNext(apiData -> System.out.println("API에서 조회됨: " + identifier))
                        .flatMap(priceData ->
                                // API 결과를 캐시에 저장 (5분 TTL)
                                reactiveRedisTemplate.opsForValue()
                                        .set(cacheKey, priceData, Duration.ofMinutes(5))
                                        .doOnNext(result -> System.out.println("캐시 저장 완료 : " + identifier))
                                        .thenReturn(priceData)
                                )
                        .onErrorReturn(createDefaultCurrentPriceData())
                );
    }

    private Mono<CurrentPriceDataDTO> fetchPrice(ETF etf) {
        System.out.println("API 호출 대상 - Nation: " + etf.getNation() +
                ", ETF Number: " + etf.getEtfNum() +
                ", Ticker: " + etf.getTicker());

        if (etf.getNation() == Nation.KOREA && etf.getEtfNum() != null) {
            return koreaETFPriceService.getCurrentPriceData(etf.getEtfNum());
        } else if (etf.getNation() == Nation.US && etf.getTicker() != null) {
            return usETFService.getCurrentPriceData(etf.getTicker());
        } else {
            System.err.println("알 수 없는 ETF 타입 : " + etf.getName() + ", Nation: " + etf.getNation());
            return Mono.just(createDefaultCurrentPriceData());
        }

    }

    // 기본값 생성 메서드들
    private CurrentPriceDataDTO createDefaultCurrentPriceData() {
        return CurrentPriceDataDTO.builder()
                .currentPrice(0.0)
                .prdyVrss(0.0)
                .prdyCtrt(0.0)
                .prdyVrssSign("N/A")
                .nav(0.0)
                .navPrdyVrss(0.0)
                .navPrdyVrssSign("N/A")
                .navPrdyCtrt(0.0)
                .build();
    }

    private EtfResponseDTO.CompareEtfDto createDefaultCompareEtfDto(ETF etf) {
        return EtfResponseDTO.CompareEtfDto.builder()
                .etfId(etf.getId())
                .category(etf.getCategory())
                .sector(etf.getSector())
                .name(etf.getName())
                .fee(etf.getFee())
                .ticker(etf.getTicker())
                .etfNum(etf.getEtfNum())
                .price(0L)
                .profitRate("0.00%")
                .isPositive(true)
                .build();
    }
}