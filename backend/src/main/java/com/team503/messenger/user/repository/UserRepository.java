package com.team503.messenger.user.repository;

import com.team503.messenger.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    /**
     * Search active users by username or display name (case-insensitive).
     * Returns at most 10 results to avoid performance issues.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.status = 'ACTIVE'
            AND (LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
                 OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY u.username
            LIMIT 10
            """)
    java.util.List<User> searchActiveUsers(@Param("query") String query);
}
