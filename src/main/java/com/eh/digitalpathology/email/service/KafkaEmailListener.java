package com.eh.digitalpathology.email.service;

import com.eh.digitalpathology.email.model.EmailEnvelop;
import com.eh.digitalpathology.email.model.EntityChangeNotification;
import com.eh.digitalpathology.email.model.IbexEvent;
import com.eh.digitalpathology.email.model.SlideErrorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Service
@RefreshScope
@KafkaListener( topics = "${kafka.topic.email}", groupId = "email-consumer-group", containerFactory = "kafkaListenerContainerFactory" )
public class KafkaEmailListener {
    private final EmailService emailService;
    private static final Logger logger = LoggerFactory.getLogger( KafkaEmailListener.class.getName( ) );

    public KafkaEmailListener ( EmailService emailService) {
        this.emailService = emailService;
    }

    @KafkaHandler
    public void emailMessageHandler ( @Header( KafkaHeaders.RECEIVED_KEY ) String key, EmailEnvelop emailEnvelop, Acknowledgment ack ) {
            emailService.sendEmail( key, emailEnvelop );
            ack.acknowledge( );
    }

    @KafkaHandler
    public void ibexErrorHandler ( @Header( KafkaHeaders.RECEIVED_KEY ) String key, SlideErrorInfo slideErrorInfo, Acknowledgment ack ) {
        emailService.sendEmail( key, slideErrorInfo );
        ack.acknowledge( );
    }

    @KafkaHandler
    public void ibexEventHandler ( @Header( KafkaHeaders.RECEIVED_KEY ) String key, IbexEvent event, Acknowledgment ack ) {
        emailService.sendEmail( key, event );
        ack.acknowledge( );
    }

    @KafkaHandler
    public void entityHandler ( @Header( KafkaHeaders.RECEIVED_KEY ) String key, EntityChangeNotification<?> entity, Acknowledgment ack ) {
        emailService.sendEmail( key, entity );
        ack.acknowledge( );
    }

}
