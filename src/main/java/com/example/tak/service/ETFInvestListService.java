package com.example.tak.service;

import com.example.tak.common.Category;
import com.example.tak.common.Nation;
import com.example.tak.domain.ETF;
import com.example.tak.dto.response.ETFInvestListResponseDTO;
import com.example.tak.repository.EtfDataRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ETFInvestListService {

    private final EtfDataRepository etfDataRepository;

    public ETFInvestListService(EtfDataRepository etfDataRepository) {
        this.etfDataRepository = etfDataRepository;
    }

    public Page<ETFInvestListResponseDTO> getETFInvestList(String filter, Pageable pageable) {
        Page<ETF> etfEntities;

        try {
            // filter를 Category로 해석
            Category category = Category.fromName(filter);
            etfEntities = etfDataRepository.findByCategory(category, pageable);
        } catch (IllegalArgumentException e) {
            try {
                // filter를 Nation으로 해석
                Nation nation = Nation.fromName(filter);

                if (nation == Nation.KOREA) {
                    etfEntities = etfDataRepository.findByTickerIsNull(pageable);
                } else if (nation == Nation.US) {
                    etfEntities = etfDataRepository.findByTickerIsNotNull(pageable);
                } else {
                    // Nation이 ALL이거나 기타 값일 경우 빈 리스트 반환
                    return Page.empty(pageable);
                }
            } catch (IllegalArgumentException ex) {
                // filter 값이 Category나 Nation에 해당하지 않는 경우 빈 리스트 반환
                return Page.empty(pageable);
            }
        }

        return etfEntities.map(this::mapToResponseDTO);
    }

    private ETFInvestListResponseDTO mapToResponseDTO(ETF etf) {
        return ETFInvestListResponseDTO.builder()
                .id(etf.getId())
                .name(etf.getName())
                .company(etf.getCompany())
                .sector(etf.getSector())
                .build();
    }
}