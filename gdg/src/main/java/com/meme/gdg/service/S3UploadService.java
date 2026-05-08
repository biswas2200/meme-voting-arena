package com.meme.gdg.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

/**
 * Uploads meme images to S3 and returns a public HTTPS URL.
 *
 * In production the ECS task role must have s3:PutObject on the bucket.
 * Locally (dev/docker-dev) set AWS_ACCESS_KEY_ID + AWS_SECRET_ACCESS_KEY
 * env vars, or leave s3.bucket empty to fall back to local disk storage.
 */
@Service
@Slf4j
public class S3UploadService {

    @Value("${app.s3.bucket:}")
    private String bucket;

    @Value("${app.s3.region:ap-south-1}")
    private String region;

    /**
     * Returns true when S3 is configured (bucket name is set).
     */
    public boolean isEnabled() {
        return bucket != null && !bucket.isBlank();
    }

    /**
     * Uploads the file to S3 and returns the public HTTPS URL.
     */
    public String upload(MultipartFile file) throws IOException {
        String ext = "";
        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf("."));
        }
        String key = "uploads/" + UUID.randomUUID() + ext;

        S3Client s3 = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3.putObject(req, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        s3.close();

        // Return a public URL — bucket must have public-read or a CloudFront distribution
        String url = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
        log.info("Uploaded to S3: {}", url);
        return url;
    }
}
