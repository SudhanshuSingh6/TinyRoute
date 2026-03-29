package com.tinyroute.repository.analytics;

import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.UrlUniqueVisitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface UrlUniqueVisitorRepository extends JpaRepository<UrlUniqueVisitor, Long> {

    void deleteByUrlMapping(UrlMapping urlMapping);

    long countByUrlMappingId(Long urlMappingId);

    long countByUrlMappingIdAndFirstSeenAtBetween(Long urlMappingId,
                                                  LocalDateTime start,
                                                  LocalDateTime end);

    @Transactional
    @Modifying
    @Query(value = """
        INSERT IGNORE INTO url_unique_visitor (url_mapping_id, ip_hash, first_seen_at)
        VALUES (:urlMappingId, :ipHash, :firstSeenAt)
        """, nativeQuery = true)
    int insertUniqueVisitor(@Param("urlMappingId") Long urlMappingId,
                            @Param("ipHash") String ipHash,
                            @Param("firstSeenAt") LocalDateTime firstSeenAt);
}