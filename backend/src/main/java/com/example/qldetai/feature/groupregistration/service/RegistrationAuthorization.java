package com.example.qldetai.feature.groupregistration.service;

import com.example.qldetai.feature.groupregistration.repository.TopicRegistrationRepository;
import com.example.qldetai.feature.topic.service.TopicAuthorization;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("registrationAuthorization")
@Transactional(readOnly = true)
public class RegistrationAuthorization {

    private final TopicRegistrationRepository topicRegistrationRepository;
    private final TopicAuthorization topicAuthorization;

    public RegistrationAuthorization(
            TopicRegistrationRepository topicRegistrationRepository,
            TopicAuthorization topicAuthorization) {
        this.topicRegistrationRepository = topicRegistrationRepository;
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
}
