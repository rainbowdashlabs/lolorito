package de.chojo.lolorito.config.elements;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class Links {
    private String tos = "";
    private String invite = "https://discord.com/oauth2/authorize?client_id=868279025251012639&scope=bot&permissions=1342532672";
    private String support = "";
    private String website = "https://rainbowdashlabs.github.io/lolorito/";
    private String faq = "https://rainbowdashlabs.github.io/lolorito/faq";

    public String tos() {
        return tos;
    }

    public String invite() {
        return invite;
    }

    public String support() {
        return support;
    }

    public String website() {
        return website;
    }

    public String faq() {
        return faq;
    }
}
