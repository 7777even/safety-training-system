package com.sts.exam.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sts.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 按 JSON 语义生成稳定摘要：对象键排序、数组保序、数字去除无意义尾零。
 */
public final class CanonicalJsonHasher {

  private final ObjectMapper objectMapper;

  public CanonicalJsonHasher(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  /**
   * 对任意可序列化值的规范化 UTF-8 JSON 计算 SHA-256。
   */
  public String hash(Object value) {
    try {
      String canonical = objectMapper.writeValueAsString(canonicalize(objectMapper.valueToTree(value)));
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                                                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (JsonProcessingException | IllegalArgumentException ex) {
      throw corrupted("考核执行快照无法规范化");
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 不可用", ex);
    }
  }

  /**
   * 返回不改变数组顺序的规范化 JSON 树。
   */
  public JsonNode canonicalize(JsonNode value) {
    if (value == null || value.isNull()) return objectMapper.nullNode();
    if (value.isObject()) {
      ObjectNode sorted = objectMapper.createObjectNode();
      List<String> names = new ArrayList<>();
      value.fieldNames().forEachRemaining(names::add);
      Collections.sort(names);
      for (String name : names) sorted.set(name, canonicalize(value.get(name)));
      return sorted;
    }
    if (value.isArray()) {
      ArrayNode result = objectMapper.createArrayNode();
      value.forEach(item -> result.add(canonicalize(item)));
      return result;
    }
    if (value.isNumber()) {
      return DecimalNode.valueOf(value.decimalValue().stripTrailingZeros());
    }
    return value.deepCopy();
  }

  private BusinessException corrupted(String message) {
    return new BusinessException(HttpStatus.BAD_REQUEST, 7063, message);
  }
}
