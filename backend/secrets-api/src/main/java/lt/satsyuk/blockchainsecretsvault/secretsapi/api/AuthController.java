package lt.satsyuk.blockchainsecretsvault.secretsapi.api;

import jakarta.validation.Valid;
import lt.satsyuk.blockchainsecretsvault.secretsapi.auth.AuthService;
import lt.satsyuk.blockchainsecretsvault.secretsapi.config.AuthProperties;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthProperties properties;

    public AuthController(AuthService authService, AuthProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/login")
    public Mono<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Mono.fromSupplier(() -> new AuthResponse(
                "Bearer",
                authService.login(request.username().trim(), request.password()),
                properties.tokenTtl().toSeconds()
        ));
    }
}
