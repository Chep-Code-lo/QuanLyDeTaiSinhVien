package com.example.qldetai.feature.topic;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.qldetai.aspect.AuditLogWriter;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Uses the actual MySQL schema/triggers; business test rows are rolled back. */
@EnabledIfEnvironmentVariable(named = "MYSQL_SCHEMA_SMOKE_ENABLED", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=${MYSQL_SMOKE_DB_URL}",
        "spring.datasource.username=${MYSQL_SMOKE_DB_USERNAME}",
        "spring.datasource.password=${MYSQL_SMOKE_DB_PASSWORD}",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.demo-data.enabled=false"
})
class RegistrationTopicMySqlIntegrationTests extends RegistrationTopicIntegrationTests {
    @Autowired private MockMvc mysqlMvc;
    @Autowired private UserRepository mysqlUsers;

    // REQUIRES_NEW audit writes must not persist smoke-test rows outside the rollback.
    @MockitoBean private AuditLogWriter auditLogWriter;

    @Test
    void formCatalogEndpointsLoadWithTheExistingMySqlSchema() throws Exception {
        var admin = mysqlUsers.findAll().stream()
                .filter(actor -> actor.getUserRoles().stream()
                        .anyMatch(role -> role.getRole().getCode() == RoleCode.ADMIN))
                .findFirst().orElseThrow();
        var principal = UserPrincipal.from(admin);
        mysqlMvc.perform(get("/api/v1/registration-periods").param("size", "100").with(user(principal)))
                .andExpect(status().isOk());
        mysqlMvc.perform(get("/api/v1/departments").with(user(principal)))
                .andExpect(status().isOk());
        mysqlMvc.perform(get("/api/v1/lecturers").param("size", "100").with(user(principal)))
                .andExpect(status().isOk());
    }
}
