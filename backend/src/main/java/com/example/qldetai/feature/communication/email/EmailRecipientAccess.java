package com.example.qldetai.feature.communication.email;

import com.example.qldetai.feature.groupregistration.domain.GroupMember;
import com.example.qldetai.feature.groupregistration.domain.RegistrationStatus;
import com.example.qldetai.feature.groupregistration.domain.TopicRegistration;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserRole;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.topic.domain.TopicSupervisor;
import java.util.ArrayList;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/** Applies the same recipient scope to search, group dispatch and explicit user IDs. */
@Component
public class EmailRecipientAccess {
    public Specification<User> accessible(Long lecturerUserId, RoleCode role, String search) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("status"), UserStatus.ACTIVE));
            predicates.add(cb.isNotNull(root.get("email")));
            predicates.add(cb.notEqual(cb.trim(root.get("email")), ""));
            RoleCode recipientRole = lecturerUserId == null ? role : RoleCode.STUDENT;
            if (recipientRole != null) {
                var matchingRole = query.subquery(Long.class);
                var userRole = matchingRole.from(UserRole.class);
                matchingRole.select(userRole.get("id")).where(cb.equal(userRole.get("user"), root),
                        cb.equal(userRole.get("role").get("code"), recipientRole));
                predicates.add(cb.exists(matchingRole));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("fullName")), pattern), cb.like(cb.lower(root.get("email")), pattern)));
            }
            if (lecturerUserId != null) {
                var supervised = query.subquery(Long.class);
                var registration = supervised.from(TopicRegistration.class);
                var member = supervised.from(GroupMember.class);
                var supervisor = supervised.from(TopicSupervisor.class);
                supervised.select(member.get("id")).where(
                        cb.equal(member.get("student").get("user"), root), cb.isNull(member.get("leftAt")),
                        cb.equal(member.get("group"), registration.get("group")),
                        cb.equal(registration.get("status"), RegistrationStatus.APPROVED),
                        cb.equal(registration.get("topic"), supervisor.get("topic")),
                        cb.equal(supervisor.get("lecturer").get("user").get("id"), lecturerUserId));
                predicates.add(cb.exists(supervised));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
