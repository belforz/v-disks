package com.v_disk.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class RedisConnectionChecker {

    private static final Logger logger = LoggerFactory.getLogger(RedisConnectionChecker.class);
    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 2000;

    private final Environment environment;

    @Autowired
    public RedisConnectionChecker(Environment environment) {
        this.environment = environment;
    }

    /**
     * Event listener that runs when the application has fully started.
     * Performs connectivity checks to Redis based on several possible sources.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        // Determine Redis URL from multiple sources and record the source
        String source = null;
        String redisUrl = null;

        // 1) Direct environment variable
        String envVal = System.getenv("REDIS_URL");
        if (envVal != null && !envVal.isBlank()) {
            redisUrl = envVal;
            source = "ENV:REDIS_URL";
        }

        // 2) System property
        if (redisUrl == null) {
            String sysProp = System.getProperty("REDIS_URL");
            if (sysProp != null && !sysProp.isBlank()) {
                redisUrl = sysProp;
                source = "SYS_PROP:REDIS_URL";
            }
        }

        // 3) Spring Environment property 'REDIS_URL' (e.g. from --spring.profiles or other property sources)
        if (redisUrl == null) {
            String springProp = environment.getProperty("REDIS_URL");
            if (springProp != null && !springProp.isBlank()) {
                redisUrl = springProp;
                source = "SPRING_PROP:REDIS_URL";
            }
        }

        // 4) Spring Redis property legacy 'spring.redis.url'
        String legacyRedis = environment.getProperty("spring.redis.url");
        if (redisUrl == null && legacyRedis != null && !legacyRedis.isBlank()) {
            redisUrl = legacyRedis;
            source = "SPRING_PROP:spring.redis.url";
        }

        // 5) Spring Data Redis property 'spring.data.redis.url' (official for Spring Data Redis)
        String dataRedis = environment.getProperty("spring.data.redis.url");
            if (dataRedis != null && !dataRedis.isBlank()) {
                if (redisUrl == null) {
                    redisUrl = dataRedis;
                    source = "SPRING_PROP:spring.data.redis.url";
                } else if (!dataRedis.equals(redisUrl)) {
                    // Avoid printing full URLs or credentials to logs. Log only that the two
                    // configuration properties differ and which source will be used.
                    logger.warn("Both 'spring.redis.url' and 'spring.data.redis.url' are set and differ; using configuration from {}", source);
                }
            }

        if (redisUrl == null || redisUrl.isBlank()) {
            logger.info("REDIS_URL not set; skipping Redis connectivity check.");
            return;
        }

        try {
            // Log only source of configuration (avoid printing URLs or credentials)
            logger.info(">> Redis configuration source: {}", source);
            
            URI u = new URI(redisUrl);
            String host = u.getHost();
            int port = (u.getPort() > 0 ? u.getPort() : 6379);
            String userInfo = u.getUserInfo();
            String scheme = u.getScheme();

            // Identify if it's Upstash or another Redis service
            boolean isUpstash = host != null && host.contains("upstash");
            if (isUpstash) {
                logger.info(">> Redis provider detected: Upstash");
            } else if ("localhost".equals(host) || "127.0.0.1".equals(host)) {
                logger.info(">> Redis provider detected: Local");
            } else {
                logger.info(">> Redis provider: Unknown/Custom");
            }

            // Determine whether to use TLS
            boolean useTls = false;
            // explicit scheme rediss://
            if (scheme != null && scheme.equalsIgnoreCase("rediss")) {
                useTls = true;
            }
            // heuristic: Upstash endpoints require TLS
            if (isUpstash) {
                useTls = true;
            }
            // allow override via environment property REDIS_TLS
            String tlsEnv = environment.getProperty("REDIS_TLS");
            if (tlsEnv != null) {
                useTls = Boolean.parseBoolean(tlsEnv);
            }

            if (useTls) {
                logger.info(">> Redis TLS enabled for connection (scheme={} | upstash={})", scheme, isUpstash);
            } else {
                logger.info(">> Redis TLS not enabled for connection (scheme={} | upstash={})", scheme, isUpstash);
            }

            // DNS resolution
            checkDnsResolution(host);
            
            // TCP connection test with timeout (and TLS if required)
            checkTcpConnection(host, port, userInfo, useTls);

        } catch (URISyntaxException ex) {
            logger.error("Invalid Redis URL format: {}", ex.getMessage());
            logger.debug("Redis URI parsing exception:", ex);
        } catch (Exception ex) {
            logger.error("Exception while checking Redis connectivity: {}", ex.toString());
            logger.debug("Redis check exception:", ex);
        }
    }
    
    /**
     * Checks DNS resolution for the Redis host.
     */
    private void checkDnsResolution(String host) {
        try {
                // DNS resolution succeeded (not printing resolved addresses for privacy)
                InetAddress.getAllByName(host);
                logger.info(">> Redis DNS resolution OK");
        } catch (IOException e) {
            logger.error(">> Redis DNS resolution failed");
        }
    }
    
    /**
     * Checks TCP connection to Redis host:port and attempts a PING.
     */
    private void checkTcpConnection(String host, int port, String userInfo, boolean useTls) {
        try {
            long start = System.currentTimeMillis();
            Socket socket;
            if (useTls) {
                // Create SSL socket and perform TLS handshake
                SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket sslSocket = (SSLSocket) factory.createSocket();
                sslSocket.connect(new InetSocketAddress(host, port), CONNECTION_TIMEOUT_MS);
                sslSocket.startHandshake();
                sslSocket.setSoTimeout(READ_TIMEOUT_MS);
                socket = sslSocket;
            } else {
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), CONNECTION_TIMEOUT_MS);
                socket.setSoTimeout(READ_TIMEOUT_MS);
            }

            long connTime = System.currentTimeMillis() - start;
            logger.info(">> Redis TCP connection OK ({} ms)", connTime);
            
            // Try a simple PING if we have a connection
            if (tryRedisPing(socket, userInfo)) {
                logger.info(">> Redis PING successful!");
            }
            socket.close();
            } catch (IOException e) {
            logger.error(">> Redis TCP connection FAILED");
            logger.debug("Redis connection exception:", e);
        }
    }
    
    /**
     * Attempts a Redis PING command using RESP protocol.
     *
     * @param socket The connected socket
     * @param userInfo Authentication info from the Redis URL (if present)
     * @return true if PING was successful
     */
    private boolean tryRedisPing(Socket socket, String userInfo) {
        try {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Handle authentication if credentials are provided
            if (userInfo != null && !userInfo.isEmpty()) {
                String user;
                String password;
                if (userInfo.contains(":")) {
                    String[] parts = userInfo.split(":", 2);
                    user = parts[0];
                    password = parts[1];
                } else {
                    // only password provided
                    user = null;
                    password = userInfo;
                }

                // Build AUTH command in RESP
                if (user != null) {
                    writeRespCommand(out, new String[] {"AUTH", user, password});
                } else {
                    writeRespCommand(out, new String[] {"AUTH", password});
                }
                out.flush();

                // Read auth response
                byte[] authResp = new byte[1024];
                int authBytes = in.read(authResp);
                if (authBytes <= 0) {
                    logger.error(">> Redis AUTH failed: no response from server");
                    return false;
                }
                String authResult = new String(authResp, 0, authBytes, StandardCharsets.UTF_8);
                if (!authResult.startsWith("+OK") && !authResult.startsWith("+PONG")) {
                    // Don't log the server response body to avoid leaking sensitive info
                    logger.error(">> Redis AUTH failed");
                    return false;
                }
                logger.info(">> Redis AUTH successful");
            }

            // PING command (RESP)
            writeRespCommand(out, new String[] {"PING"});
            out.flush();

            byte[] resp = new byte[1024];
            int bytes = in.read(resp);
            if (bytes <= 0) {
                logger.error(">> Redis PING failed: no response from server");
                return false;
            }
            String result = new String(resp, 0, bytes, StandardCharsets.UTF_8);

            if (result.startsWith("+PONG") || result.contains("PONG")) {
                return true;
            } else {
                // Avoid logging full reply
                logger.error(">> Redis PING failed");
                return false;
            }
        } catch (IOException e) {
            logger.error(">> Redis PING failed: {}", e.getMessage());
            logger.debug("Redis PING exception:", e);
            return false;
        }
    }

    /**
     * Writes a Redis command in RESP format to the output stream.
     */
    private void writeRespCommand(OutputStream out, String[] parts) throws IOException {
        // *<N>\r\n$<len>\r\n<part>\r\n...
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(parts.length).append("\r\n");
        for (String p : parts) {
            byte[] bytes = p.getBytes(StandardCharsets.UTF_8);
            sb.append("$").append(bytes.length).append("\r\n");
            sb.append(p).append("\r\n");
        }
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
    }
    
    // maskCredentials removed to avoid accidental logging of sensitive data
}
