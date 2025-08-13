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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EtfGetListService {
    private final UsPriceService usPriceService;
    private final PriceService priceService;
    private final EtfDataRepository etfDataRepository;

    public Page<EtfResponseDTO.CompareEtfDto> getEtfsByFilter(String filter, Pageable pageable) {
        Page<ETF> etfs;

        try {
            Category category = Category.fromName(filter);
            etfs = etfDataRepository.findByCategory(category, pageable);
        } catch (IllegalArgumentException e) {
            try {
                Nation nation = Nation.fromName(filter);

                if (nation == Nation.KOREA) {
                    etfs = etfDataRepository.findByTickerIsNull(pageable);
                } else if (nation == Nation.US) {
                    etfs = etfDataRepository.findByTickerIsNotNull(pageable);
                } else {
                    return Page.empty(pageable);
                }
            } catch (IllegalArgumentException ex) {
                return Page.empty(pageable);
            }
        }
        return etfs.map(this::toCompareEtfDto);
    }

    private EtfResponseDTO.CompareEtfDto toCompareEtfDto(ETF etf) {
        CurrentPriceDataDTO priceData = getCurrentPrice(etf);

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
    }

    private CurrentPriceDataDTO getCurrentPrice(ETF etf) {
        System.out.println("ETF Nation: " + etf.getNation());
        System.out.println("ETF Number (KOREA): " + etf.getEtfNum());
        System.out.println("ETF Ticker (US): " + etf.getTicker());

        try {
            if (etf.getNation() == Nation.KOREA) {
                return priceService.getCurrentPriceData(etf.getEtfNum());
            } else if (etf.getNation() == Nation.US) {
                return usPriceService.getCurrentPriceData(etf.getTicker());
            } else {
                throw new EtfHandler(ErrorStatus.ETF_NOT_FOUND);
            }
        } catch (Exception e) {
            System.err.println("Error retrieving current price for ETF: " + etf.getName() + ", " + e.getMessage());
            // 기본값 반환
            return CurrentPriceDataDTO.builder()
                    .currentPrice(0.0)
                    .prdyVrss(0.0)
                    .prdyCtrt(0.0)
                    .prdyVrssSign("N/A")
                    .build();
        }
    }
}
