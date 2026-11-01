package com.cloudnote.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(name = "cloudnote.storage", havingValue = "s3")
public class S3Config {

    /**
     * No access keys anywhere: DefaultCredentialsProvider picks up the EC2 instance role
     * (via instance metadata) automatically.
     */
    @Bean
    S3Client s3Client(@Value("${cloudnote.s3.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
