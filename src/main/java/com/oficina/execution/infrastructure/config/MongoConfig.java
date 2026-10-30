package com.oficina.execution.infrastructure.config;

import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

@Configuration
public class MongoConfig {

    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory fabrica) {
        return new MongoTransactionManager(fabrica,
            TransactionOptions.builder().readPreference(ReadPreference.primary()).build());
    }

    @Bean
    public MappingMongoConverter mappingMongoConverter(MongoDatabaseFactory fabrica, MongoMappingContext contexto,
                                                       MongoCustomConversions conversoes) {
        var conversor = new MappingMongoConverter(new DefaultDbRefResolver(fabrica), contexto);
        conversor.setCustomConversions(conversoes);
        conversor.setTypeMapper(new DefaultMongoTypeMapper(null));
        return conversor;
    }
}
