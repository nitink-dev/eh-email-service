package com.eh.digitalpathology.email.service;

import com.eh.digitalpathology.email.config.EmailConfig;
import com.eh.digitalpathology.email.config.EmailTemplateConfig;
import com.eh.digitalpathology.email.model.EmailMessagePayload;
import com.eh.digitalpathology.email.model.EmailTemplate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Iterator;
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

    // Row background colors for alternating table stripes (inline styles only,
    // since most email clients strip <style> blocks / classes).
    private static final String ROW_BG_EVEN = "#ffffff";
    private static final String ROW_BG_ODD = "#f6f8fa";

    public void sendEmail(String key, String value) {
        EmailTemplate emailTemplate = emailTemplateConfig.getTemplate(key);

        if (emailTemplate == null) {
            log.info("sendEmail :: Template for key {} not found.", key);
            return;
        }


        try {
            if (Objects.nonNull(emailTemplate.getSubject()) &&
                    Objects.nonNull(emailTemplate.getBody())) {

                if (value.trim().startsWith("{")) {
                    JsonNode node = objectMapper.readTree(value);
                    if (node.has("entityType") && node.has("newData")) {
                        sendEntityChangeEmail(emailTemplate, node);
                        return;
                    }
                }

                String subjectTemplate = emailTemplate.getSubject();
                String bodyTemplate = emailTemplate.getBody();
                String subject = "";
                String body = "";
                String recipientList;
                EmailMessagePayload payload ;

                if (value.trim().startsWith("{")) {
                    payload = objectMapper.readValue(value, EmailMessagePayload.class);
                } else {
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
                // Existing barcode / IBEX templates remain plain text.
                sendEmailMessage(recipientList, body, subject, false);
            } else {
                log.info("sendEmail :: Subject and Body for the key {} not found. ", key);
            }
        } catch (JsonProcessingException e) {
            log.error("sendEmail :: JsonProcessingException while sending the email ", e);
        }
    }

    private void sendEntityChangeEmail(EmailTemplate emailTemplate, JsonNode node) {
        String entityType = node.path("entityType").asText();
        String changesHtml = buildChangeSummary(node.path("oldData"), node.path("newData"));
        String subject = emailTemplate.getSubject().replace("${entityType}", entityType);
        String body = emailTemplate.getBody()
                .replace("${entityType}", entityType)
                .replace("${changes}", changesHtml);
        // Entity-change template is HTML (table-based), so send as HTML.
        sendEmailMessage(emailConfig.getTo(), body, subject, true);
    }

    /**
     * Builds an HTML table body (rows only) comparing old vs new values.
     * The surrounding <table>/<thead> markup lives in the template itself;
     * this method only produces the <tbody> rows for the "${changes}" placeholder.
     */
    private String buildChangeSummary(JsonNode oldData, JsonNode newData) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> fieldNames = newData.fieldNames();
        int rowIndex = 0;
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            JsonNode oldVal = oldData.path(field);
            JsonNode newVal = newData.path(field);
            if (!Objects.equals(oldVal, newVal)) {
                String rowBg = (rowIndex % 2 == 0) ? ROW_BG_EVEN : ROW_BG_ODD;
                sb.append("<tr style=\"background-color:").append(rowBg).append(";\">")
                        .append("<td style=\"padding:10px 14px;border:1px solid #e2e5e9;font-family:Arial,Helvetica,sans-serif;font-size:13px;color:#333333;font-weight:bold;\">")
                        .append(escapeHtml(field))
                        .append("</td>")
                        .append("<td style=\"padding:10px 14px;border:1px solid #e2e5e9;font-family:Arial,Helvetica,sans-serif;font-size:13px;color:#c0392b;\">")
                        .append(escapeHtml(oldVal.asText("-")))
                        .append("</td>")
                        .append("<td style=\"padding:10px 14px;border:1px solid #e2e5e9;font-family:Arial,Helvetica,sans-serif;font-size:13px;color:#1e7e34;font-weight:bold;\">")
                        .append(escapeHtml(newVal.asText("-")))
                        .append("</td>")
                        .append("</tr>");
                rowIndex++;
            }
        }
        if (rowIndex == 0) {
            return "<tr><td colspan=\"3\" style=\"padding:14px;text-align:center;font-family:Arial,Helvetica,sans-serif;font-size:13px;color:#777777;border:1px solid #e2e5e9;\">No changes detected.</td></tr>";
        }
        return sb.toString();
    }

    /**
     * Minimal HTML escaping to keep field/old/new values from breaking the
     * table markup or enabling HTML/script injection in the email body.
     */
    private String escapeHtml(String input) {
        if (input == null) {
            return "-";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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

    private void sendEmailMessage(String recipientList, String body, String subject, boolean isHtml) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(new InternetAddress(emailConfig.getFrom()));
            setRecipients(helper, recipientList);
            helper.setText(body, isHtml);
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