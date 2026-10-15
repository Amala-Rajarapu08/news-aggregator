package com.pulseai.service;
import com.pulseai.dto.auth.AuthResponse;
import com.pulseai.entity.*;
import com.pulseai.repository.*;
import com.pulseai.security.JwtTokenService;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class BrowserSessionService {
    private final BrowserSessionRepository sessions;
    private final UserRepository users;
    private final JwtTokenService tokens;
    private final UserMapper mapper;
    private final SecureRandom random = new SecureRandom();
    public BrowserSessionService(BrowserSessionRepository sessions, UserRepository users, JwtTokenService tokens, UserMapper mapper) {
        this.sessions = sessions; this.users = users; this.tokens = tokens; this.mapper = mapper;
    }
    @Transactional public Result create(User user) {
        sessions.deleteByExpiresAtBefore(Instant.now());
        if (sessions.countByUserId(user.getId()) >= 20) throw new IllegalArgumentException("Too many active sessions. Sign out another device first.");
        String raw = randomToken();
        var session = sessions.saveAndFlush(new BrowserSession(user.getId(), hash(raw), Instant.now().plus(Duration.ofDays(7))));
        return result(user, session, raw);
    }
    @Transactional public Result refresh(String raw) {
        var session = sessions.findByTokenHash(hash(raw)).filter(s -> s.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BadCredentialsException("Session expired. Please sign in again."));
        var user = users.findById(session.getUserId()).filter(User::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Session is unavailable."));
        String rotated = randomToken(); session.rotate(hash(rotated)); sessions.flush();
        return result(user, session, rotated);
    }
    @Transactional public void logout(String raw) { sessions.findByTokenHash(hash(raw)).ifPresent(sessions::delete); }
    private Result result(User user, BrowserSession session, String raw) {
        return new Result(mapper.toAuthResponse(user, tokens.issue(user, session.getId())), raw,
                Math.max(0, Duration.between(Instant.now(), session.getExpiresAt()).toSeconds()));
    }
    private String randomToken() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String hash(String raw) {
        if (raw == null || raw.length() > 200) throw new BadCredentialsException("Session is unavailable.");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public record Result(AuthResponse response, String refreshToken, long maxAge) {}
}
