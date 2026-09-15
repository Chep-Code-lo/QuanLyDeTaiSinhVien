package com.example.qldetai.feature.identity;

import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(0)
public class RoleCatalogInitializer implements ApplicationRunner {

    private static final Map<RoleCode, String> ROLE_NAMES = Map.of(
            RoleCode.STUDENT, "Sinh viên",
            RoleCode.LECTURER, "Giảng viên",
            RoleCode.ADMIN, "Quản trị hệ thống");

    private final RoleRepository roleRepository;

    public RoleCatalogInitializer(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ROLE_NAMES.forEach((code, name) -> roleRepository.findByCode(code)
                .orElseGet(() -> roleRepository.save(new Role(code, name))));
    }
}
