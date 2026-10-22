package com.example.qldetai.feature.identity;

import static org.assertj.core.api.Assertions.assertThat;
import com.example.qldetai.feature.identity.domain.*;
import com.example.qldetai.feature.identity.dto.IdentityDtos.UpdateUserStatusRequest;
import com.example.qldetai.feature.identity.repository.*;
import com.example.qldetai.feature.identity.service.UserAdminService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admin-audit;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserAdminConcurrencyTests {
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserAdminService service;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void concurrentStatusChangesCannotDisableAllAdministrators() throws Exception {
        var ids = new TransactionTemplate(transactions).execute(status -> List.of(create("one"), create("two")));
        assertThat(users.findActiveAdministrators()).hasSize(2);
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> lock(ids.get(0), barrier));
            var second = executor.submit(() -> lock(ids.get(1), barrier));
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(users.findActiveAdministrators()).hasSize(1);
    }

    private Long create(String name) {
        User user = new User(); user.setEmail(name + "@admin-audit.local"); user.setFullName(name);
        user.setPasswordHash("not-a-real-password"); user.setStatus(UserStatus.ACTIVE);
        user.replaceRoles(Set.of(roles.findByCode(RoleCode.ADMIN).orElseThrow()), LocalDateTime.now());
        return users.saveAndFlush(user).getId();
    }

    private boolean lock(Long id, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try { service.updateStatus(id, new UpdateUserStatusRequest(UserStatus.LOCKED)); return true; }
        catch (com.example.qldetai.exception.BusinessRuleException expected) { return false; }
    }
}
