package com.chandler.motivation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "motivation.security")
public class MotivationSecurityProperties {
    private String jwtSecret = "change-this-dev-secret";
    private long tokenExpireHours = 168;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getTokenExpireHours() {
        return tokenExpireHours;
    }

    public void setTokenExpireHours(long tokenExpireHours) {
        this.tokenExpireHours = tokenExpireHours;
    }
}
