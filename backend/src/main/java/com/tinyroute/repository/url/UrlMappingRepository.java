package com.tinyroute.repository.url;

import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.UrlStatus;
import com.tinyroute.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    // ---------- Redirect / lookup ----------
    UrlMapping findByShortUrl(String shortUrl);

    Optional<UrlMapping> findByShortUrlAndUser(String shortUrl, User user);

    Optional<UrlMapping> findByShortUrlAndUserUsername(String shortUrl, String username);

    boolean existsByShortUrl(String shortUrl);

    @Transactional
    @Modifying
    @Query("""
        update UrlMapping u
        set u.status = :status
        where u.id = :id
    """)
    int updateStatus(@Param("id") Long id, @Param("status") UrlStatus status);

    @Transactional
    @Modifying
    @Query("""
        update UrlMapping u
        set u.lastClickedAt = :now
        where u.id = :id
    """)
    int updateLastClickedAt(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying
    @Query("""
        update UrlMapping u
        set u.clickCount = u.clickCount + 1
        where u.id = :id
    """)
    int incrementClickCount(@Param("id") Long id);

    // ---------- User-owned URLs ----------
    List<UrlMapping> findByUser(User user);

    // ---------- URL creation / duplicate detection ----------
    boolean existsByOriginalUrlAndUser(String originalUrl, User user);

    Optional<UrlMapping> findByShortUrlAndUserId(String shortUrl, Long userId);

    boolean existsByOriginalUrlAndUserAndIdNot(String originalUrl, User user, Long id);
}