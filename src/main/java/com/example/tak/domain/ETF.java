package com.example.tak.domain;

import com.example.tak.common.Category;
import com.example.tak.common.Nation;
import com.example.tak.converter.CategoryConverter;
import com.example.tak.converter.NationConverter;
import com.example.tak.config.BaseEntity;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Table(
        name = "etf",
        indexes = {
                @Index(name ="idx_etf_sector", columnList = "sector"),
                @Index(name = "idx_etf_category", columnList = "category"),
                @Index(name = "idx_etf_nation", columnList = "nation"),
                @Index(name = "idx_etf_name", columnList = "name")
        }
)
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ETF extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "company")
    private String company;

    @Column(name = "listing_date")
    private LocalDateTime listingDate;

    @Column(name = "net_worth")
    private Long netWorth;

    @Column(name = "dividend_rate")
    private Float dividendRate;

    @Column(name = "sector")
    private String sector;

    @Column(name = "category")
    @Convert(converter = CategoryConverter.class)
    private Category category;

    @Column(name = "nation")
    @Convert(converter = NationConverter.class)
    private Nation nation;

    @Column(name = "fee")
    private Float fee;

    @Column(name = "price")
    private Long price;

    @Column(name = "ticker")
    private String ticker;

    @Column(name = "etf_num")
    private String etfNum;

    @JsonSetter("iNav")
    @Column(name = "i_nav")
    private Double iNav;

    @Column(name = "invest_point", length = 1000)
    private String investPoint;

    @Builder.Default
    @OneToMany(mappedBy = "etf", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Stock> stocks = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "etf", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Distribution> distributions = new ArrayList<>();

    public static ETF of(String name, String company, LocalDateTime listingDate,
                         Long netWorth, Float dividendRate, String sector, Category category, Float fee,
                         Long price, String ticker, String etfNum, Double iNav, String investPoint) {
        return ETF.builder()
                .name(name)
                .company(company)
                .listingDate(listingDate)
                .netWorth(netWorth)
                .dividendRate(dividendRate)
                .sector(sector)
                .category(category)
                .fee(fee)
                .price(price)
                .ticker(ticker)
                .etfNum(etfNum)
                .iNav(iNav)
                .investPoint(investPoint)
                .build();
    }
}
