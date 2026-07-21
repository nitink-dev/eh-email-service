package com.eh.digitalpathology.email.config;

import com.eh.digitalpathology.email.model.EmailTemplate;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "email")
@RefreshScope
public class EmailTemplateConfig {

    private Map<String, EmailTemplate> templates;

    public Map<String, EmailTemplate> getTemplates() {
        return templates;
    }

    public void setTemplates(Map<String, EmailTemplate> templates) {
        this.templates = templates;
    }

    public EmailTemplate getTemplate(String key) {
        return templates.getOrDefault(key, new EmailTemplate());
    }

}
