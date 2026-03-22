package com.tinyroute.repository;

import com.tinyroute.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Modifying
    @Query("UPDATE User u SET u.bioPageViews = u.bioPageViews + 1 WHERE u.username = :username")
    void incrementBioPageViews(@Param("username") String username);
}