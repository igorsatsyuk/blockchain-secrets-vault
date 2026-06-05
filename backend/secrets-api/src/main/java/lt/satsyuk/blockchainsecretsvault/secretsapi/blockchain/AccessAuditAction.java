package lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain;

public enum AccessAuditAction {
    REGISTER(0),
    GRANT(1),
    REVOKE(2),
    READ(3),
    WRITE(4);

    private final int contractCode;

    AccessAuditAction(int contractCode) {
        this.contractCode = contractCode;
    }

    public int contractCode() {
        return contractCode;
    }
}
