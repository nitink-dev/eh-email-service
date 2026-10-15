package com.eh.digitalpathology.email.service;

import com.eh.digitalpathology.email.model.EmailEnvelop;
import com.eh.digitalpathology.email.model.EntityChangeNotification;
import com.eh.digitalpathology.email.model.IbexEvent;
import com.eh.digitalpathology.email.model.SlideErrorInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith( MockitoExtension.class )
class KafkaEmailListenerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private KafkaEmailListener kafkaEmailListener;

    @Test
    void emailMessageHandler_ShouldSendEmailAndAcknowledge ( ) {

        String key = "MISSING_BARCODE";
        EmailEnvelop payload = new EmailEnvelop( "ABC123", "PatientId" );

        assertDoesNotThrow( ( ) -> kafkaEmailListener.emailMessageHandler( key, payload, acknowledgment ) );

        verify( emailService ).sendEmail( key, payload );
        verify( acknowledgment ).acknowledge( );
        verifyNoMoreInteractions( acknowledgment );
    }

    @Test
    void ibexErrorHandler_ShouldSendEmailAndAcknowledge ( ) {

        String key = "IBEX_ERROR";
        SlideErrorInfo payload = new SlideErrorInfo( "ABC123", 500, "Failure" );

        assertDoesNotThrow( ( ) -> kafkaEmailListener.ibexErrorHandler( key, payload, acknowledgment ) );

        verify( emailService ).sendEmail( key, payload );
        verify( acknowledgment ).acknowledge( );
        verifyNoMoreInteractions( acknowledgment );
    }

    @Test
    void ibexEventHandler_ShouldSendEmailAndAcknowledge ( ) {

        String key = "IBEX_EVENT";
        IbexEvent event = mock( IbexEvent.class );
        assertDoesNotThrow( ( ) -> kafkaEmailListener.ibexEventHandler( key, event, acknowledgment ) );

        verify( emailService ).sendEmail( key, event );
        verify( acknowledgment ).acknowledge( );
        verifyNoMoreInteractions( acknowledgment );
    }

    @Test
    void entityHandler_ShouldSendEmailAndAcknowledge ( ) {

        String key = "ENTITY_UPDATE";

        EntityChangeNotification< Map< String, Object > > notification = new EntityChangeNotification<>( key, "DEVICE", Map.of( "name", "Scanner-A" ), Map.of( "name", "Scanner-B" ) );
        assertDoesNotThrow( ( ) -> kafkaEmailListener.entityHandler( key, notification, acknowledgment ) );

        verify( emailService ).sendEmail( key, notification );
        verify( acknowledgment ).acknowledge( );
        verifyNoMoreInteractions( acknowledgment );
    }

    @Test
    void emailMessageHandler_WhenServiceThrowsException_ShouldPropagateException ( ) {

        String key = "MISSING_BARCODE";
        EmailEnvelop payload = new EmailEnvelop( "ABC123" );

        doThrow( new RuntimeException( "Failure" ) ).when( emailService ).sendEmail( key, payload );
        RuntimeException exception = assertThrows( RuntimeException.class, ( ) -> kafkaEmailListener.emailMessageHandler( key, payload, acknowledgment ) );
        assertEquals( "Failure", exception.getMessage( ) );

        verify( emailService ).sendEmail( key, payload );
        verify( acknowledgment, never( ) ).acknowledge( );
    }

    @Test
    void ibexErrorHandler_WhenServiceThrowsException_ShouldPropagateException ( ) {

        String key = "IBEX_ERROR";
        SlideErrorInfo payload = new SlideErrorInfo( "ABC123", 500, "Failure" );

        doThrow( new RuntimeException( "Email Error" ) ).when( emailService ).sendEmail( key, payload );
        RuntimeException exception = assertThrows( RuntimeException.class, ( ) -> kafkaEmailListener.ibexErrorHandler( key, payload, acknowledgment ) );
        assertEquals( "Email Error", exception.getMessage( ) );

        verify( emailService ).sendEmail( key, payload );
        verify( acknowledgment, never( ) ).acknowledge( );
    }

    @Test
    void ibexEventHandler_WhenServiceThrowsException_ShouldPropagateException ( ) {

        String key = "IBEX_EVENT";
        IbexEvent event = mock( IbexEvent.class );

        doThrow( new RuntimeException( "Failure" ) ).when( emailService ).sendEmail( key, event );
        RuntimeException exception = assertThrows( RuntimeException.class, ( ) -> kafkaEmailListener.ibexEventHandler( key, event, acknowledgment ) );
        assertEquals( "Failure", exception.getMessage( ) );

        verify( emailService ).sendEmail( key, event );
        verify( acknowledgment, never( ) ).acknowledge( );
    }

    @Test
    void entityHandler_WhenServiceThrowsException_ShouldPropagateException ( ) {

        String key = "ENTITY_UPDATE";

        EntityChangeNotification< Map< String, Object > > notification = new EntityChangeNotification<>( key, "DEVICE", Map.of( "name", "Old" ), Map.of( "name", "New" ) );
        doThrow( new RuntimeException( "Failure" ) ).when( emailService ).sendEmail( key, notification );
        RuntimeException exception = assertThrows( RuntimeException.class, ( ) -> kafkaEmailListener.entityHandler( key, notification, acknowledgment ) );
        assertEquals( "Failure", exception.getMessage( ) );

        verify( emailService ).sendEmail( key, notification );
        verify( acknowledgment, never( ) ).acknowledge( );
    }
}