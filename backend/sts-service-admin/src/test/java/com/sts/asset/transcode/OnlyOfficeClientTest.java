package com.sts.asset.transcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OnlyOfficeClientTest {
  @Test
  void signsHs256JwtWithoutExposingSecret() throws Exception {
    OnlyOfficeProperties properties = properties();
    OnlyOfficeClient client = new OnlyOfficeClient(properties, new ObjectMapper(), HttpClient.newHttpClient());
    String jwt = client.jwt(Map.of("key", "job-9"));
    assertThat(jwt.split("\\.")).hasSize(3);
    assertThat(jwt).doesNotContain(properties.getJwtSecret());
  }

  @Test
  void rejectsNonHttpAndUnlistedDownloadHosts() {
    OnlyOfficeClient client = new OnlyOfficeClient(properties(), new ObjectMapper(), HttpClient.newHttpClient());
    assertThatThrownBy(() -> client.validateDownloadUri(URI.create("file:///etc/passwd")))
      .isInstanceOf(TranscodeFailureException.class);
    assertThatThrownBy(() -> client.validateDownloadUri(URI.create("http://169.254.169.254/meta")))
      .isInstanceOf(TranscodeFailureException.class);
    client.validateDownloadUri(URI.create("http://onlyoffice-ds/cache/result.pdf"));
  }

  private static OnlyOfficeProperties properties() {
    OnlyOfficeProperties properties = new OnlyOfficeProperties();
    properties.setBaseUrl("http://onlyoffice-ds");
    properties.setJwtSecret("12345678901234567890123456789012");
    properties.setInternalSourceBaseUrl("http://sts-admin:8080");
    properties.setAllowedDownloadHosts(List.of("onlyoffice-ds"));
    return properties;
  }
}
