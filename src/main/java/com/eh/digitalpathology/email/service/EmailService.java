package com.eh.digitalpathology.email.service;

import com.eh.digitalpathology.email.config.EmailConfig;
import com.eh.digitalpathology.email.config.EmailTemplateConfig;
import com.eh.digitalpathology.email.model.EmailMessagePayload;
import com.eh.digitalpathology.email.model.EmailTemplate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JavaMailSender mailSender;
    private final EmailConfig emailConfig;

    private final EmailTemplateConfig emailTemplateConfig;

    public EmailService( JavaMailSender mailSender, EmailConfig emailConfig, EmailTemplateConfig emailTemplateConfig) {
        this.mailSender = mailSender;
        this.emailConfig = emailConfig;
        this.emailTemplateConfig = emailTemplateConfig;
    }

    private static final String BARCODE_LITERAL = "${barcode}";

    public void sendEmail(String key, String value) {
        EmailTemplate emailTemplate = emailTemplateConfig.getTemplate(key);

        if (emailTemplate == null) {
            log.info("sendEmail :: Template for key {} not found.", key);
            return;
        }


        try {
            if (Objects.nonNull(emailTemplate.getSubject()) &&
                    Objects.nonNull(emailTemplate.getBody())) {
                String subjectTemplate = emailTemplate.getSubject();
                String bodyTemplate = emailTemplate.getBody();
                String subject = "";
                String body = "";
                String recipientList;
                EmailMessagePayload payload ;

                if (value.trim().startsWith("{")) {
                    // Deserialize JSON into payload
                    payload = objectMapper.readValue(value, EmailMessagePayload.class);
                } else {
                    // Handle plain string by wrapping it in a payload
                    payload = new EmailMessagePayload();
                    payload.setBarcode(value);
                }

                if (key.contains("IBEX")) {
                    recipientList = emailConfig.getIbexTo();
                    subject = subjectTemplate;
                    body = populateEmailBody(emailTemplate.getBody(), payload);
                } else {
                    recipientList = emailConfig.getTo();
                    subject = populateEmailBody(subjectTemplate, payload);
                    body = populateEmailBody(bodyTemplate, payload);
                }
                sendEmailMessage(recipientList, body, subject);
            } else {
                log.info("sendEmail :: Subject and Body for the key {} not found. ", key);
            }
        } catch (JsonProcessingException e) {
            log.error("sendEmail :: JsonProcessingException while sending the email ", e);
        }
    }

    private String populateEmailBody(String template, EmailMessagePayload payload) {
        String body = template;
        if (payload.getBarcode() != null) {
            body = body.replace(BARCODE_LITERAL, payload.getBarcode());
        }
        if (payload.getErrorCode() != 0) {
            body = body.replace("${errorCode}", String.valueOf(payload.getErrorCode()));
        }
        if (payload.getErrorMsg() != null) {
            body = body.replace("${errorMessage}", payload.getErrorMsg());
        }
        if (payload.getSubjectId() != null) {
            body = body.replace(BARCODE_LITERAL, payload.getSubjectId());
        }
        if (payload.getMissingTag() != null) {
            body = body.replace("${missingTag}", payload.getMissingTag());
        }
        return body;
    }

    private void sendEmailMessage(String recipientList, String body, String subject) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(new InternetAddress(emailConfig.getFrom()));
            setRecipients(helper, recipientList);
            helper.setText(body);
            helper.setSubject(subject);
            mailSender.send(message);
            log.info("sendEmail :: Email sent successfully.");
        } catch (MessagingException e) {
            log.error("sendEmail :: MessagingException while sending the email ", e);
        }

    }

    private void setRecipients(MimeMessageHelper helper, String recipients) throws MessagingException {
        String[] toVal = recipients.split(",");
        InternetAddress[] recipientAddresses = new InternetAddress[toVal.length];
        for (int i = 0; i < toVal.length; i++) {
            recipientAddresses[i] = new InternetAddress(toVal[i]);
        }
        helper.setTo(recipientAddresses);
    }
}
