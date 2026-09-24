package com.example.qldetai.feature.topic.service;

import com.example.qldetai.feature.academic.service.AcademicAuthorization;
import com.example.qldetai.feature.topic.repository.TopicRepository;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("topicAuthorization")
@Transactional(readOnly = true)
public class TopicAuthorization {

    private final TopicRepository topicRepository;
    private final AcademicAuthorization academicAuthorization;

    public TopicAuthorization(
            TopicRepository topicRepository, AcademicAuthorization academicAuthorization) {
        this.topicRepository = topicRepository;
        this.academicAuthorization = academicAuthorization;
    }

    public boolean canManageTopic(Long topicId, Authentication authentication) {
        return topicRepository
                .findById(topicId)
                .map(
                        topic ->
                                academicAuthorization.canManageDepartment(
                                        topic.getDepartment().getId(), authentication))
                .orElse(false);
    }
}
