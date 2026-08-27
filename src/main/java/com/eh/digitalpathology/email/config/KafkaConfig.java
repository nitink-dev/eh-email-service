package com.eh.digitalpathology.email.config;

import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.producer.ProducerConfig;
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
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
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
        log.info( "Kafka Bootstrap Server: {}", kafkaProperties.getBootstrapServers( ) );
    }


    @Bean
    public ProducerFactory< String, String > producerFactory ( ) {
        Map< String, Object > props = kafkaProperties.buildProducerProperties( );
        props.put( ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class );
        return new DefaultKafkaProducerFactory<>( props );
    }

    @Bean
    public KafkaTemplate< String, String > kafkaTemplate ( ) {
        return new KafkaTemplate<>( producerFactory( ) );
    }

    /* ===========================
       CONSUMER
       =========================== */

    @Bean
    public ConsumerFactory< String, Object > consumerFactory ( ) {

        Map< String, Object > props = kafkaProperties.buildConsumerProperties( );
        props.put( JsonDeserializer.TRUSTED_PACKAGES, "*" );
        props.put( JsonDeserializer.USE_TYPE_INFO_HEADERS, true );
        props.put( JsonDeserializer.TYPE_MAPPINGS, String.join( ",", "email-envelop:com.eh.digitalpathology.email.model.EmailEnvelop", "slide-error:com.eh.digitalpathology.email.model.SlideErrorInfo",
                "ibex-event:com.eh.digitalpathology.email.model.IbexEvent", "entity-email:com.eh.digitalpathology.email.model.EntityChangeNotification") );

        JsonDeserializer< Object > deserializer = new JsonDeserializer<>( );
        return new DefaultKafkaConsumerFactory<>( props, null, new ErrorHandlingDeserializer<>( deserializer ) );
    }

    @Bean("kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory< String, Object > kafkaListenerContainerFactory ( ) {

        ConcurrentKafkaListenerContainerFactory< String, Object > factory = new ConcurrentKafkaListenerContainerFactory<>( );
        factory.setConsumerFactory( consumerFactory( ) );
        factory.setConcurrency( kafkaProperties.getListener( ).getConcurrency( ) );

        factory.getContainerProperties( ).setAckMode( ContainerProperties.AckMode.valueOf( kafkaProperties.getListener( ).getAckMode( ).name( ) ) );
        factory.setCommonErrorHandler( errorHandler( ) );
        return factory;
    }

    @Bean
    public CommonErrorHandler errorHandler ( ) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer( kafkaTemplate( ), ( consumerRecord, ex ) -> {
            log.error( "Failed record. topic={}, key={}, exception={}", consumerRecord.topic( ), consumerRecord.key( ), ex.getMessage( ), ex );
            return new TopicPartition( consumerRecord.topic( ) + ".DLT", consumerRecord.partition( ) );
        } );
        return new DefaultErrorHandler( recoverer, new FixedBackOff( 0L, 3 ) );
    }

}
