package com.example.qldetai.feature.groupregistration.repository;

import com.example.qldetai.feature.groupregistration.domain.RegistrationStatus;
import com.example.qldetai.feature.groupregistration.domain.TopicRegistration;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRegistrationRepository extends JpaRepository<TopicRegistration, Long> {

    Page<TopicRegistration> findByStatus(RegistrationStatus status, Pageable pageable);

    Page<TopicRegistration> findByPeriodId(Long periodId, Pageable pageable);

    Page<TopicRegistration> findByPeriodIdAndStatus(Long periodId, RegistrationStatus status, Pageable pageable);

    boolean existsByGroupIdAndStatusIn(Long groupId, Collection<RegistrationStatus> statuses);

    List<TopicRegistration> findByTopicIdAndStatus(Long topicId, RegistrationStatus status);
}
