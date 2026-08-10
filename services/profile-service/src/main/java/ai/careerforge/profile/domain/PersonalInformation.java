package ai.careerforge.profile.domain;

import java.util.List;

/** Embedded — bounded, read with the profile (docs/DATABASE.md &sect;2). */
public class PersonalInformation {

    private String fullName;
    private String headline;
    private String email;
    private String phone;
    private List<String> links = List.of();

    protected PersonalInformation() {
        // Spring Data
    }

    public PersonalInformation(String fullName, String headline, String email, String phone, List<String> links) {
        this.fullName = fullName;
        this.headline = headline;
        this.email = email;
        this.phone = phone;
        this.links = links == null ? List.of() : List.copyOf(links);
    }

    public String fullName() {
        return fullName;
    }

    public String headline() {
        return headline;
    }

    public String email() {
        return email;
    }

    public String phone() {
        return phone;
    }

    public List<String> links() {
        return links;
    }
}
