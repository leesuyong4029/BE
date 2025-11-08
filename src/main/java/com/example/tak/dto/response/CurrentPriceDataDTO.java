package com.example.tak.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentPriceDataDTO {
    private Double currentPrice;
    private String prdyVrssSign;
    private Double prdyVrss;
    private Double prdyCtrt;
    private Double nav;
    private String navPrdyVrssSign;
    private Double navPrdyVrss;
    private Double navPrdyCtrt;
}
