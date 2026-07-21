package com.eh.digitalpathology.email.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
@RefreshScope
public class KafkaEmailListener {
    private final EmailService emailService;
    private static final Logger logger = LoggerFactory.getLogger(KafkaEmailListener.class.getName());

    public KafkaEmailListener(EmailService emailService) {
        this.emailService = emailService;
    }


    @KafkaListener(topics = "${kafka.topic.email}",  groupId = "email-consumer-group", containerFactory = "kafkaListenerContainerFactory" )
    public void listen(ConsumerRecord<String, String> consumerRecord, Acknowledgment ack ) {
        logger.info("listen :: consumer record key :: {}", consumerRecord.key());
        logger.info("listen :: consumer record value :: {}", consumerRecord.value());
        try {
            emailService.sendEmail(consumerRecord.key(), consumerRecord.value());
            ack.acknowledge();
        } catch (Exception e) {
            logger.error("listen :: Error while sending email ", e);
        }
    }
}
