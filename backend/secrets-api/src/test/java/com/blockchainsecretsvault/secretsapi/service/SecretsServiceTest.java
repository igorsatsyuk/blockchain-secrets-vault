package com.blockchainsecretsvault.secretsapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.blockchainsecretsvault.secretsapi.api.CreateSecretRequest;
import com.blockchainsecretsvault.secretsapi.api.UpdateSecretRequest;
import com.blockchainsecretsvault.secretsapi.model.SecretRecord;
import com.blockchainsecretsvault.secretsapi.repository.InMemorySecretRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class SecretsServiceTest {

    private final InMemorySecretRepository repository = new InMemorySecretRepository();
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);
    private final SecretsService service = new SecretsService(repository, clock);

    @Test
    void createsSecretWithNormalizedFields() {
        CreateSecretRequest request = new CreateSecretRequest(
                "  payment-api  ",
                "  tokens  ",
                "secret-value",
                Set.of(" PROD ", "api")
        );

        StepVerifier.create(service.create(request))
                .assertNext(secret -> {
                    assertThat(secret.id()).isNotNull();
                    assertThat(secret.name()).isEqualTo("payment-api");
                    assertThat(secret.description()).isEqualTo("tokens");
                    assertThat(secret.payload()).isEqualTo("secret-value");
                    assertThat(secret.tags()).containsExactlyInAnyOrder("prod", "api");
                    assertThat(secret.createdAt()).isEqualTo(clock.instant());
                    assertThat(secret.updatedAt()).isEqualTo(clock.instant());
                })
                .verifyComplete();
    }

    @Test
    void normalizesTagsWithStableLocale() {
        Locale previousDefault = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
        try {
            CreateSecretRequest request = new CreateSecretRequest("alpha", null, "payload", Set.of("IDENTITY"));

            StepVerifier.create(service.create(request))
                    .assertNext(secret -> assertThat(secret.tags()).containsExactly("identity"))
                    .verifyComplete();
        } finally {
            Locale.setDefault(previousDefault);
        }
    }

    @Test
    void rejectsDuplicateCreateByNameIgnoringCase() {
        service.create(new CreateSecretRequest("payment-api", null, "one", Set.of())).block();

        StepVerifier.create(service.create(new CreateSecretRequest("PAYMENT-API", null, "two", Set.of())))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(DuplicateSecretNameException.class)
                        .hasMessageContaining("PAYMENT-API"))
                .verify();
    }

    @Test
    void rejectsDuplicateCreateWhenNameDiffersOnlyByWhitespace() {
        service.create(new CreateSecretRequest("alpha", null, "one", Set.of())).block();

        StepVerifier.create(service.create(new CreateSecretRequest("  alpha  ", null, "two", Set.of())))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(DuplicateSecretNameException.class)
                        .hasMessageContaining("alpha"))
                .verify();
    }

    @Test
    void listsAndGetsExistingSecrets() {
        SecretRecord created = service.create(new CreateSecretRequest("alpha", null, "payload", Set.of())).block();

        StepVerifier.create(service.list())
                .expectNext(created)
                .verifyComplete();

        StepVerifier.create(service.get(created.id()))
                .expectNext(created)
                .verifyComplete();
    }

    @Test
    void failsWhenGettingMissingSecret() {
        UUID missing = UUID.randomUUID();

        StepVerifier.create(service.get(missing))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(SecretNotFoundException.class)
                        .hasMessageContaining(missing.toString()))
                .verify();
    }

    @Test
    void updatesOnlyProvidedFields() {
        SecretRecord created = service.create(new CreateSecretRequest(
                "alpha",
                "description",
                "payload",
                Set.of("one")
        )).block();

        UpdateSecretRequest request = new UpdateSecretRequest(" beta ", null, "new-payload", Set.of(" TWO "));

        StepVerifier.create(service.update(created.id(), request))
                .assertNext(updated -> {
                    assertThat(updated.id()).isEqualTo(created.id());
                    assertThat(updated.name()).isEqualTo("beta");
                    assertThat(updated.description()).isEqualTo("description");
                    assertThat(updated.payload()).isEqualTo("new-payload");
                    assertThat(updated.tags()).containsExactly("two");
                    assertThat(updated.createdAt()).isEqualTo(created.createdAt());
                    assertThat(updated.updatedAt()).isEqualTo(clock.instant());
                })
                .verifyComplete();
    }

    @Test
    void updateCanKeepNameWhenBlankAndClearDescriptionAndTags() {
        SecretRecord created = service.create(new CreateSecretRequest(
                "alpha",
                "description",
                "payload",
                Set.of("one")
        )).block();

        UpdateSecretRequest request = new UpdateSecretRequest("  ", "  ", null, Set.of("", " TWO "));

        StepVerifier.create(service.update(created.id(), request))
                .assertNext(updated -> {
                    assertThat(updated.name()).isEqualTo("alpha");
                    assertThat(updated.description()).isNull();
                    assertThat(updated.payload()).isEqualTo("payload");
                    assertThat(updated.tags()).containsExactly("two");
                })
                .verifyComplete();
    }

    @Test
    void updateAllowsKeepingSameNameAndFailsWhenMissingSecret() {
        SecretRecord created = service.create(new CreateSecretRequest("alpha", null, "payload", null)).block();

        StepVerifier.create(service.update(created.id(), new UpdateSecretRequest("ALPHA", null, null, null)))
                .assertNext(updated -> assertThat(updated.name()).isEqualTo("ALPHA"))
                .verifyComplete();

        UUID missing = UUID.randomUUID();
        StepVerifier.create(service.update(missing, new UpdateSecretRequest("missing", null, null, null)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(SecretNotFoundException.class)
                        .hasMessageContaining(missing.toString()))
                .verify();
    }

    @Test
    void rejectsEmptyUpdateAndDuplicateRename() {
        SecretRecord first = service.create(new CreateSecretRequest("first", null, "payload", Set.of())).block();
        service.create(new CreateSecretRequest("second", null, "payload", Set.of())).block();

        StepVerifier.create(service.update(first.id(), new UpdateSecretRequest(null, null, null, null)))
                .expectError(EmptySecretUpdateException.class)
                .verify();

        StepVerifier.create(service.update(first.id(), new UpdateSecretRequest("second", null, null, null)))
                .expectError(DuplicateSecretNameException.class)
                .verify();
    }

    @Test
    void deletesExistingSecretAndFailsForMissingSecret() {
        SecretRecord created = service.create(new CreateSecretRequest("alpha", null, "payload", Set.of())).block();

        StepVerifier.create(service.delete(created.id())).verifyComplete();
        StepVerifier.create(service.delete(created.id()))
                .expectError(SecretNotFoundException.class)
                .verify();
    }

    @Test
    void detectsEmptyUpdateRequestOnlyWhenAllFieldsAreMissing() {
        assertThat(new UpdateSecretRequest(null, null, null, null).isEmpty()).isTrue();
        assertThat(new UpdateSecretRequest("name", null, null, null).isEmpty()).isFalse();
        assertThat(new UpdateSecretRequest(null, "description", null, null).isEmpty()).isFalse();
        assertThat(new UpdateSecretRequest(null, null, "payload", null).isEmpty()).isFalse();
        assertThat(new UpdateSecretRequest(null, null, null, Set.of("tag")).isEmpty()).isFalse();
    }
}
