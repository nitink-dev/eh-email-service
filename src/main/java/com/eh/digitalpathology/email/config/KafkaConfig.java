package com.eh.digitalpathology.email.config;

import jakarta.annotation.PostConstruct;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

@Configuration
public class KafkaConfig {
    private static final Logger log = LoggerFactory.getLogger( KafkaConfig.class );

    private final KafkaProperties kafkaProperties;

    public KafkaConfig ( KafkaProperties kafkaProperties ) {
        this.kafkaProperties = kafkaProperties;
    }

    @PostConstruct
    public void logKafkaConfig ( ) {
        log.info( "Kafka Bootstrap Server: {}", kafkaProperties.getBootstrapServers() );
    }


    @Bean
    public ProducerFactory< String, String > producerFactory ( ) {
        Map< String, Object > props = kafkaProperties.buildProducerProperties( );
        return new DefaultKafkaProducerFactory<>( props );
    }

    @Bean( name = "customKafkaTemplate" )
    public KafkaTemplate< String, String > customKafkaTemplate ( ) {
        return new KafkaTemplate<>( producerFactory( ) );
    }

    /* ===========================
       CONSUMER
       =========================== */

    @Bean
    public ConsumerFactory< String, String > consumerFactory ( ) {
        Map< String, Object > props = kafkaProperties.buildConsumerProperties( );
        return new DefaultKafkaConsumerFactory<>( props );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory< String, String > kafkaListenerContainerFactory ( ) {
        ConcurrentKafkaListenerContainerFactory< String, String > factory = new ConcurrentKafkaListenerContainerFactory<>( );
        factory.setConsumerFactory( consumerFactory( ) );
        factory.getContainerProperties( ).setAckMode( ContainerProperties.AckMode.MANUAL_IMMEDIATE );
        factory.setCommonErrorHandler( errorHandler( ) );
        return factory;
    }

    @Bean
    public CommonErrorHandler errorHandler ( ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer( customKafkaTemplate( ), ( record, ex ) -> new TopicPartition( "dead-letter-topic", record.partition( ) ) );
        return new DefaultErrorHandler( recoverer, new FixedBackOff( 0L, 3 )   // retry 3 times
        );
    }

}
