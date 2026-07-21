package com.eh.digitalpathology.email.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@RefreshScope
public class MailConfig {

    @Value("${spring.mail.host}")
    private String host;

    @Value("${spring.mail.port}")
    private int port;

    @Value("${spring.mail.smtp.auth}")
    private String smtpAuth;

    @Value("${spring.mail.smtp.starttls.enable}")
    private String startTlsEnable;

    @Bean
    public JavaMailSender javaMailSender ( ) {
        JavaMailSenderImpl mailSender=new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", smtpAuth);
        props.put("mail.smtp.starttls.enable", startTlsEnable);
        return mailSender;

    }
}

