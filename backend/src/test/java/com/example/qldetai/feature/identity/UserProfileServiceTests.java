package com.example.qldetai.feature.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.qldetai.feature.academic.domain.Department;
import com.example.qldetai.feature.identity.domain.Lecturer;
import com.example.qldetai.feature.identity.domain.Student;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.dto.IdentityDtos.UserResponse;
import com.example.qldetai.feature.identity.mapper.IdentityMapper;
import com.example.qldetai.feature.identity.repository.AcademicAppointmentRepository;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.repository.StudentRepository;
import com.example.qldetai.feature.identity.service.UserProfileService;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.util.List;

class UserProfileServiceTests {
    private final StudentRepository students = mock(StudentRepository.class);
    private final LecturerRepository lecturers = mock(LecturerRepository.class);
    private final AcademicAppointmentRepository appointments =
            mock(AcademicAppointmentRepository.class);
    private final UserProfileService profiles =
            new UserProfileService(
                    students,
                    lecturers,
                    appointments,
                    Mappers.getMapper(IdentityMapper.class),
                    Clock.systemUTC());

    @Test
    void batchProfilesAreMatchedByUserIdAndPreservePagination() {
        User studentUser = user(1L);
        User lecturerUser = user(2L);
        User admin = user(3L);
        Department department = new Department();
        department.setId(9L);
        Student student = new Student();
        student.setId(11L);
        student.setUser(studentUser);
        student.setStudentCode("SV01");
        student.setDepartment(department);
        Lecturer lecturer = new Lecturer();
        lecturer.setId(12L);
        lecturer.setUser(lecturerUser);
        lecturer.setLecturerCode("GV01");
        lecturer.setDepartment(department);
        // Page order deliberately differs from repository result order.
        List<Long> ids = List.of(3L, 2L, 1L);
        when(students.findByUserIdIn(ids)).thenReturn(List.of(student));
        when(lecturers.findByUserIdIn(ids)).thenReturn(List.of(lecturer));
        Page<User> page =
                new PageImpl<>(List.of(admin, lecturerUser, studentUser), PageRequest.of(1, 3), 9);

        Page<UserResponse> result = profiles.toUsers(page);

        assertThat(result.getTotalElements()).isEqualTo(9);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getContent()).extracting(UserResponse::id).containsExactly(3L, 2L, 1L);
        assertThat(result.getContent().get(0).student()).isNull();
        assertThat(result.getContent().get(0).lecturer()).isNull();
        assertThat(result.getContent().get(1).lecturer().lecturerCode()).isEqualTo("GV01");
        assertThat(result.getContent().get(1).student()).isNull();
        assertThat(result.getContent().get(2).student().studentCode()).isEqualTo("SV01");
        assertThat(result.getContent().get(2).lecturer()).isNull();
        verify(students).findByUserIdIn(ids);
        verify(lecturers).findByUserIdIn(ids);
        verifyNoMoreInteractions(students, lecturers);
        verifyNoInteractions(appointments);
    }

    @Test
    void emptyPageDoesNotQueryProfilesAndKeepsPageMetadata() {
        Page<User> page = new PageImpl<>(List.of(), PageRequest.of(3, 10), 21);

        Page<UserResponse> result = profiles.toUsers(page);

        assertThat(result).isEmpty();
        assertThat(result.getNumber()).isEqualTo(3);
        assertThat(result.getTotalElements()).isEqualTo(21);
        verifyNoInteractions(students, lecturers, appointments);
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@test.local");
        user.setFullName("User " + id);
        return user;
    }
}
