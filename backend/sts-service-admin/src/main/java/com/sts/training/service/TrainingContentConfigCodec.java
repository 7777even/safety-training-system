package com.sts.training.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.common.exception.BusinessException;
import com.sts.training.dto.TrainingContentExecutionConfig;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 培训内容 executionConfig JSON 编解码。
 */
@Component
public class TrainingContentConfigCodec {

  private final ObjectMapper objectMapper;

  public TrainingContentConfigCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String toJson(TrainingContentExecutionConfig config) {
    if (config == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(config);
    } catch (JsonProcessingException ex) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8124, "内容执行配置无法序列化");
    }
  }

  public TrainingContentExecutionConfig fromJson(String json) {
    if (!StringUtils.hasText(json)) {
      return null;
    }
    try {
      return objectMapper.readValue(json, TrainingContentExecutionConfig.class);
    } catch (JsonProcessingException ex) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8124, "内容执行配置无法解析");
    }
  }
}
