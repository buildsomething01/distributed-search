package com.example.search.service;

import com.example.search.model.DocumentEvent;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Profile("indexer")
public class DocumentConsumer {
    private final OpenSearchService openSearchService;

    public DocumentConsumer(OpenSearchService openSearchService) {
        this.openSearchService = openSearchService;
    }

    @KafkaListener(
            topics = "${app.kafka.document-topic:document-index-events}",
            groupId = "${app.kafka.indexer-group:document-indexers}",
            containerFactory = "batchKafkaListenerContainerFactory")
    public void consume(List<DocumentEvent> events) {
        openSearchService.bulkIndex(events);
    }
}
