package com.example.qldetai.feature.identity;

import com.example.qldetai.aspect.BusinessAudit;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ConflictException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.academic.Department;
import com.example.qldetai.feature.academic.DepartmentRepository;
import com.example.qldetai.feature.identity.IdentityDtos.CreateUserRequest;
import com.example.qldetai.feature.identity.IdentityDtos.LecturerProfileInput;
import com.example.qldetai.feature.identity.IdentityDtos.StudentProfileInput;
import com.example.qldetai.feature.identity.IdentityDtos.UpdateUserRolesRequest;
import com.example.qldetai.feature.identity.IdentityDtos.UpdateUserStatusRequest;
import com.example.qldetai.feature.identity.IdentityDtos.UserResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityMapper identityMapper;
    private final Clock clock;

    public UserAdminService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            StudentRepository studentRepository,
            LecturerRepository lecturerRepository,
            DepartmentRepository departmentRepository,
            PasswordEncoder passwordEncoder,
            IdentityMapper identityMapper,
            Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.identityMapper = identityMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String search, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        String query = search == null ? "" : search.trim();
        Page<UserResponse> users = query.isBlank()
                ? userRepository.findAll(pageable).map(identityMapper::toUser)
                : userRepository.findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(query, query, pageable)
                        .map(identityMapper::toUser);
        return PageResponse.from(users);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return identityMapper.toUser(requireUser(id));
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
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.replaceRoles(requireRoles(request.roles()), now);
        user = userRepository.save(user);

        if (request.student() != null) {
            studentRepository.save(createStudent(user, request.student()));
        }
        if (request.lecturer() != null) {
            lecturerRepository.save(createLecturer(user, request.lecturer()));
        }
        return identityMapper.toUser(user);
    }

    @Transactional
    @BusinessAudit(action = "USER_STATUS_UPDATE", entityType = "USER", entityIdArgument = 0)
    public UserResponse updateStatus(Long id, UpdateUserStatusRequest request) {
        User user = requireUser(id);
        user.setStatus(request.status());
        user.setUpdatedAt(LocalDateTime.now(clock));
        return identityMapper.toUser(user);
    }

    @Transactional
    @BusinessAudit(action = "USER_ROLES_UPDATE", entityType = "USER", entityIdArgument = 0)
    public UserResponse updateRoles(Long id, UpdateUserRolesRequest request) {
        User user = requireUser(id);
        if (studentRepository.findByUserId(id).isPresent() && !request.roles().contains(RoleCode.STUDENT)) {
            throw new BusinessRuleException("Remove the student profile before removing the STUDENT role.");
        }
        if (lecturerRepository.findByUserId(id).isPresent() && !request.roles().contains(RoleCode.LECTURER)) {
            throw new BusinessRuleException("Remove the lecturer profile before removing the LECTURER role.");
        }
        if (request.roles().contains(RoleCode.STUDENT) && studentRepository.findByUserId(id).isEmpty()) {
            throw new BusinessRuleException("A student profile is required for the STUDENT role.");
        }
        if (request.roles().contains(RoleCode.LECTURER) && lecturerRepository.findByUserId(id).isEmpty()) {
            throw new BusinessRuleException("A lecturer profile is required for the LECTURER role.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        user.replaceRoles(requireRoles(request.roles()), now);
        user.setUpdatedAt(now);
        return identityMapper.toUser(user);
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

    private void validateProfiles(
            Set<RoleCode> roles, StudentProfileInput student, LecturerProfileInput lecturer) {
        if (roles.contains(RoleCode.STUDENT) != (student != null)) {
            throw new BusinessRuleException("The STUDENT role and student profile must be supplied together.");
        }
        if (roles.contains(RoleCode.LECTURER) != (lecturer != null)) {
            throw new BusinessRuleException("The LECTURER role and lecturer profile must be supplied together.");
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
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User account was not found."));
    }

    private Department requireDepartment(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department was not found."));
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
