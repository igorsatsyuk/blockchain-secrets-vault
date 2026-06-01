package com.blockchainsecretsvault.secretsapi.api;

import com.blockchainsecretsvault.secretsapi.service.SecretsService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/secrets")
public class SecretsController {

    private final SecretsService secretsService;

    public SecretsController(SecretsService secretsService) {
        this.secretsService = secretsService;
    }

    @PostMapping
    public Mono<ResponseEntity<SecretResponse>> create(@Valid @RequestBody CreateSecretRequest request) {
        return secretsService.create(request)
                .map(SecretResponse::from)
                .map(response -> ResponseEntity
                        .created(URI.create("/api/v1/secrets/" + response.id()))
                        .body(response));
    }

    @GetMapping
    public Mono<List<SecretResponse>> list() {
        return secretsService.list()
                .map(SecretResponse::from)
                .collectList();
    }

    @GetMapping("/{id}")
    public Mono<SecretResponse> get(@PathVariable("id") UUID id) {
        return secretsService.get(id).map(SecretResponse::from);
    }

    @PutMapping("/{id}")
    public Mono<SecretResponse> update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateSecretRequest request
    ) {
        return secretsService.update(id, request).map(SecretResponse::from);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable("id") UUID id) {
        return secretsService.delete(id).thenReturn(ResponseEntity.noContent().build());
    }
}
