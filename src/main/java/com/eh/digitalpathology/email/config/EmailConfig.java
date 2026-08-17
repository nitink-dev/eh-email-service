package com.eh.digitalpathology.email.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "email")
@RefreshScope
public class EmailConfig {

    private String to;
    private String from;
    private String ibexTo;

    public String getTo ( ) {
        return to;
    }

    public void setTo ( String to ) {
        this.to = to;
    }

    public String getFrom ( ) {
        return from;
    }

    public void setFrom ( String from ) {
        this.from = from;
    }

    public String getIbexTo ( ) {
        return ibexTo;
    }

    public void setIbexTo ( String ibexTo ) {
        this.ibexTo = ibexTo;
    }
}
