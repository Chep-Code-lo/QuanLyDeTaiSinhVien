package com.example.qldetai.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.example.qldetai.feature.identity.domain.AcademicAppointment;
import com.example.qldetai.feature.identity.domain.PositionCode;
import com.example.qldetai.feature.identity.repository.AcademicAppointmentRepository;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
        "app.demo-data.enabled=true",
        "app.demo-data.password=Demo@123",
        "spring.datasource.url=jdbc:h2:mem:demo-appointments;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
    }
)
@AutoConfigureMockMvc
@Transactional
class DemoDataInitializerIntegrationTests {

    @Autowired
    private DemoDataInitializer initializer;

    @Autowired
    private AcademicAppointmentRepository appointmentRepository;

    @Autowired
    private LecturerRepository lecturerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Clock clock;

    @Test
    void freshDemoHasOneDeanAppointmentAndRepeatedStartupDoesNotDuplicateIt() {
        AcademicAppointment original = demoAppointment();
        assertEquals(PositionCode.DEAN, original.getPositionCode());
        assertEquals("GV001", original.getLecturer().getLecturerCode());
        assertNull(original.getEndsAt());

        runInitializerTwice();

        assertEquals(original.getId(), demoAppointment().getId());
    }

    @Test
    void endedAppointmentStaysEndedAfterStartupAndLoggingInAgain() throws Exception {
        AcademicAppointment original = demoAppointment();
        Long originalId = original.getId();
        LocalDateTime endsAt = LocalDateTime.now(clock).withNano(0).minusMinutes(1);
        original.setStartsAt(endsAt.minusDays(1));
        appointmentRepository.saveAndFlush(original);

        MockHttpSession adminSession = login("admin@demo.local");
        mockMvc
            .perform(
                patch("/api/v1/admin/academic-appointments/{id}/end", originalId)
                    .session(adminSession)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"endsAt\":\"" + endsAt + "\"}")
            )
            .andExpect(status().isOk());
        mockMvc
            .perform(post("/api/v1/auth/logout").session(adminSession).with(csrf()))
            .andExpect(status().isOk());

        runInitializerTwice();

        AcademicAppointment persisted = demoAppointment();
        assertEquals(originalId, persisted.getId());
        assertEquals(endsAt, persisted.getEndsAt());

        MockHttpSession newAdminSession = login("admin@demo.local");
        mockMvc
            .perform(
                get("/api/v1/admin/academic-appointments")
                    .session(newAdminSession)
                    .param("lecturerId", persisted.getLecturer().getId().toString())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].id").value(originalId));

        MockHttpSession lecturerSession = login("lecturer@demo.local");
        mockMvc
            .perform(get("/api/v1/auth/me").session(lecturerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.activeAppointments").isEmpty());
    }

    @Test
    void futureAppointmentIsNotDuplicatedOnStartup() {
        AcademicAppointment original = demoAppointment();
        original.setStartsAt(LocalDateTime.now(clock).withNano(0).plusDays(1));
        appointmentRepository.saveAndFlush(original);
        Long originalId = original.getId();
        LocalDateTime startsAt = original.getStartsAt();

        runInitializerTwice();

        AcademicAppointment persisted = demoAppointment();
        assertEquals(originalId, persisted.getId());
        assertEquals(startsAt, persisted.getStartsAt());
    }

    private AcademicAppointment demoAppointment() {
        Long userId = userRepository
            .findByEmailIgnoreCase("lecturer@demo.local")
            .orElseThrow()
            .getId();
        Long lecturerId = lecturerRepository.findByUserId(userId).orElseThrow().getId();
        var appointments = appointmentRepository.findForAdministration(lecturerId);
        assertEquals(1, appointments.size());
        return appointments.getFirst();
    }

    private void runInitializerTwice() {
        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());
        entityManager.flush();
        entityManager.clear();
    }

    private MockHttpSession login(String username) throws Exception {
        var result = mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + username + "\",\"password\":\"Demo@123\"}")
            )
            .andExpect(status().isOk())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
