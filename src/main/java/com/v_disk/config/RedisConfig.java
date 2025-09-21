package com.v_disk.config;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    private static final Logger logger = LoggerFactory.getLogger(RedisConfig.class);

    private final Environment env;

    public RedisConfig(Environment env) {
        this.env = env;
    }

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // Prefer explicit spring.data.redis.url or REDIS_URL if present
        String redisUrl = env.getProperty("spring.data.redis.url");
        String source = "spring.data.redis.url";

        if (redisUrl == null || redisUrl.isBlank()) {
            redisUrl = System.getenv("REDIS_URL");
            if (redisUrl != null && !redisUrl.isBlank()) {
                source = "ENV:REDIS_URL";
            }
        }

        if (redisUrl != null && !redisUrl.isBlank()) {
            try {
                URI uri = new URI(redisUrl);
                String scheme = uri.getScheme();
                String host = uri.getHost();
                int port = (uri.getPort() > 0 ? uri.getPort() : 6379);
                String userInfo = uri.getUserInfo();

                String username = null;
                String password = null;
                if (userInfo != null && !userInfo.isBlank()) {
                    if (userInfo.contains(":")) {
                        String[] parts = userInfo.split(":", 2);
                        username = parts[0];
                        password = parts[1];
                    } else {
                        password = userInfo;
                    }
                }

                boolean useTls = "rediss".equalsIgnoreCase(scheme) || (host != null && host.contains("upstash"));
                String usedHost = host != null ? host : redisHost;
                int usedPort = port > 0 ? port : redisPort;

                RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(usedHost, usedPort);
                if (username != null) cfg.setUsername(username);
                if (password != null) cfg.setPassword(RedisPassword.of(password));

                LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                        .useSsl().build();
                if (!useTls) {
                    clientConfig = LettuceClientConfiguration.builder().build();
                }

                logger.info("Creating LettuceConnectionFactory (source={}) tls={}", source, useTls);
                return new LettuceConnectionFactory(cfg, clientConfig);
            } catch (URISyntaxException ex) {
                logger.warn("Failed to parse spring.data.redis.url ({}): {}", redisUrl, ex.getMessage());
                // fallback to legacy config below
            }
        }

        // Fallback: legacy properties spring.redis.host / port
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(redisHost, redisPort);
    logger.info("Creating LettuceConnectionFactory (fallback)");
        return new LettuceConnectionFactory(cfg);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> tpl = new RedisTemplate<>();
        tpl.setConnectionFactory(factory);
        return tpl;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
