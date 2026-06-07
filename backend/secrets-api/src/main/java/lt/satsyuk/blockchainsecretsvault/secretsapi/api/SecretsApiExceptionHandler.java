package lt.satsyuk.blockchainsecretsvault.secretsapi.api;

import lt.satsyuk.blockchainsecretsvault.secretsapi.auth.InvalidCredentialsException;
import lt.satsyuk.blockchainsecretsvault.secretsapi.service.DuplicateSecretNameException;
import lt.satsyuk.blockchainsecretsvault.secretsapi.service.EmptySecretUpdateException;
import lt.satsyuk.blockchainsecretsvault.secretsapi.service.InvalidAuditActionException;
import lt.satsyuk.blockchainsecretsvault.secretsapi.service.InvalidBlockchainAccountException;
import lt.satsyuk.blockchainsecretsvault.secretsapi.service.SecretNotFoundException;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.BlockchainAclException;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice
public class SecretsApiExceptionHandler {

    private static final HttpStatus BAD_REQUEST = HttpStatus.BAD_REQUEST;
    private static final HttpStatus BAD_GATEWAY = HttpStatus.BAD_GATEWAY;

    @ExceptionHandler(SecretNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(SecretNotFoundException exception) {
        return ErrorResponse.of(404, "Not Found", exception.getMessage());
    }

    @ExceptionHandler(DuplicateSecretNameException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(DuplicateSecretNameException exception) {
        return ErrorResponse.of(409, "Conflict", exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidCredentials(InvalidCredentialsException exception) {
        return ErrorResponse.of(401, "Unauthorized", exception.getMessage());
    }

    @ExceptionHandler(EmptySecretUpdateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleEmptyUpdate(EmptySecretUpdateException exception) {
        return ErrorResponse.of(BAD_REQUEST.value(), BAD_REQUEST.getReasonPhrase(), exception.getMessage());
    }

    @ExceptionHandler(InvalidBlockchainAccountException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidBlockchainAccount(InvalidBlockchainAccountException exception) {
        return ErrorResponse.of(BAD_REQUEST.value(), BAD_REQUEST.getReasonPhrase(), exception.getMessage());
    }

    @ExceptionHandler(InvalidAuditActionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidAuditAction(InvalidAuditActionException exception) {
        return ErrorResponse.of(BAD_REQUEST.value(), BAD_REQUEST.getReasonPhrase(), exception.getMessage());
    }

    @ExceptionHandler(BlockchainAclException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorResponse handleBlockchainAcl(BlockchainAclException exception) {
        return ErrorResponse.of(BAD_GATEWAY.value(), BAD_GATEWAY.getReasonPhrase(), exception.getMessage());
    }

    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(WebExchangeBindException exception) {
        Map<String, String> details = exception.getFieldErrors().stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        TreeMap::new,
                        Collectors.mapping(
                                error -> error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage(),
                                Collectors.collectingAndThen(
                                        Collectors.toCollection(TreeSet::new),
                                        messages -> String.join("; ", messages)
                                )
                        )
                ));
        return ErrorResponse.withDetails(
                BAD_REQUEST.value(),
                BAD_REQUEST.getReasonPhrase(),
                "Request validation failed",
                details
        );
    }

    @ExceptionHandler(ServerWebInputException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadInput(ServerWebInputException exception) {
        return ErrorResponse.of(BAD_REQUEST.value(), BAD_REQUEST.getReasonPhrase(), "Malformed request");
    }
}

