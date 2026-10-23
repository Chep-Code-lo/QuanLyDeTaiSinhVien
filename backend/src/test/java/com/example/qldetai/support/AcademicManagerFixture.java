package com.example.qldetai.support;

import com.example.qldetai.feature.academic.domain.Department;
import com.example.qldetai.feature.identity.domain.*;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.HashSet;

/** Explicit dual-role dean for academic workflow fixtures; production ADMIN receives no appointment. */
public final class AcademicManagerFixture {
    private AcademicManagerFixture() {}

    public static void appointDean(EntityManager em, User account, Department department, LocalDateTime now) {
        var roles = new HashSet<Role>();
        account.getUserRoles().forEach(r -> roles.add(r.getRole()));
        roles.add(em.createQuery("select r from Role r where r.code = :code", Role.class)
                .setParameter("code", RoleCode.LECTURER).getSingleResult());
        account.replaceRoles(roles, now);
        em.merge(account);
        Lecturer lecturer = new Lecturer();
        lecturer.setUser(account);
        lecturer.setDepartment(department);
        lecturer.setLecturerCode("M" + account.getId());
        em.persist(lecturer);
        AcademicAppointment appointment = new AcademicAppointment();
        appointment.setLecturer(lecturer);
        appointment.setFaculty(department.getFaculty());
        appointment.setPositionCode(PositionCode.DEAN);
        appointment.setStartsAt(now.minusDays(1));
        appointment.setAssignedBy(account);
        em.persist(appointment);
        em.flush();
    }
}
