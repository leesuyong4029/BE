package com.example.tak.repository;

import com.example.tak.common.Category;
import com.example.tak.domain.ETF;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EtfDataRepository extends JpaRepository<ETF, Long> {

    Page<ETF> findByCategory(Category category, Pageable pageable);
    Page<ETF> findByTickerIsNull(Pageable pageable);
    Page<ETF> findByTickerIsNotNull(Pageable pageable);
    List<ETF> findByName(String name);
}