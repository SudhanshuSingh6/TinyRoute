package com.tinyroute.repository.url;

import com.tinyroute.entity.UrlEditHistory;
import com.tinyroute.entity.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UrlEditHistoryRepository extends JpaRepository<UrlEditHistory, Long> {
    List<UrlEditHistory> findByUrlMappingOrderByChangedAtDesc(UrlMapping urlMapping);
}