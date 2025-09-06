package ru.javaboys.huntyhr.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

/**
 * Verifies availability of configured S3 storage on application startup.
 * If S3 is not reachable or the bucket is not accessible, application startup will fail.
 */
@Slf4j
@Component
public class S3StartupHealthCheck {

    private final S3Client amazonS3;
    private final String bucketName;

    public S3StartupHealthCheck(S3Client amazonS3,
                                @Value("${jmix.awsfs.bucket}") String bucketName) {
        this.amazonS3 = amazonS3;
        this.bucketName = bucketName;
    }

    @PostConstruct
    public void verifyS3Availability() {
        try {
            // Perform a lightweight call that requires connectivity and auth
            amazonS3.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            log.info("S3 bucket '{}' is accessible. Startup check passed.", bucketName);
        } catch (Exception e) {
            log.error("S3 bucket '{}' is not accessible. Failing application startup.", bucketName, e);
            // Throwing RuntimeException will stop Spring Boot startup during context initialization
            throw new IllegalStateException("S3 storage is not available for bucket '" + bucketName + "'", e);
        }
    }
}
