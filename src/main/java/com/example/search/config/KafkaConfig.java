package com.example.search.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@Profile("indexer")
public class KafkaConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<Object, Object> batchKafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
