package com.sts.survey.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 问卷投放链接配置。
 */
@Data
@ConfigurationProperties(prefix = "sts.survey")
public class SurveyProperties {

  /**
   * 填写页公开前缀，与投放 ID 拼接成可访问链接和二维码内容。
   */
  private String publicFillUrlPrefix = "/survey/fill";
}
