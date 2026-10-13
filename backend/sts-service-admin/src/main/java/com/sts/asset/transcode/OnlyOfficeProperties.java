package com.sts.asset.transcode;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "sts.transcode.onlyoffice")
public class OnlyOfficeProperties {
  private String baseUrl;
  private String jwtSecret;
  private String internalSourceBaseUrl;
  private List<String> allowedDownloadHosts = new ArrayList<>();
  private long maxOutputBytes = 209715200;
  private int timeoutSeconds = 120;
}
