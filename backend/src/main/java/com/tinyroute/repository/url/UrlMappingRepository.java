package com.tinyroute.repository.url;

import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {
    UrlMapping findByShortUrl(String shortUrl);
    UrlMapping findByShortUrlAndUserUsername(String shortUrl, String username);
    List<UrlMapping> findByUser(User user);
    List<UrlMapping> findByUserAndIsDeletedFalse(User user);
    UrlMapping findByShortUrlAndUser(String shortUrl, User user);
    UrlMapping findByOriginalUrlAndUser(String originalUrl, User user); // duplicate check
}
