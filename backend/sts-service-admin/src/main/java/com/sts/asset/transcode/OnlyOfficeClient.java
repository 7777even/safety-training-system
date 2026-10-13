package com.sts.asset.transcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@RequiredArgsConstructor
public class OnlyOfficeClient {
  private final OnlyOfficeProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public byte[] convert(Long jobId, String fileType, String sourceUrl) {
    validateConfiguration();
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("async", false);
      payload.put("key", "job-" + jobId);
      payload.put("filetype", fileType);
      payload.put("outputtype", "pdf");
      payload.put("url", sourceUrl);
      Map<String, Object> requestBody = new LinkedHashMap<>(payload);
      requestBody.put("token", jwt(payload));
      HttpRequest request = HttpRequest.newBuilder(
          URI.create(stripSlash(properties.getBaseUrl()) + "/converter"))
        .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
        .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) throw failure("OnlyOffice 转换请求失败");
      var json = objectMapper.readTree(response.body());
      if (!json.path("endConvert").asBoolean(false) || !json.hasNonNull("fileUrl")) {
        int error = json.path("error").asInt(0);
        throw new TranscodeFailureException(error == -4 || error == -5 ? "SOURCE_INVALID" : "TRANSCODE_ERROR",
                                            "OnlyOffice 转换失败，错误码 " + error);
      }
      return download(URI.create(json.path("fileUrl").asText()));
    } catch (TranscodeFailureException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw failure("OnlyOffice 请求被中断");
    } catch (IOException | IllegalArgumentException e) {
      throw failure("OnlyOffice 请求或响应无效");
    }
  }

  private byte[] download(URI uri) throws IOException, InterruptedException {
    validateDownloadUri(uri);
    HttpRequest request = HttpRequest.newBuilder(uri)
      .timeout(Duration.ofSeconds(properties.getTimeoutSeconds())).GET().build();
    HttpResponse<java.io.InputStream> response = httpClient.send(
      request, HttpResponse.BodyHandlers.ofInputStream());
    if (response.statusCode() / 100 != 2) throw failure("OnlyOffice 产物下载失败");
    long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
    if (declared > properties.getMaxOutputBytes()) throw failure("OnlyOffice 产物超过大小限制");
    try (var input = response.body(); var output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      long total = 0;
      for (int read; (read = input.read(buffer)) >= 0; ) {
        total += read;
        if (total > properties.getMaxOutputBytes()) throw failure("OnlyOffice 产物超过大小限制");
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    }
  }

  void validateDownloadUri(URI uri) {
    if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
      throw failure("OnlyOffice 下载地址协议不允许");
    }
    if (!properties.getAllowedDownloadHosts().contains(uri.getHost())) {
      throw failure("OnlyOffice 下载地址主机不允许");
    }
  }

  String jwt(Map<String, Object> payload) throws IOException {
    String header = base64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    String body = base64(objectMapper.writeValueAsBytes(payload));
    String signingInput = header + "." + body;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return signingInput + "." + base64(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("无法签发 OnlyOffice JWT", e);
    }
  }

  private void validateConfiguration() {
    if (properties.getBaseUrl() == null || properties.getJwtSecret() == null
      || properties.getJwtSecret().length() < 16 || properties.getInternalSourceBaseUrl() == null
      || properties.getAllowedDownloadHosts().isEmpty()) {
      throw new IllegalStateException("OnlyOffice 配置不完整");
    }
  }

  private static String base64(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  private static String stripSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private static TranscodeFailureException failure(String message) {
    return new TranscodeFailureException("TRANSCODE_ERROR", message);
  }
}
