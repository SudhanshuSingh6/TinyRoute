package com.tinyroute.repository.analytics;

import com.tinyroute.entity.ClickEvent;
import com.tinyroute.entity.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {
    List<ClickEvent> findByUrlMappingAndClickDateBetween(UrlMapping mapping, LocalDateTime startDate, LocalDateTime endDate);
    List<ClickEvent> findByUrlMappingInAndClickDateBetween(List<UrlMapping> urlMappings, LocalDateTime startDate, LocalDateTime endDate);

    // used to check if this IP has clicked this URL before — for unique click detection
    boolean existsByUrlMappingAndIpHash(UrlMapping urlMapping, String ipHash);
}