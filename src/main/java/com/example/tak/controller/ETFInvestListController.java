package com.example.tak.controller;

import com.example.tak.config.response.ApiResponse;
import com.example.tak.dto.response.ETFInvestListResponseDTO;
import com.example.tak.service.ETFInvestListService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@ResponseBody
@RequiredArgsConstructor
public class ETFInvestListController {

    private final ETFInvestListService etfInvestListService;


    @GetMapping("/api/invest")
    public ApiResponse<List<ETFInvestListResponseDTO>> getInvestList(@RequestParam(name = "filter") String filter, @PageableDefault(size = 6, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<ETFInvestListResponseDTO> page = etfInvestListService.getETFInvestList(filter, pageable);
        return ApiResponse.onSuccess(page.getContent());
    }

}
