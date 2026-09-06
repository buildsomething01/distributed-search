package com.example.search.controller;

import com.example.search.model.DocumentEvent;
import com.example.search.security.TenantContext;
import com.example.search.service.DocumentProducer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/documents")
@Profile("api")
public class DocumentController {
    private final TenantContext tenantContext;
    private final DocumentProducer producer;

    public DocumentController(TenantContext tenantContext, DocumentProducer producer) {
        this.tenantContext = tenantContext;
        this.producer = producer;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> index(
            @Valid @RequestBody DocumentRequest body,
            HttpServletRequest request) {
        String tenantId = tenantContext.tenantId(request);
        producer.send(new DocumentEvent(
                DocumentEvent.Operation.UPSERT,
                tenantId,
                body.documentId(),
                body.title(),
                body.content(),
                body.documentType(),
                body.department(),
                body.version(),
                Instant.now()));

        return ResponseEntity.accepted().body(Map.of(
                "documentId", body.documentId(),
                "status", "QUEUED"));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(
            @PathVariable String documentId,
            @RequestParam long version,
            HttpServletRequest request) {
        String tenantId = tenantContext.tenantId(request);
        producer.send(new DocumentEvent(
                DocumentEvent.Operation.DELETE,
                tenantId,
                documentId,
                null,
                null,
                null,
                null,
                version,
                Instant.now()));
        return ResponseEntity.accepted().build();
    }
}
