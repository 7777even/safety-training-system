package com.sts.admin.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
    public StorageService localStorageService(StorageProperties props, TenantObjectKeyService objectKeys) {
      return new LocalStorageAdapter(props, objectKeys);
    }

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(name = "storage.type", havingValue = "minio")
  public S3Client minioS3Client(StorageProperties props) {
    StorageProperties.Minio minio = props.getMinio();
    requireText(minio.getEndpoint(), "storage.minio.endpoint");
    requireText(minio.getAccessKey(), "storage.minio.access-key");
    requireText(minio.getSecretKey(), "storage.minio.secret-key");
    requireText(minio.getBucket(), "storage.minio.bucket");
    return S3Client.builder()
      .endpointOverride(URI.create(minio.getEndpoint()))
      .region(Region.of(minio.getRegion()))
      .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create(minio.getAccessKey(), minio.getSecretKey())))
      .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
      .build();
  }

  @Bean
  @ConditionalOnProperty(name = "storage.type", havingValue = "minio")
  public S3StorageGateway s3StorageGateway(S3Client client, StorageProperties props) {
    return new AwsS3StorageGateway(client, props.getMinio());
  }

  @Bean
  @ConditionalOnProperty(name = "storage.type", havingValue = "minio")
  public StorageService minioStorageService(S3StorageGateway gateway, TenantObjectKeyService objectKeys) {
    return new MinioStorageService(gateway, objectKeys);
  }

  private static void requireText(String value, String key) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(key + " must not be blank");
    }
  }
}
