package com.sts.asset.transcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(OnlyOfficeProperties.class)
public class OnlyOfficeConfiguration {
  @Bean
  @ConditionalOnExpression("'${sts.transcode.provider:stub}' == 'ffmpeg'"
    + " && '${sts.transcode.document-provider:disabled}' == 'onlyoffice'")
  OnlyOfficeClient onlyOfficeClient(OnlyOfficeProperties properties, ObjectMapper objectMapper) {
    return new OnlyOfficeClient(properties, objectMapper,
                                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build());
  }
}
