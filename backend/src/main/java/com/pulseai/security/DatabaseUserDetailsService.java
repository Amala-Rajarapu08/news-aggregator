package com.pulseai.security;

import java.util.Locale;

import com.pulseai.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(normalizedEmail)
                .map(PulseUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found."));
    }
}
