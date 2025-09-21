package com.v_disk.utils;

import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RedisConnectionChecker {

    private static final Logger logger = LoggerFactory.getLogger(RedisConnectionChecker.class);

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            String redisUrl = System.getenv("REDIS_URL");
            if (redisUrl == null || redisUrl.isBlank()) {
                logger.info("REDIS_URL not set; skipping Redis connectivity check.");
                return;
            }

            URI u = new URI(redisUrl);
            String host = u.getHost();
            int port = (u.getPort() > 0 ? u.getPort() : 6379);

            logger.info(">> Redis DNS: {} -> {}", host, InetAddress.getByName(host));
            try (Socket s = new Socket(host, port)) {
                logger.info(">> Redis TCP OK {}:{}", host, port);
            } catch (Exception e) {
                logger.error(">> Redis TCP FAILED {}:{} - {}", host, port, e.toString());
                logger.debug("Redis connection exception:", e);
            }

        } catch (Exception ex) {
            logger.error("Exception while checking Redis connectivity: {}", ex.toString());
            logger.debug("Redis check exception:", ex);
        }
    }
}
