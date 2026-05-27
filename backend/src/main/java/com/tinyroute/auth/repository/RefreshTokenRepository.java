package com.tinyroute.auth.repository;

import com.tinyroute.auth.entity.RefreshToken;
import com.tinyroute.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Transactional
    @Modifying
    @Query("""
                UPDATE RefreshToken r
                SET r.revoked = true
                WHERE r.user = :user
                  AND r.revoked = false
            """)
    void revokeAllActiveForUser(@Param("user") User user);

}