package com.aquaflow.backend.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.MessageChannel;

import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class MqttConfig {

    @Value("${MQTT_BROKER_URL:tcp://localhost:1883}")
    private String mqttBrokerUrl;

    private static final String CLIENT_ID = "aquaflow-backend";

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] { mqttBrokerUrl });
        options.setUserName("");
        options.setPassword("".toCharArray());
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInboundChannelAdapter(MqttPahoClientFactory clientFactory) {
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                CLIENT_ID, clientFactory, "sensor/+/data", "device/+/status", "device/+/alert");
        adapter.setOutputChannel(mqttInputChannel());
        adapter.setQos(1);
        return adapter;
    }

    @Bean
    public MessageChannel mqttOutputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageHandler mqttOutboundHandler(MqttPahoClientFactory clientFactory) {
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(CLIENT_ID + "-out", clientFactory);
        handler.setDefaultTopic("actuator/command");
        handler.setDefaultQos(1);
        handler.setDefaultRetained(false);
        return handler;
    }

    @Bean
    public Map<String, Object> mqttConnectionProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("connectTimeout", 5000);
        props.put("keepAliveInterval", 30);
        props.put("automaticReconnect", true);
        return props;
    }
}