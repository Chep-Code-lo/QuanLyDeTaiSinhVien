package com.example.qldetai.feature.groupregistration.service;

import com.example.qldetai.feature.groupregistration.repository.GroupMemberRepository;
import com.example.qldetai.feature.groupregistration.repository.TopicRegistrationRepository;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.topic.service.TopicAuthorization;
import com.example.qldetai.security.UserPrincipal;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("registrationAuthorization")
@Transactional(readOnly = true)
public class RegistrationAuthorization {

    private final TopicRegistrationRepository topicRegistrationRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final TopicAuthorization topicAuthorization;

    public RegistrationAuthorization(
            TopicRegistrationRepository topicRegistrationRepository,
            GroupMemberRepository groupMemberRepository,
            TopicAuthorization topicAuthorization) {
        this.topicRegistrationRepository = topicRegistrationRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.topicAuthorization = topicAuthorization;
    }

    public boolean canManageRegistration(Long registrationId, Authentication authentication) {
        return topicRegistrationRepository
                .findById(registrationId)
                .map(
                        registration ->
                                topicAuthorization.canManageTopic(
                                        registration.getTopic().getId(), authentication))
                .orElse(false);
    }

    public boolean canViewRegistration(Long registrationId, Authentication authentication) {
        UserPrincipal principal = principal(authentication);
        if (principal == null) {
            return false;
        }
        if (principal.roles().contains(RoleCode.ADMIN)
                || principal.roles().contains(RoleCode.LECTURER)) {
            return true;
        }
        return topicRegistrationRepository
                .findById(registrationId)
                .map(
                        registration ->
                                groupMemberRepository
                                        .existsByGroupIdAndStudentUserIdAndLeftAtIsNull(
                                                registration.getGroup().getId(), principal.id()))
                .orElse(false);
    }

    private UserPrincipal principal(Authentication authentication) {
        return authentication != null
                        && authentication.getPrincipal() instanceof UserPrincipal userPrincipal
                ? userPrincipal
                : null;
    }
}
