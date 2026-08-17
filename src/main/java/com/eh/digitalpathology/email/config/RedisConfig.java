package com.eh.digitalpathology.email.config;

import io.lettuce.core.resource.ClientResources;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger( RedisConfig.class.getName() );
    private final RedisSentinelProps sentinelProps;

    @Value( "${app.redis.mode:standalone}" )
    private String redisMode;
    @Value( "${spring.data.redis.password:}" )
    private String redisPassword;

    public RedisConfig ( RedisSentinelProps sentinelProps ) {
        this.sentinelProps = sentinelProps;
    }
    /* =========================================================
     * Redis Sentinel (QA / PROD)
     * ========================================================= */
    @Bean( name = "customRedisConnectionFactory" )
    @Primary
    @ConditionalOnProperty( name = "app.redis.mode", havingValue = "sentinel" )
    public RedisConnectionFactory sentinelRedisConnectionFactory (ObjectProvider<LettuceClientConfigurationBuilderCustomizer> customizers, ObjectProvider<ClientResources> clientResources ) {
        RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration( ).master( sentinelProps.getMaster( ) );
        sentinelProps.getNodes( ).forEach( node -> {
            String[] parts = node.split( ":" );
            if ( parts.length < 2 ) {
                throw new IllegalArgumentException( "Invalid Redis sentinel node format: " + node );
            }
            try {
                sentinelConfig.sentinel( parts[ 0 ], Integer.parseInt( parts[ 1 ] ) );
            } catch ( NumberFormatException e ) {
                throw new IllegalArgumentException( "Invalid Redis sentinel node port in: " + node, e );
            }
        } );

        if ( !redisPassword.isBlank( ) ) {
            sentinelConfig.setPassword( RedisPassword.of( redisPassword ) );
        }
        return new LettuceConnectionFactory( sentinelConfig, buildLettuceClientConfig( customizers, clientResources ) );
    }
    /* =========================================================
     * Redis Standalone (DEV)
     * ========================================================= */
    @Bean( name = "customRedisConnectionFactory" )
    @Primary
    @ConditionalOnProperty( name = "app.redis.mode", havingValue = "standalone", matchIfMissing = true )
    public RedisConnectionFactory standaloneRedisConnectionFactory ( ObjectProvider< LettuceClientConfigurationBuilderCustomizer > customizers, ObjectProvider< ClientResources > clientResources, @Value( "${spring.data.redis.host}" ) String host, @Value( "${spring.data.redis.port}" ) int port ) {
        log.info( "Configuring Redis in standalone mode with host={} and port={}", host, port );
        RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration( host, port );
        if ( !redisPassword.isBlank( ) ) {
            standaloneConfig.setPassword( RedisPassword.of( redisPassword ) );
        }
        return new LettuceConnectionFactory( standaloneConfig, buildLettuceClientConfig( customizers, clientResources ) );
    }
    /* =========================================================
     * Shared Lettuce Client Configuration
     * ========================================================= */
    private LettuceClientConfiguration buildLettuceClientConfig (ObjectProvider< LettuceClientConfigurationBuilderCustomizer > customizers, ObjectProvider< ClientResources > clientResources ) {

        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder( );
        clientResources.ifAvailable( builder::clientResources );
        customizers.orderedStream( ).forEach( c -> c.customize( builder ) );
        return builder.build( );
    }
    /* =========================================================
     * Fail‑fast validation
     * ========================================================= */
    @PostConstruct
    public void validateRedisConfig ( ) {
        if ( "sentinel".equalsIgnoreCase( redisMode ) && sentinelProps.getNodes( ).isEmpty( ) ) {
            throw new IllegalStateException( "Redis mode is 'sentinel' but no sentinel nodes are configured" );
        }
        if ( "standalone".equalsIgnoreCase( redisMode ) ) {
            log.info( "Redis is configured in standalone mode" );
        } else if ( "sentinel".equalsIgnoreCase( redisMode ) ) {
            log.info( "Redis is configured in sentinel mode with master '{}' and sentinels: {}", sentinelProps.getMaster(), sentinelProps.getNodes() );
        } else {
            log.warn( "Unknown Redis mode '{}', defaulting to standalone", redisMode );
        }
    }

    @Bean
    public RedisTemplate< String, Object > redisTemplate (@Qualifier( "customRedisConnectionFactory" ) RedisConnectionFactory factory ) {
        RedisTemplate< String, Object > template = new RedisTemplate<>( );
        template.setConnectionFactory( factory );
        var stringSerializer = new StringRedisSerializer( );
        var jsonSerializer = new GenericJackson2JsonRedisSerializer( );
        template.setKeySerializer( stringSerializer );
        template.setValueSerializer( jsonSerializer );
        template.setHashKeySerializer( stringSerializer );
        template.setHashValueSerializer( jsonSerializer );
        template.afterPropertiesSet( );
        return template;
    }
}

