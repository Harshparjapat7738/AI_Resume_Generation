package ai.careerforge.profile.domain;

/** Embedded — docs/DATABASE.md &sect;3 shape, plus {@code credentialUrl} (additive). */
public class Certification {

    private String evidenceId;
    private String name;
    private String issuer;
    private String issuedOn;
    private String expiresOn;
    private String credentialId;
    private String credentialUrl;

    protected Certification() {
        // Spring Data
    }

    public Certification(String evidenceId, String name, String issuer, String issuedOn,
                         String expiresOn, String credentialId, String credentialUrl) {
        this.evidenceId = evidenceId;
        this.name = name;
        this.issuer = issuer;
        this.issuedOn = issuedOn;
        this.expiresOn = expiresOn;
        this.credentialId = credentialId;
        this.credentialUrl = credentialUrl;
    }

    public String evidenceId() {
        return evidenceId;
    }

    public String name() {
        return name;
    }

    public String issuer() {
        return issuer;
    }

    public String issuedOn() {
        return issuedOn;
    }

    public String expiresOn() {
        return expiresOn;
    }

    public String credentialId() {
        return credentialId;
    }

    public String credentialUrl() {
        return credentialUrl;
    }
}
