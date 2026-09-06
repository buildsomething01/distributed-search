package com.example.search.service;

import com.example.search.model.DocumentEvent;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("api")
public class DocumentProducer {
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String topic;

    public DocumentProducer(
            KafkaTemplate<Object, Object> kafkaTemplate,
            @Value("${app.kafka.document-topic:document-index-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void send(DocumentEvent event) {
        String key = event.tenantId() + ":" + event.documentId();
        try {
            kafkaTemplate.send(topic, key, event).get(3, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not queue document for indexing", ex);
        }
    }
}
