package com.eh.digitalpathology.email.service;

import com.eh.digitalpathology.email.config.EmailConfig;
import com.eh.digitalpathology.email.config.EmailTemplateConfig;
import com.eh.digitalpathology.email.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger( EmailService.class );

    private static final String BARCODE_LITERAL = "${barcode}";
    private static final Set< String > EXCLUDED_FIELDS = Set.of( "id", "deviceId" );
    private final ObjectMapper objectMapper;
    private final JavaMailSender mailSender;
    private final EmailConfig emailConfig;
    private final EmailTemplateConfig emailTemplateConfig;

    public EmailService ( ObjectMapper objectMapper, JavaMailSender mailSender, EmailConfig emailConfig, EmailTemplateConfig emailTemplateConfig ) {
        this.objectMapper = objectMapper;
        this.mailSender = mailSender;
        this.emailConfig = emailConfig;
        this.emailTemplateConfig = emailTemplateConfig;
    }

    public EmailTemplate fetchEmailTemplate ( String key ) {
        return emailTemplateConfig.getTemplate( key );
    }

    public void sendEmail ( String key, EmailEnvelop payload ) {
        EmailTemplate template = fetchEmailTemplate( key );

        if ( template == null ) {
            log.error( "sendEmail :: email envelop :: Template not found for key = {}", key );
            return;
        }
        boolean highImportance = payload.missingValue( ) != null && !payload.missingValue( ).isBlank( );
        String subject = populateEmailBody( template.getSubject( ), payload );
        String body = populateEmailBody( template.getBody( ), payload );
        sendEmailMessage( emailConfig.getTo( ), body, subject, false, highImportance );
    }

    public void sendEmail ( String key, SlideErrorInfo payload ) {
        EmailTemplate template = fetchEmailTemplate( key );

        if ( template == null ) {
            log.warn( "sendEmail :: slide error info :: Template not found for key = {}", key );
            return;
        }
        String subject = populateEmailBody( template.getSubject( ), payload );
        String body = populateEmailBody( template.getBody( ), payload );
        sendEmailMessage( emailConfig.getIbexTo( ), body, subject, false, false );
    }

    public void sendEmail ( String key, IbexEvent payload ) {
        EmailTemplate template = fetchEmailTemplate( key );

        if ( template == null ) {
            log.warn( "sendEmail :: ibex event :: Template not found for key = {}", key );
            return;
        }
        String subject = populateEmailBody( template.getSubject( ), payload );
        String body = populateEmailBody( template.getBody( ), payload );
        sendEmailMessage( emailConfig.getIbexTo( ), body, subject, false, false );
    }

    public < T > void sendEmail ( String key, EntityChangeNotification< T > notification ) {
        EmailTemplate template = fetchEmailTemplate( key );

        if ( template == null ) {
            log.warn( "Template not found for key={}", key );
            return;
        }
        if ( notification.newData( ) == null ) {
            sendEntityDeleteEmail( template, notification );
        } else {
            sendEntityChangeEmail( template, notification );
        }
    }

    private < T > void sendEntityChangeEmail ( EmailTemplate template, EntityChangeNotification< T > notification ) {

        String entityType = notification.entityType( );
        String name = resolveEntityName( notification.newData( ) );
        String changes = buildChangeSummary( notification.oldData( ), notification.newData( ) );
        String subject = template.getSubject( ).replace( "${entityType}", entityType ).replace( "${name}", name );

        String body = template.getBody( ).replace( "${entityType}", entityType ).replace( "${name}", name ).replace( "${changes}", changes );
        sendEmailMessage( emailConfig.getTo( ), body, subject, true, false );
    }

    private < T > void sendEntityDeleteEmail ( EmailTemplate template, EntityChangeNotification< T > notification ) {

        String entityType = notification.entityType( );
        String deletedFields = buildFieldSummary( notification.oldData( ) );
        String subject = template.getSubject( ).replace( "${entityType}", entityType );
        String body = template.getBody( ).replace( "${entityType}", entityType ).replace( "${deletedFields}", deletedFields );

        sendEmailMessage( emailConfig.getTo( ), body, subject, true, true );
    }

    private < T > String resolveEntityName ( T data ) {
        Map< String, Object > map = objectMapper.convertValue( data, new TypeReference<>( ) {} );

        if ( map.containsKey( "name" ) && map.get( "name" ) != null ) {
            return String.valueOf( map.get( "name" ) );
        }
        if ( map.containsKey( "barcode" ) && map.get( "barcode" ) != null ) {
            return String.valueOf( map.get( "barcode" ) );
        }
        return " ";
    }

    private < T > String buildChangeSummary ( T oldData, T newData ) {

        Map< String, Object > newMap = objectMapper.convertValue( newData, new TypeReference<>( ) {} );
        if ( oldData == null ) {
            StringBuilder sb = new StringBuilder( );
            int rowCount = 0;
            for ( Map.Entry< String, Object > entry : newMap.entrySet( ) ) {
                if ( EXCLUDED_FIELDS.contains( entry.getKey( ) ) || entry.getValue( ) == null ) {
                    continue;
                }
                String rowColor = rowCount % 2 == 0 ? "#ffffff" : "#f9fafb";
                sb.append( "<tr style=\"background-color:" ).append( rowColor ).append( ";\">" ).append( "<td style=\"padding:10px 12px;font-size:13px;font-weight:600;border-bottom:1px solid #eeeeee;\">" ).append( escapeHtml( entry.getKey( ) ) ).append( "</td>" )
                        .append( "<td style=\"padding:10px 12px;color:#27ae60;border-bottom:1px solid #eeeeee;\">" ).append( escapeHtml( String.valueOf( entry.getValue( ) ) ) ).append( "</td>" ).append( "</tr>" );
                rowCount++;
            }
            return sb.toString( );
        }
        Map< String, Object > oldMap = objectMapper.convertValue( oldData, new TypeReference<>( ) {} );
        StringBuilder sb = new StringBuilder( );
        int changedCount = 0;
        for ( Map.Entry< String, Object > entry : newMap.entrySet( ) ) {
            String field = entry.getKey( );
            if ( EXCLUDED_FIELDS.contains( field ) ) {
                continue;
            }

            Object oldValue = oldMap.get( field );
            Object newValue = entry.getValue( );
            if ( !Objects.equals( oldValue, newValue ) ) {

                String rowColor = changedCount % 2 == 0 ? "#ffffff" : "#f9fafb";
                sb.append( "<tr style=\"background-color:" ).append( rowColor ).append( ";\">" ).append( "<td style=\"padding:10px 12px;" + "font-size:13px;" + "font-weight:600;" + "border-bottom:1px solid #eeeeee;\">" ).append( escapeHtml( field ) ).append( "</td>" )
                        .append( "<td style=\"padding:10px 12px;" + "color:#c0392b;" + "border-bottom:1px solid #eeeeee;\">" ).append( escapeHtml( String.valueOf( oldValue ) ) ).append( "</td>" ).append( "<td style=\"padding:10px 12px;" + "color:#27ae60;" + "border-bottom:1px solid #eeeeee;\">" )
                        .append( escapeHtml( String.valueOf( newValue ) ) ).append( "</td>" ).append( "</tr>" );
                changedCount++;
            }
        }

        if ( changedCount == 0 ) {
            sb.append( "<tr><td colspan=\"3\">No changes detected</td></tr>" );
        }
        return sb.toString( );
    }

    private < T > String buildFieldSummary ( T data ) {

        Map< String, Object > map = objectMapper.convertValue( data, new TypeReference<>( ) {} );

        StringBuilder sb = new StringBuilder( );
        int rowIndex = 0;
        for ( Map.Entry< String, Object > entry : map.entrySet( ) ) {
            if ( EXCLUDED_FIELDS.contains( entry.getKey( ) ) || entry.getValue( ) == null ) {
                continue;
            }
            String rowColor = rowIndex % 2 == 0 ? "#ffffff" : "#f9fafb";
            sb.append( "<tr style=\"background-color:" ).append( rowColor ).append( ";\">" ).append( "<td style=\"padding:10px 12px;" + "font-size:13px;" + "font-weight:600;" + "border-bottom:1px solid #eeeeee;\">" ).append( escapeHtml( entry.getKey( ) ) ).append( "</td>" )
                    .append( "<td style=\"padding:10px 12px;" + "font-size:13px;" + "border-bottom:1px solid #eeeeee;\">" ).append( escapeHtml( String.valueOf( entry.getValue( ) ) ) ).append( "</td>" ).append( "</tr>" );
            rowIndex++;
        }

        if ( rowIndex == 0 ) {
            sb.append( "<tr><td colspan=\"2\">No details available</td></tr>" );
        }
        return sb.toString( );
    }

    private String populateEmailBody ( String template, EmailEnvelop payload ) {

        String body = template;
        if ( payload.barcode( ) != null ) {
            body = body.replace( BARCODE_LITERAL, payload.barcode( ) );
        }
        if ( payload.missingValue( ) != null ) {
            body = body.replace( "${missingValue}", payload.missingValue( ) );
        }
        return body;
    }

    private String populateEmailBody ( String template, SlideErrorInfo payload ) {
        return template.replace( "${barcode}", Objects.toString( payload.barcode( ), "" ) ).replace( "${errorCode}", String.valueOf( payload.errorCode( ) ) ).replace( "${errorMessage}", Objects.toString( payload.errorMsg( ), "" ) );
    }

    private String populateEmailBody ( String template, IbexEvent payload ) {
        return template.replace( "${barcode}", Objects.toString( payload.getEventType( ), "" ) );
    }

    private void sendEmailMessage ( String recipientList, String body, String subject, boolean html, boolean highImportance ) {
        try {
            MimeMessage message = mailSender.createMimeMessage( );
            MimeMessageHelper helper = new MimeMessageHelper( message, true );
            helper.setFrom( new InternetAddress( emailConfig.getFrom( ) ) );
            setRecipients( helper, recipientList );
            if ( highImportance ) {
                message.setHeader( "X-Priority", "1" );
                message.setHeader( "Priority", "urgent" );
                message.setHeader( "Importance", "high" );
                subject = "[HIGH PRIORITY] " + subject;
            }
            helper.setSubject( subject );
            helper.setText( body, html );
            mailSender.send( message );
            log.info( "sendEmailMessage :: Email sent successfully with subject :: {}", subject );

        } catch ( Exception e ) {
            log.error( "sendEmailMessage :: Error sending email", e );
        }
    }

    private void setRecipients ( MimeMessageHelper helper, String recipients ) throws MessagingException {
        String[] values = recipients.split( "," );
        InternetAddress[] addresses = new InternetAddress[ values.length ];

        for ( int i = 0; i < values.length; i++ ) {
            addresses[ i ] = new InternetAddress( values[ i ].trim( ) );
        }
        helper.setTo( addresses );
    }

    private String escapeHtml ( String input ) {
        if ( input == null ) {
            return " ";
        }
        return input.replace( "&", "&amp;" ).replace( "<", "&lt;" ).replace( ">", "&gt;" ).replace( "\"", "&quot;" );
    }
}