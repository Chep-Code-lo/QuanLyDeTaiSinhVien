package com.example.qldetai.feature.communication.email;

import com.example.qldetai.feature.groupregistration.repository.GroupMemberRepository;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.repository.StudentRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.topic.repository.TopicSupervisorRepository;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Resolves predefined recipient groups from real application relations only. */
@Component
public class EmailRecipientResolver {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final TopicSupervisorRepository topicSupervisorRepository;

    public EmailRecipientResolver(
            UserRepository userRepository,
            StudentRepository studentRepository,
            LecturerRepository lecturerRepository,
            GroupMemberRepository groupMemberRepository,
            TopicSupervisorRepository topicSupervisorRepository) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.topicSupervisorRepository = topicSupervisorRepository;
    }

    public List<ResolvedRecipient> resolve(
            Set<EmailRecipientGroup> groups, EmailEventContext context) {
        // MySQL's email unique key is case-sensitive in this schema. SMTP addresses
        // are not, so deduplicate on a canonical email address rather than user id.
        Map<String, ResolvedRecipient> recipients = new LinkedHashMap<>();
        for (EmailRecipientGroup group : groups) {
            switch (group) {
                case DIRECT_PARTICIPANTS -> {
                    List<Long> directIds = nonNullIds(context.directRecipientUserIds());
                    if (!directIds.isEmpty()) {
                        addAll(
                                recipients,
                                userRepository.findActiveByIdIn(directIds),
                                group.name());
                    }
                }
                case FACULTY_STUDENTS -> {
                    if (context.facultyId() != null) {
                        addAll(
                                recipients,
                                studentRepository
                                        .findActiveByFacultyId(context.facultyId())
                                        .stream()
                                        .map(student -> student.getUser())
                                        .toList(),
                                group.name());
                    }
                }
                case FACULTY_LECTURERS -> {
                    if (context.facultyId() != null) {
                        addAll(
                                recipients,
                                lecturerRepository
                                        .findActiveByFacultyId(context.facultyId())
                                        .stream()
                                        .map(lecturer -> lecturer.getUser())
                                        .toList(),
                                group.name());
                    }
                }
                case GROUP_STUDENTS, TOPIC_STUDENTS -> {
                    if (context.groupId() != null) {
                        addAll(
                                recipients,
                                groupMemberRepository
                                        .findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(
                                                context.groupId())
                                        .stream()
                                        .map(member -> member.getStudent().getUser())
                                        .toList(),
                                group.name());
                    }
                }
                case SUPERVISORS -> {
                    if (context.topicId() != null) {
                        addAll(
                                recipients,
                                topicSupervisorRepository
                                        .findByTopicIdOrderBySlotNoAsc(context.topicId())
                                        .stream()
                                        .map(supervisor -> supervisor.getLecturer().getUser())
                                        .toList(),
                                group.name());
                    }
                }
                case ADMINS ->
                        addAll(recipients, userRepository.findActiveAdministrators(), group.name());
            }
        }
        return new ArrayList<>(recipients.values());
    }

    private List<Long> nonNullIds(List<Long> values) {
        return values == null
                ? List.of()
                : values.stream().filter(id -> id != null).distinct().toList();
    }

    private void addAll(
            Map<String, ResolvedRecipient> target, Collection<User> users, String category) {
        for (User user : users) {
            if (user.getId() == null
                    || user.getStatus() != UserStatus.ACTIVE
                    || user.getEmail() == null
                    || user.getEmail().isBlank()) {
                continue;
            }
            target.putIfAbsent(
                    user.getEmail().trim().toLowerCase(Locale.ROOT),
                    new ResolvedRecipient(user, category));
        }
    }

    public record ResolvedRecipient(User user, String category) {}
}
