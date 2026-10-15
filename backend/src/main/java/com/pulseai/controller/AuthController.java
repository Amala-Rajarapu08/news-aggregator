package com.pulseai.controller;
import com.pulseai.dto.auth.*;
import com.pulseai.service.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final BrowserSessionService sessions;
    private final boolean secureCookie;
    private final Set<String> origins;
    public AuthController(AuthService auth, BrowserSessionService sessions,
            @Value("${app.jwt.cookie-secure:true}") boolean secureCookie,
            @Value("${app.cors.allowed-origins}") String origins) {
        this.auth = auth; this.sessions = sessions; this.secureCookie = secureCookie;
        this.origins = new HashSet<>(Arrays.asList(origins.split(",")));
    }
    @PostMapping("/register") public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegistrationRequest request, HttpServletRequest servlet) {
        checkOrigin(servlet); var result = auth.register(request);
        return ResponseEntity.created(URI.create("/api/v1/users/me")).header(HttpHeaders.SET_COOKIE, cookie(result.refreshToken(), result.maxAge())).body(result.response());
    }
    @PostMapping("/login") public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servlet) {
        checkOrigin(servlet); var result = auth.login(request);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(result.refreshToken(), result.maxAge())).body(result.response());
    }
    @PostMapping("/refresh") public ResponseEntity<AuthResponse> refresh(@CookieValue(name = "pulse_refresh", required = false) String raw, HttpServletRequest request) {
        checkOrigin(request); var result = sessions.refresh(raw);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(result.refreshToken(), result.maxAge())).body(result.response());
    }
    @PostMapping("/logout") public ResponseEntity<Void> logout(@CookieValue(name = "pulse_refresh", required = false) String raw, HttpServletRequest request) {
        checkOrigin(request); if (raw != null) sessions.logout(raw);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", 0)).build();
    }
    private String cookie(String raw, long age) { return ResponseCookie.from("pulse_refresh", raw).httpOnly(true).secure(secureCookie).sameSite("Strict").path("/api/v1/auth").maxAge(age).build().toString(); }
    private void checkOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if ((origin != null && origins.stream().map(String::trim).noneMatch(origin::equals)) || "cross-site".equals(request.getHeader("Sec-Fetch-Site")))
            throw new AccessDeniedException("Untrusted request origin.");
    }
}
