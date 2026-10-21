package com.example.qldetai.feature.identity.repository;

import com.example.qldetai.feature.identity.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
    Optional<User> findWithRolesById(Long id);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByIdAndStatus(Long id, com.example.qldetai.feature.identity.domain.UserStatus status);

    @EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
    @Query("""
            select u from User u
            where u.status <> com.example.qldetai.feature.identity.domain.UserStatus.DISABLED
              and (
                :search = ''
                or lower(u.email) like lower(concat('%', :search, '%'))
                or lower(u.fullName) like lower(concat('%', :search, '%'))
              )
            """)
    Page<User> searchVisibleForAdmin(@Param("search") String search, Pageable pageable);

    @Query("""
            select distinct u from User u
            join u.userRoles ur
            where ur.role.code = com.example.qldetai.feature.identity.domain.RoleCode.ADMIN
              and u.status = com.example.qldetai.feature.identity.domain.UserStatus.ACTIVE
            order by u.fullName asc
            """)
    List<User> findActiveAdministrators();

    @Query("""
            select u from User u
            where u.id in :ids
              and u.status = com.example.qldetai.feature.identity.domain.UserStatus.ACTIVE
            """)
    List<User> findActiveByIdIn(@Param("ids") List<Long> ids);

    Page<User> findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
            String email, String fullName, Pageable pageable);
}
