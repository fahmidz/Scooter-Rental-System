
package com.scooter.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.scooter.backend.ApiError.Failure;

@Service
public class MqttGateway {

    private static final Logger log =
            LoggerFactory.getLogger(MqttGateway.class);

    private final String url, id;
    private final TelemetryService telemetry;
    private final ObjectMapper mapper;

    private MqttClient client;

    public MqttGateway(
            @Value("${mqtt.url}") String url,
            @Value("${mqtt.client-id}") String id,
            TelemetryService telemetry,
            ObjectMapper mapper) {

        this.url = url;
        this.id = id;
        this.telemetry = telemetry;
        this.mapper = mapper;
    }

    @PostConstruct
    public void connect() {
        try {
            // Memory persistence prevents MQTT folders
            client = new MqttClient(
                    url,
                    id + "-" + UUID.randomUUID().toString().substring(0, 8),
                    new MemoryPersistence()
            );

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(5);

            client.setCallback(new MqttCallbackExtended() {

                @Override
                public void connectComplete(
                        boolean reconnect,
                        String serverURI) {

                    try {
                        client.subscribe("scooters/+/telemetry", 1);
                        log.info("Subscribed to scooter telemetry");

                    } catch (MqttException e) {
                        log.error("Subscribe failed", e);
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    log.warn(
                            "MQTT connection lost: {}",
                            cause.toString()
                    );
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }

                @Override
                public void messageArrived(
                        String topic,
                        MqttMessage message) {

                    try {
                        String[] parts = topic.split("/");

                        if (parts.length != 3) {
                            return;
                        }

                        int scooterId = Integer.parseInt(parts[1]);

                        JsonNode body = mapper.readTree(
                                message.getPayload()
                        );

                        telemetry.process(scooterId, body);

                    } catch (Exception e) {
                        log.warn(
                                "Invalid telemetry on {}: {}",
                                topic,
                                e.getMessage()
                        );
                    }
                }
            });

            client.connect(options);

        } catch (Exception e) {
            log.error(
                    "MQTT broker unavailable; start Mosquitto and restart the backend: {}",
                    e.getMessage()
            );
        }
    }

    public synchronized void command(
            int scooterId,
            String command) {

        if (!"LOCK".equals(command)
                && !"UNLOCK".equals(command)) {
            throw new IllegalArgumentException("Unsupported command");
        }

        try {
            if (client == null || !client.isConnected()) {
                throw new Failure(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "MQTT broker disconnected"
                );
            }

            MqttMessage msg = new MqttMessage(
                    command.getBytes(StandardCharsets.UTF_8)
            );

            msg.setQos(1);

            client.publish(
                    "scooters/" + scooterId + "/command",
                    msg
            );

        } catch (MqttException e) {
            throw new Failure(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MQTT command failed: " + e.getMessage()
            );
        }
    }

    @PreDestroy
    public void disconnect() {
        try {
            if (client != null) {
                if (client.isConnected()) {
                    client.disconnect();
                }

                client.close();
            }

        } catch (Exception e) {
            log.warn("MQTT shutdown error", e);
        }
    }
}
