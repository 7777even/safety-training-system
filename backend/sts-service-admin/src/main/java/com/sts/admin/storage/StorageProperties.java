package com.sts.admin.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

  private String type = "local";

  private Local local = new Local();
  private Minio minio = new Minio();

  @Data
  public static class Local {
    private String basePath = "/data/uploads";
    private String urlPrefix = "http://localhost:8080/api/files";
  }

  @Data
  public static class Minio {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket = "sts-private";
    private String region = "us-east-1";
    private boolean autoCreateBucket;
    private long multipartPartSize = 10 * 1024 * 1024L;
    private int multipartExpireHours = 24;
    private long cleanupIntervalMs = 3_600_000;
  }
}
