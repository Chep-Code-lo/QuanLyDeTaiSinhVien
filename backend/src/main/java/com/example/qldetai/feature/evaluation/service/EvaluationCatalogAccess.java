package com.example.qldetai.feature.evaluation.service;

import com.example.qldetai.feature.evaluation.domain.AssessmentComponent;
import com.example.qldetai.feature.evaluation.domain.Rubric;
import com.example.qldetai.feature.identity.domain.AcademicAppointment;
import com.example.qldetai.feature.identity.domain.PositionCode;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.UserRole;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EvaluationCatalogAccess {
    private final Clock clock;

    public Specification<AssessmentComponent> components(Long periodId, Long actorId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return (root, query, cb) -> cb.and(cb.equal(root.get("period").get("id"), periodId),
                cb.or(cb.equal(root.get("status"), "LOCKED"),
                        draftManager(root.get("period").get("faculty"), actorId, now, query, cb)));
    }

    public Specification<Rubric> rubrics(Long componentId, Long actorId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return (root, query, cb) -> cb.and(cb.equal(root.get("component").get("id"), componentId),
                cb.or(cb.and(cb.equal(root.get("status"), "LOCKED"),
                                cb.equal(root.get("component").get("status"), "LOCKED")),
                        draftManager(root.get("component").get("period").get("faculty"), actorId, now, query, cb)));
    }

    private Predicate draftManager(Path<?> faculty, Long actorId, LocalDateTime now,
            CriteriaQuery<?> query, CriteriaBuilder cb) {
        var roles = query.subquery(Long.class);
        var role = roles.from(UserRole.class);
        roles.select(role.get("id")).where(cb.equal(role.get("user").get("id"), actorId),
                cb.equal(role.get("role").get("code"), RoleCode.ADMIN));
        var appointments = query.subquery(Long.class);
        var appointment = appointments.from(AcademicAppointment.class);
        appointments.select(appointment.get("id")).where(
                cb.equal(appointment.get("lecturer").get("user").get("id"), actorId),
                cb.equal(appointment.get("positionCode"), PositionCode.DEAN),
                cb.equal(appointment.get("faculty"), faculty),
                cb.lessThanOrEqualTo(appointment.get("startsAt"), now),
                cb.or(cb.isNull(appointment.get("endsAt")), cb.greaterThan(appointment.get("endsAt"), now)));
        // Locked catalogs are shared reference data. Drafts stay within management scope.
        return cb.or(cb.exists(roles), cb.exists(appointments));
    }
}
