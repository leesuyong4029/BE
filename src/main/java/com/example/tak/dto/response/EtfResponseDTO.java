package com.example.tak.dto.response;

import com.example.tak.common.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class EtfResponseDTO {

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class     CompareEtfDto {
        private Long etfId;
        private Category category;
        private String name;
        private String sector;
        private Float fee;
        private Long price;
        private String ticker;
        private String etfNum;
        private String profitRate;
        private boolean isPositive;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompareEtfListPageDto {
        private List<CompareEtfDto> compareEtfDtoList;
    }
}
