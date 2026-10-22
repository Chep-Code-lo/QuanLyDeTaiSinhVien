package com.example.qldetai.feature.identity.service;

import com.example.qldetai.feature.identity.domain.Role;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.repository.RoleRepository;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@Order(0)
public class RoleCatalogInitializer implements ApplicationRunner {

    private static final Map<RoleCode, String> ROLE_NAMES =
            Map.of(
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
        ROLE_NAMES.forEach(
                (code, name) ->
                        roleRepository
                                .findByCode(code)
                                .orElseGet(() -> roleRepository.save(new Role(code, name))));
    }
}
