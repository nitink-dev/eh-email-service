package com.eh.digitalpathology.email.service;

import com.eh.digitalpathology.email.model.HostInfo;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Optional;

@Component
public class HostInfoProvider {

    private static final Logger log = LoggerFactory.getLogger(HostInfoProvider.class);

    @Value("${spring.application.name:unknown-app}")
    private String applicationName;

    @Value("${server.port:8080}")
    private int port;

    private final RedisTemplate<String, Object> redisTemplate;

    public HostInfoProvider(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Runs at startup
     */
    @PostConstruct
    public void initOnStartup() {
        registerHostInfo("startup");
    }

    /**
     * Runs on /refresh or bus refresh
     */
    @EventListener(RefreshScopeRefreshedEvent.class)
    public void onRefresh() {
        registerHostInfo("config-refresh");
    }

    /**
     * Common logic
     */
    private void registerHostInfo(String trigger) {
        try {
            String key = String.format("service:host:%s", applicationName);
            HostInfo hostInfo = new HostInfo(resolveIp(), port, applicationName);
            redisTemplate.opsForValue().set(key, hostInfo);
            log.info("Stored instance info in Redis. trigger={}, key={}, value={}", trigger, key, hostInfo);
        } catch (Exception e) {
            log.error("Host initialization failed during {}", trigger, e);
        }
    }

    /**
     * Resolves the service IP
     */
    private String resolveIp() {
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            if (isLoopBackAddress(ip)) {
                log.warn("Localhost IP detected ({}), resolving real network IP", ip);
                return resolveNonLoopBackIpv4FromNetworkInterfaces().orElse(ip);
            }
            log.info("Resolved IP using hostname: {}", ip);
            return ip;
        } catch (Exception e) {
            throw new RuntimeException("Unable to resolve service IP", e);
        }
    }

    private boolean isLoopBackAddress(String ip) {
        return ip != null && ip.startsWith("127.");
    }

    private Optional<String> resolveNonLoopBackIpv4FromNetworkInterfaces() {
        try {
            return NetworkInterface.networkInterfaces()
                    .flatMap(ni -> Collections.list(ni.getInetAddresses()).stream())
                    .filter(addr -> !addr.isLoopbackAddress())
                    .filter(addr -> addr instanceof Inet4Address)
                    .map(InetAddress::getHostAddress)
                    .findFirst();

        } catch (Exception e) {
            log.warn("Failed to resolve IP from network interfaces", e);
            return Optional.empty();
        }
    }
}