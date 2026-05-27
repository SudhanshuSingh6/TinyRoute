package com.tinyroute.url.repository;

import com.tinyroute.url.entity.UrlEditHistory;
import com.tinyroute.url.entity.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UrlEditHistoryRepository extends JpaRepository<UrlEditHistory, Long> {
    List<UrlEditHistory> findByUrlMappingOrderByChangedAtDesc(UrlMapping urlMapping);

    void deleteByUrlMapping(UrlMapping urlMapping);
}