package com.tinyroute.repository;

import com.tinyroute.models.UrlEditHistory;
import com.tinyroute.models.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UrlEditHistoryRepository extends JpaRepository<UrlEditHistory, Long> {
    List<UrlEditHistory> findByUrlMappingOrderByChangedAtDesc(UrlMapping urlMapping);
}