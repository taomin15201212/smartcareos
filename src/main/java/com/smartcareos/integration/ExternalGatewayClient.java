package com.smartcareos.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Component
public class ExternalGatewayClient {
    private final String mode;
    private final String notificationEndpoint;
    private final String governmentEndpoint;
    private final ObjectMapper mapper;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public ExternalGatewayClient(@Value("${smartcareos.gateways.mode:sandbox}") String mode,
            @Value("${smartcareos.gateways.notification-endpoint:}") String notificationEndpoint,
            @Value("${smartcareos.gateways.government-endpoint:}") String governmentEndpoint,
            ObjectMapper mapper) {
        this.mode=mode; this.notificationEndpoint=notificationEndpoint;
        this.governmentEndpoint=governmentEndpoint; this.mapper=mapper;
    }

    public GatewayResult notify(String channel,String recipient,String summary) {
        return send(notificationEndpoint,Map.of("channel",channel,"recipient",recipient,"summary",summary));
    }

    public GatewayResult government(String contract,String mapping,String payloadHash) {
        return send(governmentEndpoint,Map.of("contractCode",contract,"mappingVersion",mapping,"payloadHash",payloadHash));
    }

    private GatewayResult send(String endpoint,Map<String,Object> payload) {
        if("sandbox".equalsIgnoreCase(mode))
            return new GatewayResult(true,"sandbox-"+UUID.randomUUID(),"accepted by local sandbox");
        if(!"http".equalsIgnoreCase(mode) || endpoint.isBlank())
            return new GatewayResult(false,null,"gateway is not configured");
        try {
            HttpRequest request=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());
            boolean accepted=response.statusCode()>=200 && response.statusCode()<300;
            return new GatewayResult(accepted,response.headers().firstValue("X-External-Reference")
                    .orElse("http-"+response.statusCode()),accepted?"accepted":"HTTP "+response.statusCode());
        } catch(Exception exception) {
            return new GatewayResult(false,null,exception.getClass().getSimpleName());
        }
    }

    public record GatewayResult(boolean accepted,String reference,String message) {}
}
