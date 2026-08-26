package com.eh.digitalpathology.email.service;

import com.eh.digitalpathology.email.config.EmailConfig;
import com.eh.digitalpathology.email.config.EmailTemplateConfig;
import com.eh.digitalpathology.email.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith( MockitoExtension.class )
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailConfig emailConfig;

    @Mock
    private EmailTemplateConfig emailTemplateConfig;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp ( ) {

        lenient().when( mailSender.createMimeMessage( ) ).thenReturn( mimeMessage );
        lenient().when( emailConfig.getFrom( ) ).thenReturn( "sender@test.com" );
        lenient().when( emailConfig.getTo( ) ).thenReturn( "user@test.com" );
        lenient().when( emailConfig.getIbexTo( ) ).thenReturn( "ibex@test.com" );
    }

    @Test
    void fetchEmailTemplate_ShouldReturnTemplate ( ) {

        EmailTemplate template = new EmailTemplate( );
        template.setSubject( "subject" );

        when( emailTemplateConfig.getTemplate( "TEST" ) ).thenReturn( template );
        EmailTemplate result = emailService.fetchEmailTemplate( "TEST" );

        assertNotNull( result );
        assertEquals( "subject", result.getSubject( ) );
    }

    @Test
    void sendEmail_WithEmailEnvelope_ShouldSendEmail ( ) {

        EmailTemplate template = new EmailTemplate( );
        template.setSubject( "Barcode ${barcode}" );
        template.setBody( "Slide ${barcode}" );

        when( emailTemplateConfig.getTemplate( "BARCODE" ) ).thenReturn( template );
        EmailEnvelop payload = new EmailEnvelop( "ABC123", null );
        assertDoesNotThrow( ( ) -> emailService.sendEmail( "BARCODE", payload ) );

        verify( mailSender ).createMimeMessage( );
        verify( mailSender ).send( any( MimeMessage.class ) );
    }

    @Test
    void sendEmail_WithMissingTemplate_ShouldNotSendEmail ( ) {

        when( emailTemplateConfig.getTemplate( "BARCODE" ) ).thenReturn( null );
        EmailEnvelop payload = new EmailEnvelop( "ABC123", null );
        emailService.sendEmail( "BARCODE", payload );

        verify( mailSender, never( ) ).send( any( MimeMessage.class ) );
    }

    @Test
    void sendEmail_WithSlideErrorInfo_ShouldSendEmail ( ) {

        EmailTemplate template = new EmailTemplate( );
        template.setSubject( "${errorCode}" );
        template.setBody( "${errorMessage}" );

        when( emailTemplateConfig.getTemplate( "ERROR" ) ).thenReturn( template );
        SlideErrorInfo info = new SlideErrorInfo( "ABC123", 500, "Failure" );

        emailService.sendEmail( "ERROR", info );
        verify( mailSender ).send( any( MimeMessage.class ) );
    }

    @Test
    void sendEmail_WithIbexEvent_ShouldSendEmail ( ) {

        EmailTemplate template = new EmailTemplate( );
        template.setSubject( "${barcode}" );
        template.setBody( "${barcode}" );

        when( emailTemplateConfig.getTemplate( "IBEX" ) ).thenReturn( template );

        IbexEvent event = mock( IbexEvent.class );
        when( event.getEventType( ) ).thenReturn( "SCAN_COMPLETE" );

        emailService.sendEmail( "IBEX", event );
        verify( mailSender ).send( any( MimeMessage.class ) );
    }

    @Test
    void sendEmail_WithEntityChange_ShouldSendEmail ( ) {

        EmailTemplate template = new EmailTemplate( );
        template.setSubject( "${entityType} ${name}" );
        template.setBody( "${entityType} ${changes}" );

        when( emailTemplateConfig.getTemplate( "ENTITY" ) ).thenReturn( template );

        Map< String, Object > oldMap = new HashMap<>( );
        oldMap.put( "name", "Scanner-A" );
        oldMap.put( "location", "Room1" );

        Map< String, Object > newMap = new HashMap<>( );
        newMap.put( "name", "Scanner-A" );
        newMap.put( "location", "Room2" );

        when( objectMapper.convertValue( eq( oldMap ), any( TypeReference.class ) ) ).thenReturn( oldMap );
        when( objectMapper.convertValue( eq( newMap ), any( TypeReference.class ) ) ).thenReturn( newMap );

        EntityChangeNotification< Map< String, Object > > notification = new EntityChangeNotification<>( "ENTITY", "DEVICE", oldMap, newMap );
        emailService.sendEmail( "ENTITY", notification );

        verify( mailSender ).send( any( MimeMessage.class ) );
    }

    @Test
    void sendEmail_WithEntityDelete_ShouldSendEmail ( ) {

        EmailTemplate template = new EmailTemplate( );
        template.setSubject( "${entityType}" );
        template.setBody( "${entityType}" );

        when( emailTemplateConfig.getTemplate( "ENTITY" ) ).thenReturn( template );

        Map< String, Object > oldData = Map.of( "name", "Scanner-A" );
        when( objectMapper.convertValue( eq( oldData ), any( TypeReference.class ) ) ).thenReturn( oldData );

        EntityChangeNotification< Map< String, Object > > notification = new EntityChangeNotification<>( "ENTITY", "DEVICE", oldData, null );

        emailService.sendEmail( "ENTITY", notification );
        verify( mailSender ).send( any( MimeMessage.class ) );
    }

    @Test
    void sendEmail_EntityTemplateMissing_ShouldNotSendMail ( ) {

        when( emailTemplateConfig.getTemplate( "ENTITY" ) ).thenReturn( null );
        EntityChangeNotification< Map< String, Object > > notification = new EntityChangeNotification<>( "ENTITY", "DEVICE", Map.of( ), Map.of( ) );

        emailService.sendEmail( "ENTITY", notification );
        verify( mailSender, never( ) ).send( any( MimeMessage.class ) );
    }

    @Test
    void sendEmail_WhenMailSenderThrows_ShouldNotThrow ( ) {

        EmailTemplate template = new EmailTemplate( );
        template.setSubject( "Subject" );
        template.setBody( "Body" );

        when( emailTemplateConfig.getTemplate( "TEST" ) ).thenReturn( template );
        doThrow( new MailSendException( "failure" ) ).when( mailSender ).send( any( MimeMessage.class ) );

        EmailEnvelop payload = new EmailEnvelop( "123" );
        assertDoesNotThrow( ( ) -> emailService.sendEmail( "TEST", payload ) );
    }
}
