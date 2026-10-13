package com.sts.survey.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册问卷配置。
 */
@Configuration
@EnableConfigurationProperties(SurveyProperties.class)
public class SurveyConfiguration {
}
