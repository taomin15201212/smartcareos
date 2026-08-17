package com.smartcareos.device.infrastructure;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

@Component
@ConditionalOnProperty(prefix = "smartcareos.mqtt", name = "enabled", havingValue = "true")
public class MqttRiskEventSubscriber implements SmartLifecycle, HealthIndicator {
    private static final Logger log = LoggerFactory.getLogger(MqttRiskEventSubscriber.class);
    private static final String TOPIC = "smartcare/+/devices/+/risk-events";

    private final MqttRiskEventAdapter adapter;
    private final String serverUri;
    private final String clientId;
    private final String username;
    private final String password;
    private final Duration connectTimeout;
    private final String trustStorePath;
    private final String trustStorePassword;
    private final String keyStorePath;
    private final String keyStorePassword;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile MqttAsyncClient client;

    public MqttRiskEventSubscriber(MqttRiskEventAdapter adapter,
            @Value("${smartcareos.mqtt.server-uri}") String serverUri,
            @Value("${smartcareos.mqtt.client-id:smartcareos-${random.uuid}}") String clientId,
            @Value("${smartcareos.mqtt.username:}") String username,
            @Value("${smartcareos.mqtt.password:}") String password,
            @Value("${smartcareos.mqtt.connect-timeout:PT10S}") Duration connectTimeout,
            @Value("${smartcareos.mqtt.tls.trust-store:}") String trustStorePath,
            @Value("${smartcareos.mqtt.tls.trust-store-password:}") String trustStorePassword,
            @Value("${smartcareos.mqtt.tls.key-store:}") String keyStorePath,
            @Value("${smartcareos.mqtt.tls.key-store-password:}") String keyStorePassword) {
        this.adapter = adapter;
        this.serverUri = serverUri;
        this.clientId = clientId;
        this.username = username;
        this.password = password;
        this.connectTimeout = connectTimeout;
        this.trustStorePath = trustStorePath;
        this.trustStorePassword = trustStorePassword;
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        try {
            client = new MqttAsyncClient(serverUri, clientId, new MemoryPersistence());
            client.setCallback(new MqttCallbackExtended() {
                @Override public void connectComplete(boolean reconnect, String uri) {
                    try {
                        client.subscribe(TOPIC, 1).waitForCompletion(connectTimeout.toMillis());
                        log.info("MQTT risk subscriber connected uri={} reconnect={} topic={}",
                                uri, reconnect, TOPIC);
                    } catch (Exception exception) {
                        log.error("MQTT subscription failed uri={}", uri, exception);
                    }
                }
                @Override public void connectionLost(Throwable cause) {
                    log.warn("MQTT connection lost uri={}", serverUri, cause);
                }
                @Override public void messageArrived(String topic, MqttMessage message) throws Exception {
                    try {
                        adapter.accept(topic, message.getPayload());
                    } catch (Exception exception) {
                        log.error("MQTT risk event processing failed topic={} messageId={}",
                                topic, message.getId(), exception);
                        throw exception;
                    }
                }
                @Override public void deliveryComplete(IMqttDeliveryToken token) { }
            });
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(false);
            options.setConnectionTimeout(Math.toIntExact(connectTimeout.toSeconds()));
            if (!username.isBlank()) options.setUserName(username);
            if (!password.isBlank()) options.setPassword(password.toCharArray());
            if (serverUri.startsWith("ssl://")) {
                options.setSocketFactory(tlsSocketFactory());
                options.setHttpsHostnameVerificationEnabled(true);
            }
            client.connect(options).waitForCompletion(connectTimeout.toMillis());
        } catch (Exception exception) {
            running.set(false);
            closeQuietly();
            throw new IllegalStateException("could not connect MQTT risk subscriber", exception);
        }
    }

    @Override
    public void stop() {
        running.set(false);
        try {
            if (client != null && client.isConnected())
                client.disconnect().waitForCompletion(connectTimeout.toMillis());
        } catch (Exception exception) {
            log.warn("MQTT disconnect failed", exception);
        } finally { closeQuietly(); }
    }

    private void closeQuietly() {
        try { if (client != null) client.close(); }
        catch (Exception exception) { log.debug("MQTT close failed", exception); }
    }

    private SSLSocketFactory tlsSocketFactory() throws Exception {
        if (trustStorePath.isBlank() || trustStorePassword.isBlank()
                || keyStorePath.isBlank() || keyStorePassword.isBlank()) {
            throw new IllegalStateException(
                    "MQTT TLS requires trust-store, key-store and their passwords");
        }
        KeyStore trustStore = loadStore(trustStorePath, trustStorePassword);
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trustStore);

        KeyStore keyStore = loadStore(keyStorePath, keyStorePassword);
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, keyStorePassword.toCharArray());

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);
        return context.getSocketFactory();
    }

    private static KeyStore loadStore(String path, String password) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(Path.of(path))) {
            store.load(input, password.toCharArray());
        }
        return store;
    }

    @Override public boolean isRunning() { return running.get() && client != null && client.isConnected(); }
    @Override public int getPhase() { return Integer.MAX_VALUE - 100; }
    @Override public boolean isAutoStartup() { return true; }
    @Override public Health health() {
        return isRunning() ? Health.up().withDetail("serverUri", serverUri).build()
                : Health.down().withDetail("serverUri", serverUri).build();
    }
}
