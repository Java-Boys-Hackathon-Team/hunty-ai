package ru.javaboys.huntyhr.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
public class S3ClientConfig {

    @Bean
    public S3Client s3Client(
            @Value("${jmix.awsfs.access-key}") String accessKey,
            @Value("${jmix.awsfs.secret-access-key}") String secretKey,
            @Value("${jmix.awsfs.region}") String region,
            @Value("${jmix.awsfs.endpoint-url}") String endpointUrl
    ) {
        // MinIO requires path-style access
        S3Configuration s3Conf = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .endpointOverride(URI.create(endpointUrl))
                .serviceConfiguration(s3Conf)
                .build();
    }
}
