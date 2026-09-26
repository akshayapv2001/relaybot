package dev.relaybot.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AuthController {

    public record LoginRequest(@NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password) {
    }

    private final AuthenticationManager authManager;
    private final SecurityContextRepository contextRepository;
    private final LoginThrottle throttle;

    public AuthController(AuthenticationManager authManager, SecurityContextRepository contextRepository, LoginThrottle throttle) {
        this.authManager = authManager;
        this.contextRepository = contextRepository;
        this.throttle = throttle;
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginRequest body,
                                                     HttpServletRequest request, HttpServletResponse response) {
        String client = request.getRemoteAddr();
        if (throttle.isBlocked(client)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many failed attempts. Try again in 15 minutes."));
        }
        Authentication auth;
        try {
            auth = authManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
        } catch (AuthenticationException e) {
            throttle.recordFailure(client);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Username or password is incorrect."));
        }
        throttle.reset(client);

        request.getSession(true);
        request.changeSessionId();   // prevent session fixation
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return ResponseEntity.ok(Map.of("username", auth.getName()));
    }

    @GetMapping("/api/auth/me")
    public ResponseEntity<Map<String, String>> me(Authentication auth) {
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of("username", auth.getName()));
    }
}
