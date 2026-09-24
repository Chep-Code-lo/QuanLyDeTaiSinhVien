package com.example.qldetai.security;

import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.repository.StudentRepository;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.UserRepository;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;

    public DatabaseUserDetailsService(
            UserRepository userRepository,
            StudentRepository studentRepository,
            LecturerRepository lecturerRepository) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String normalized = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(normalized)
                .or(() -> studentRepository.findByStudentCodeIgnoreCase(normalized).map(student -> student.getUser()))
                .or(() -> lecturerRepository.findByLecturerCodeIgnoreCase(normalized).map(lecturer -> lecturer.getUser()))
                .orElseThrow(() -> new UsernameNotFoundException("Account not found."));
        return UserPrincipal.from(user);
    }
}
