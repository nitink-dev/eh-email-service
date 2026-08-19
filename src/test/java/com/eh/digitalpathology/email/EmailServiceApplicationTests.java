package com.eh.digitalpathology.email;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Ensures the Spring application context loads correctly.
 * The 'main' test also directly invokes the main method to ensure
 * it can be covered by the code coverage reports.
 */
@SpringBootTest(properties = "spring.cloud.config.enabled=false")
class EmailServiceApplicationTests {

    @Test
    void contextLoads() {
        // This test ensures the application context initializes correctly.
    }
}
