package lt.satsyuk.blockchainsecretsvault.secretsapi.auth;

import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationWebFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String BEARER_SCHEME = "bearer";

    private final JwtService jwtService;

    public JwtAuthenticationWebFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !isBearer(header)) {
            return chain.filter(exchange);
        }

        try {
            String subject = jwtService.validate(header.substring(BEARER_PREFIX.length()).trim());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    subject,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
        } catch (JwtAuthenticationException _) {
            return chain.filter(exchange);
        }
    }

    private static boolean isBearer(String header) {
        return header.length() > BEARER_PREFIX.length()
                && header.charAt(BEARER_PREFIX.length() - 1) == ' '
                && BEARER_SCHEME.equals(header.substring(0, BEARER_PREFIX.length() - 1).toLowerCase(Locale.ROOT));
    }
}
