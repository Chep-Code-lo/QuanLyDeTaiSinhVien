package com.example.qldetai.feature.evaluation.service;

import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.UserRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class EvaluationActorAccess {

    private final UserRepository users;

    public EvaluationActorAccess(UserRepository users) {
        this.users = users;
    }

    public boolean isManager(Long actorId) {
        return users.findWithRolesById(actorId)
                .map(
                        user ->
                                user.getUserRoles().stream()
                                        .anyMatch(
                                                role ->
                                                        role.getRole().getCode() == RoleCode.ADMIN
                                                                || role.getRole().getCode()
                                                                        == RoleCode.LECTURER))
                .orElse(false);
    }

    public void manager(Long actorId) {
        if (!isManager(actorId)) {
            throw new AccessDeniedException(
                    "Only lecturers or administrators can manage evaluation.");
        }
    }

    public User user(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User was not found."));
    }
}
