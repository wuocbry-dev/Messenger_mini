package com.team503.messenger.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    /** Sender address; for Gmail it must be the authenticated account or a verified alias. */
    private String from;

    /** Display name shown next to the sender address. */
    private String fromName = "Messenger Mini";

    /** Public frontend URL used for links inside emails. */
    private String appUrl = "http://localhost:5173";

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getAppUrl() {
        return appUrl;
    }

    public void setAppUrl(String appUrl) {
        this.appUrl = appUrl;
    }
}
