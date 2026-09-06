package com.example.search.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenSearchConfig {

    @Bean
    RestClient openSearchClient(
            @Value("${app.opensearch.url}") String url,
            @Value("${app.opensearch.username:}") String username,
            @Value("${app.opensearch.password:}") String password) {
        RestClient.Builder builder = RestClient.builder().baseUrl(url);
        if (StringUtils.hasText(username)) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION,
                    "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8)));
        }
        return builder.build();
    }
}
