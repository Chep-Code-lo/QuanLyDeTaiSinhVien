package com.example.qldetai.feature.identity.repository;

import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import java.util.List;
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

    List<User> findByStatus(UserStatus status);

    Page<User> findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
            String email, String fullName, Pageable pageable);
}
