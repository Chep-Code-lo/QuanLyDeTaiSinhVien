package com.example.qldetai.feature.identity.service;

import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.common.audit.BusinessAudit;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ConflictException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.academic.domain.Department;
import com.example.qldetai.feature.academic.repository.DepartmentRepository;
import com.example.qldetai.feature.identity.domain.Lecturer;
import com.example.qldetai.feature.identity.domain.Role;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.Student;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.dto.IdentityDtos.CreateUserRequest;
import com.example.qldetai.feature.identity.dto.IdentityDtos.LecturerProfileInput;
import com.example.qldetai.feature.identity.dto.IdentityDtos.StudentProfileInput;
import com.example.qldetai.feature.identity.dto.IdentityDtos.UpdateUserRequest;
import com.example.qldetai.feature.identity.dto.IdentityDtos.UpdateUserAccessRequest;
import com.example.qldetai.feature.identity.dto.IdentityDtos.UpdateUserRolesRequest;
import com.example.qldetai.feature.identity.dto.IdentityDtos.UpdateUserStatusRequest;
import com.example.qldetai.feature.identity.dto.IdentityDtos.UserResponse;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.repository.RoleRepository;
import com.example.qldetai.feature.identity.repository.StudentRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserProfileService userProfileService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String search, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        String query = search == null ? "" : search.trim();
        Page<User> users = userRepository.searchVisibleForAdmin(query, pageable);
        return PageResponse.from(userProfileService.toUsers(users));
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return userProfileService.toUser(requireUser(id));
    }

    @Transactional(readOnly = true)
    public UserResponse lookup(String identifier) {
        String query = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            throw new BusinessRuleException("Vui lòng nhập email, mã sinh viên hoặc mã giảng viên.");
        }
        var matches = userRepository.findForAdminLookup(query);
        if (matches.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy tài khoản khớp với thông tin đã nhập.");
        }
        if (matches.size() > 1) {
            throw new ConflictException("Mã này trùng giữa nhiều tài khoản. Vui lòng tra cứu bằng email.");
        }
        return userProfileService.toUser(matches.getFirst());
    }

    @Transactional
    @BusinessAudit(action = "USER_CREATE", entityType = "USER")
    public UserResponse create(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already in use.");
        }
        validateProfiles(request.roles(), request.student(), request.lecturer());

        LocalDateTime now = LocalDateTime.now(clock);
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setPhone(normalizeNullable(request.phone()));
        user.setStatus(UserStatus.ACTIVE);
        user.replaceRoles(requireRoles(request.roles()), now);
        user = userRepository.save(user);

        if (request.student() != null) {
            studentRepository.save(createStudent(user, request.student()));
        }
        if (request.lecturer() != null) {
            lecturerRepository.save(createLecturer(user, request.lecturer()));
        }
        return userProfileService.toUser(user);
    }

    @Transactional
    @BusinessAudit(action = "USER_UPDATE", entityType = "USER", entityIdArgument = 0)
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = requireUser(id);
        if (user.getVersion() != request.version()) {
            throw new ConflictException(
                    "The account information was updated by another user. Refresh and try again.");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ConflictException("Email is already in use.");
        }

        Student student = studentRepository.findByUserId(id).orElse(null);
        Lecturer lecturer = lecturerRepository.findByUserId(id).orElse(null);
        validateProfileUpdate(student, lecturer, request);

        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPhone(normalizeNullable(request.phone()));
        if (student != null) {
            updateStudent(student, request.student());
        }
        if (lecturer != null) {
            updateLecturer(lecturer, request.lecturer());
        }
        user.setUpdatedAt(LocalDateTime.now(clock));
        return userProfileService.toUser(userRepository.saveAndFlush(user));
    }

    @Transactional
    @BusinessAudit(action = "USER_STATUS_UPDATE", entityType = "USER", entityIdArgument = 0)
    public UserResponse updateStatus(Long id, UpdateUserStatusRequest request) {
        lockAdministratorChanges();
        User user = requireUser(id);
        if (request.status() != UserStatus.ACTIVE) requireAdministratorRemains(user);
        user.setStatus(request.status());
        return userProfileService.toUser(user);
    }

    @Transactional
    @BusinessAudit(action = "USER_DELETE", entityType = "USER", entityIdArgument = 0)
    public void delete(Long id, Long actorId) {
        if (id.equals(actorId)) {
            throw new BusinessRuleException("You cannot delete the account currently in use.");
        }

        lockAdministratorChanges();
        User user = requireUser(id);
        if (user.getStatus() == UserStatus.DISABLED) {
            return;
        }
        requireAdministratorRemains(user);

        user.setStatus(UserStatus.DISABLED);
    }

    @Transactional
    @BusinessAudit(action = "USER_ROLES_UPDATE", entityType = "USER", entityIdArgument = 0)
    public UserResponse updateRoles(Long id, UpdateUserRolesRequest request) {
        lockAdministratorChanges();
        User user = requireUser(id);
        if (!request.roles().contains(RoleCode.ADMIN)) requireAdministratorRemains(user);
        validateRoleChange(id, request.roles());
        LocalDateTime now = LocalDateTime.now(clock);
        user.replaceRoles(requireRoles(request.roles()), now);
        return userProfileService.toUser(user);
    }

    @Transactional
    @BusinessAudit(action = "USER_ACCESS_UPDATE", entityType = "USER", entityIdArgument = 0)
    public UserResponse updateAccess(Long id, UpdateUserAccessRequest request) {
        lockAdministratorChanges();
        User user = requireUser(id);
        if (request.status() != UserStatus.ACTIVE || !request.roles().contains(RoleCode.ADMIN)) {
            requireAdministratorRemains(user);
        }
        validateRoleChange(id, request.roles());
        Set<Role> roles = requireRoles(request.roles());
        LocalDateTime now = LocalDateTime.now(clock);
        user.replaceRoles(roles, now);
        user.setStatus(request.status());
        user.setUpdatedAt(now);
        return userProfileService.toUser(userRepository.saveAndFlush(user));
    }

    private void validateRoleChange(Long id, Set<RoleCode> roles) {
        if (studentRepository.findByUserId(id).isPresent()
                && !roles.contains(RoleCode.STUDENT)) {
            throw new BusinessRuleException(
                    "Remove the student profile before removing the STUDENT role.");
        }
        if (lecturerRepository.findByUserId(id).isPresent()
                && !roles.contains(RoleCode.LECTURER)) {
            throw new BusinessRuleException(
                    "Remove the lecturer profile before removing the LECTURER role.");
        }
        if (roles.contains(RoleCode.STUDENT)
                && studentRepository.findByUserId(id).isEmpty()) {
            throw new BusinessRuleException("A student profile is required for the STUDENT role.");
        }
        if (roles.contains(RoleCode.LECTURER)
                && lecturerRepository.findByUserId(id).isEmpty()) {
            throw new BusinessRuleException(
                    "A lecturer profile is required for the LECTURER role.");
        }
    }

    private void lockAdministratorChanges() {
        roleRepository
                .findByCodeForUpdate(RoleCode.ADMIN)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Administrator role was not found."));
    }

    private void requireAdministratorRemains(User user) {
        if (user.getStatus() == UserStatus.ACTIVE
                && hasRole(user, RoleCode.ADMIN)
                && userRepository.findActiveAdministratorsForUpdate(PageRequest.of(0, 2)).size()
                        <= 1) {
            throw new BusinessRuleException(
                    "At least one active administrator account is required.");
        }
    }

    private Student createStudent(User user, StudentProfileInput input) {
        if (studentRepository.existsByStudentCodeIgnoreCase(input.studentCode().trim())) {
            throw new ConflictException("Student code is already in use.");
        }
        Student student = new Student();
        student.setUser(user);
        student.setDepartment(requireDepartment(input.departmentId()));
        student.setStudentCode(input.studentCode().trim().toUpperCase(Locale.ROOT));
        student.setClassName(input.className().trim());
        student.setCohort(input.cohort().trim());
        return student;
    }

    private Lecturer createLecturer(User user, LecturerProfileInput input) {
        if (lecturerRepository.existsByLecturerCodeIgnoreCase(input.lecturerCode().trim())) {
            throw new ConflictException("Lecturer code is already in use.");
        }
        Lecturer lecturer = new Lecturer();
        lecturer.setUser(user);
        lecturer.setDepartment(requireDepartment(input.departmentId()));
        lecturer.setLecturerCode(input.lecturerCode().trim().toUpperCase(Locale.ROOT));
        lecturer.setAcademicDegree(normalizeNullable(input.academicDegree()));
        lecturer.setSpecialization(normalizeNullable(input.specialization()));
        return lecturer;
    }

    private void updateStudent(Student student, StudentProfileInput input) {
        String studentCode = input.studentCode().trim().toUpperCase(Locale.ROOT);
        if (studentRepository.existsByStudentCodeIgnoreCaseAndIdNot(studentCode, student.getId())) {
            throw new ConflictException("Student code is already in use.");
        }
        student.setDepartment(requireDepartment(input.departmentId()));
        student.setStudentCode(studentCode);
        student.setClassName(input.className().trim());
        student.setCohort(input.cohort().trim());
    }

    private void updateLecturer(Lecturer lecturer, LecturerProfileInput input) {
        String lecturerCode = input.lecturerCode().trim().toUpperCase(Locale.ROOT);
        if (lecturerRepository.existsByLecturerCodeIgnoreCaseAndIdNot(
                lecturerCode, lecturer.getId())) {
            throw new ConflictException("Lecturer code is already in use.");
        }
        lecturer.setDepartment(requireDepartment(input.departmentId()));
        lecturer.setLecturerCode(lecturerCode);
        lecturer.setAcademicDegree(normalizeNullable(input.academicDegree()));
        lecturer.setSpecialization(normalizeNullable(input.specialization()));
    }

    private void validateProfiles(
            Set<RoleCode> roles, StudentProfileInput student, LecturerProfileInput lecturer) {
        if (roles.contains(RoleCode.STUDENT) != (student != null)) {
            throw new BusinessRuleException(
                    "The STUDENT role and student profile must be supplied together.");
        }
        if (roles.contains(RoleCode.LECTURER) != (lecturer != null)) {
            throw new BusinessRuleException(
                    "The LECTURER role and lecturer profile must be supplied together.");
        }
    }

    private void validateProfileUpdate(
            Student student, Lecturer lecturer, UpdateUserRequest request) {
        if ((student != null) != (request.student() != null)) {
            throw new BusinessRuleException(
                    "The student profile cannot be added or removed while editing the account.");
        }
        if ((lecturer != null) != (request.lecturer() != null)) {
            throw new BusinessRuleException(
                    "The lecturer profile cannot be added or removed while editing the account.");
        }
    }

    private Set<Role> requireRoles(Set<RoleCode> codes) {
        Set<Role> roles = new HashSet<>(roleRepository.findAllByCodeIn(codes));
        if (roles.size() != codes.size()) {
            throw new BusinessRuleException("One or more roles are not configured.");
        }
        return roles;
    }

    private User requireUser(Long id) {
        return userRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User account was not found."));
    }

    private Department requireDepartment(Long id) {
        return departmentRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department was not found."));
    }

    private boolean hasRole(User user, RoleCode roleCode) {
        return user.getUserRoles().stream()
                .anyMatch(userRole -> userRole.getRole().getCode() == roleCode);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
