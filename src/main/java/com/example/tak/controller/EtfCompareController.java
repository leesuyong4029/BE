package com.example.tak.controller;

import com.example.tak.common.Category;
import com.example.tak.config.response.ApiResponse;
import com.example.tak.dto.request.EtfInfoRequestDTO;
import com.example.tak.dto.response.EtfDetailResultDTO;
import com.example.tak.dto.response.EtfResponseDTO;
import com.example.tak.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/compare")
public class EtfCompareController {

    private final EtfInfoService etfInfoService;
    private final EtfDetailService etfDetailService;
    private final EtfCompareListService etfCompareListService;
    private final EtfGetListService etfGetListService;
    private final ETFGetService etfGetService;

    // 비교 페이지
    @PostMapping
    public Map<String, Object> getEtfComparison(@RequestBody EtfInfoRequestDTO etfInfoRequest) {
        return etfInfoService.getEtfComparisonAsMap(etfInfoRequest.getEtfList());
    }

    // 상세 페이지
    @GetMapping("/detail/{identifier}")
    public Map<String, Object> getEtfDetail(@PathVariable("identifier") String identifier) {
        EtfDetailResultDTO resultData = etfDetailService.getEtfDetailByIdentifier(identifier);

        Map<String, Object> response = new HashMap<>();
        response.put("result", Collections.singletonList(resultData));

        return response;
    }

    // ETF 검색 (비교 화면)
    @GetMapping("/search")
    public ApiResponse<List<EtfResponseDTO.CompareEtfDto>> searchEtf(@RequestParam("keyword") String keyword, @RequestParam("category")Category category)
    {
        List<EtfResponseDTO.CompareEtfDto> response = etfCompareListService.searchByCategory(keyword, category);
        return ApiResponse.onSuccess(response);
    }

    // 비교하기 목록 조회
//    @GetMapping("/result")
//    public ApiResponse<List<EtfResponseDTO.CompareEtfDto>> getEtfInfo(@RequestParam("filter") String filter, @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)Pageable pageable)
//    {
//        Page<EtfResponseDTO.CompareEtfDto> page = etfGetListService.getEtfsByFilter(filter, pageable);
//        return ApiResponse.onSuccess(page.getContent());
//
//    }

    // 비교하기 목록 조회 (비동기)
    @GetMapping
    public Mono<ApiResponse<List<EtfResponseDTO.CompareEtfDto>>> getEtfInfo(
            @RequestParam("filter") String filter,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return etfGetService.getEtfsByFilter(filter, pageable)
                .map(page -> ApiResponse.onSuccess(page.getContent()));
    }
}