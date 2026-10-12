package com.team503.messenger.file.service;

import com.team503.messenger.file.config.R2Properties;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Cloudflare R2 (S3-compatible) implementation. The endpoint and credentials
 * come from {@link R2Properties}; the secret is never logged or returned in
 * any controller response.
 */
public class R2StorageService implements ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(R2StorageService.class);

    private final S3Client client;
    private final S3Presigner presigner;
    private final R2Properties properties;

    public R2StorageService(R2Properties properties) {
        this.properties = properties;
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getAccessKeyId(),
                properties.getSecretAccessKey()
        );

        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        this.client = S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.US_EAST_1) // R2 requires a region; US_EAST_1 is the conventional choice.
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Config)
                .build();

        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Config)
                .build();
    }

    @Override
    public String putObject(String objectKey, InputStream content, long contentLength, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        PutObjectResponse response = client.putObject(request, RequestBody.fromInputStream(content, contentLength));
        String etag = response.eTag();
        log.debug("Stored object key={} etag={}", objectKey, etag);
        return etag;
    }

    @Override
    public URI createDownloadUrl(String objectKey) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(objectKey)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(properties.getPresignedTtlSeconds()))
                .getObjectRequest(getRequest)
                .build();
        PresignedGetObjectRequest presigned = presigner.presignGetObject(presignRequest);
        return toUri(presigned.url().toString());
    }

    @Override
    public URI createUploadUrl(String objectKey, String contentType) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(objectKey)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(properties.getPresignedTtlSeconds()))
                .putObjectRequest(putRequest)
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);
        return toUri(presigned.url().toString());
    }

    private static URI toUri(String value) {
        try {
            return new URI(value);
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException("Could not build R2 URL", e);
        }
    }

    @Override
    public void deleteObject(String objectKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(objectKey)
                .build();
        client.deleteObject(request);
    }

    @Override
    public boolean objectExists(String objectKey) {
        try {
            client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucketName())
                    .key(objectKey)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
}
