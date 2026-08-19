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
    private static final String BARCODE_LITERAL = "${barcode}";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JavaMailSender mailSender;
    private final EmailConfig emailConfig;
    private final EmailTemplateConfig emailTemplateConfig;

    public EmailService(JavaMailSender mailSender, EmailConfig emailConfig, EmailTemplateConfig emailTemplateConfig) {
        this.mailSender = mailSender;
        this.emailConfig = emailConfig;
        this.emailTemplateConfig = emailTemplateConfig;
    }

    public void sendEmail(String key, String value) {
        EmailTemplate emailTemplate = emailTemplateConfig.getTemplate(key);

        if (emailTemplate == null) {
            return;
        }


        try {
            if (Objects.nonNull(emailTemplate.getSubject()) && Objects.nonNull(emailTemplate.getBody())) {

                if (value.trim().startsWith("{")) {
                    JsonNode node = objectMapper.readTree(value);
                    if (node.has("entityType") && node.has("newData")) {
                        if (node.path("newData").isNull()) {
                            sendEntityDeleteEmail(emailTemplate, node);
                        } else {
                            sendEntityChangeEmail(emailTemplate, node);
                        }
                        return;
                    }
                }

                String subjectTemplate = emailTemplate.getSubject();
                String bodyTemplate = emailTemplate.getBody();
                String subject = "";
                String body = "";
                String recipientList;
                EmailMessagePayload payload;

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
        String name = escapeHtml(resolveEntityName(node.path("newData")));
        String subject = emailTemplate.getSubject().replace("${entityType}", entityType).replace("${name}", name);
        String body = emailTemplate.getBody().replace("${entityType}", entityType).replace("${changes}", changesHtml).replace("${name}", name);
        sendEmailMessage(emailConfig.getTo(), body, subject, true);
    }

    private void sendEntityDeleteEmail(EmailTemplate emailTemplate, JsonNode node) {
        String entityType = node.path("entityType").asText();
        JsonNode oldData = node.path("oldData");
        String name = escapeHtml(oldData.path("name").asText("-"));
        String deviceSerialNumber = escapeHtml(oldData.path("deviceSerialNumber").asText("-"));
        String subject = emailTemplate.getSubject().replace("${entityType}", entityType);
        String body = emailTemplate.getBody().replace("${entityType}", entityType).replace("${name}", name).replace("${deviceSerialNumber}", deviceSerialNumber);
        sendEmailMessage(emailConfig.getTo(), body, subject, true);
    }

    private String resolveEntityName(JsonNode newData) {
        if (newData.has("name") && !newData.path("name").asText("").isBlank()) {
            return newData.path("name").asText(" ");
        }
        if (newData.has("barcode") && !newData.path("barcode").asText("").isBlank()) {
            return newData.path("barcode").asText("-");
        }
        return "-";
    }

    /**
     * Builds an HTML table body (tbody rows only) showing old vs new values
     * for every field that changed. The surrounding <table>/<thead> markup
     * lives in the email template itself (see ENTITY_CHANGE_DEFAULT).
     */
    private String buildChangeSummary(JsonNode oldData, JsonNode newData) {

        StringBuilder sb = new StringBuilder();
        Iterator<String> fieldNames = newData.fieldNames();
        int totalFieldsProcessed = 0;
        int changedFieldsCount = 0;

        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            totalFieldsProcessed++;
            JsonNode oldVal = oldData.path(field);
            JsonNode newVal = newData.path(field);
            if (!Objects.equals(oldVal, newVal)) {
                String rowBackground = (changedFieldsCount % 2 == 0) ? "#ffffff" : "#f9fafb";
                String oldText = escapeHtml(nodeToDisplayText(oldVal));
                String newText = escapeHtml(nodeToDisplayText(newVal));

                sb.append("<tr style=\"background-color:").append(rowBackground).append(";\">")
                        .append("<td style=\"padding:10px 12px;font-size:13px;color:#333333;border-bottom:1px solid #eeeeee;font-weight:600;\">")
                        .append(escapeHtml(field))
                        .append("</td>")
                        .append("<td style=\"padding:10px 12px;font-size:13px;color:#c0392b;border-bottom:1px solid #eeeeee;\">")
                        .append(oldText)
                        .append("</td>")
                        .append("<td style=\"padding:10px 12px;font-size:13px;color:#27ae60;border-bottom:1px solid #eeeeee;\">")
                        .append(newText)
                        .append("</td>")
                        .append("</tr>\n");

                changedFieldsCount++;
            } else {
                log.debug("No change detected for field='{}'", field);
            }
        }

        if (changedFieldsCount == 0) {
            sb.append("<tr><td colspan=\"3\" style=\"padding:12px;font-size:13px;color:#888888;text-align:center;\">")
                    .append("No field changes detected.")
                    .append("</td></tr>");
        }

        log.info("Completed buildChangeSummary. Total fields processed={}, Changed fields={}", totalFieldsProcessed, changedFieldsCount);
        log.debug("Final change summary HTML:\n{}", sb);
        return sb.toString();
    }

    /**
     * JsonNode#asText(default) only falls back to the default for a null
     * value node - object/array nodes return "" from asText(), so nested
     * fields (e.g. config sub-objects) render blank rows unless handled here.
     */
    private String nodeToDisplayText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "-";
        }
        if (node.isValueNode()) {
            return node.asText("-");
        }
        return node.toString();
    }

    /**
     * Minimal HTML escaper so field names/values coming from JSON data
     * can never break the generated table markup.
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