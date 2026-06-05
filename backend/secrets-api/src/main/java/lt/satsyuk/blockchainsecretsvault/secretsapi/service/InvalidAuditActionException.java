package lt.satsyuk.blockchainsecretsvault.secretsapi.service;

public class InvalidAuditActionException extends RuntimeException {

    public InvalidAuditActionException(String action) {
        super("Invalid audit action: " + action);
    }
}
