package com.example.qldetai.security;

import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;


    @Override
    @Transactional
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String normalized = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        // Resolve only the ID first: loading a managed User before waiting on the
        // lock could retain a stale password snapshot. AuthService's transaction
        // holds this same row lock through authentication and session creation.
        Long id = userRepository.findLoginIdByEmail(normalized)
                .or(() -> userRepository.findLoginIdByStudentCode(normalized))
                .or(() -> userRepository.findLoginIdByLecturerCode(normalized))
                .orElseThrow(() -> new UsernameNotFoundException("Account not found."));
        User user = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found."));
        return UserPrincipal.from(user);
    }
}
