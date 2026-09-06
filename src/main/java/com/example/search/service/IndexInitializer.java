package com.example.search.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class IndexInitializer implements ApplicationRunner {
    private final OpenSearchService openSearchService;

    public IndexInitializer(OpenSearchService openSearchService) {
        this.openSearchService = openSearchService;
    }

    @Override
    public void run(ApplicationArguments args) {
        openSearchService.initialize();
    }
}
