package com.eh.digitalpathology.email.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.Mockito.*;

@ExtendWith( MockitoExtension.class )
class KafkaEmailListenerTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private KafkaEmailListener kafkaEmailListener;

    @Mock
    private Acknowledgment acknowledgment;

    @BeforeEach
    void setUp() {
        lenient().doNothing().when( acknowledgment).acknowledge();
    }

    @Test
    void testListen_shouldInvokeEmailService() {
        // Arrange
        String key = "DEFAULT";
        String value = "ABC123";
        ConsumerRecord<String, String> records = new ConsumerRecord<>("test-topic", 0, 0L, key, value);

        // Act
        kafkaEmailListener.listen(records, acknowledgment);

        // Assert
        verify(emailService).sendEmail(key, value);
    }

    @Test
    void testListen_shouldHandleExceptionGracefully() {
        // Arrange
        String key = "DEFAULT";
        String value = "ABC123";
        ConsumerRecord<String, String> records = new ConsumerRecord<>("test-topic", 0, 0L, key, value);

        doThrow(new RuntimeException("Simulated failure")).when(emailService).sendEmail(key, value);

        // Act
        kafkaEmailListener.listen(records, acknowledgment);

        // Assert
        verify(emailService).sendEmail(key, value);
        // No exception should propagate
    }
}