package com.example.qldetai.feature.identity;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
    Optional<User> findWithRolesById(Long id);

    boolean existsByEmailIgnoreCase(String email);

    Page<User> findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
            String email, String fullName, Pageable pageable);
}
