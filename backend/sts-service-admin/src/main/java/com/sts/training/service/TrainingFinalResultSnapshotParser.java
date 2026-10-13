package com.sts.training.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.common.exception.BusinessException;
import com.sts.training.entity.TrainingParticipant;
import com.sts.training.entity.TrainingPlanContent;
import com.sts.training.vo.TrainingParticipantResultItemVO;
import com.sts.training.vo.TrainingParticipantResultsVO;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 版本化最终结果快照解析。v1 不含 sourceStatus；v2 增加来源状态；v3 增加题库练习反馈摘要。
 */
public final class TrainingFinalResultSnapshotParser {

  private TrainingFinalResultSnapshotParser() {
  }

  public static TrainingParticipantResultsVO parse(String json, TrainingParticipant participant,
                                                   List<TrainingPlanContent> contents,
                                                   ObjectMapper objectMapper) {
    if (!StringUtils.hasText(json) || objectMapper == null) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 8111, "最终结果快照无法解析");
    }
    JsonNode root;
    try {
      root = objectMapper.readTree(json);
    } catch (Exception ex) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 8111, "最终结果快照无法解析");
    }
    int version = root.path("schemaVersion").isMissingNode() || root.path("schemaVersion").isNull()
      ? 1 : root.path("schemaVersion").asInt();
    if (version != 1 && version != 2 && version != 3) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 8111, "不支持的最终结果快照版本");
    }
    TrainingParticipantResultsVO result = new TrainingParticipantResultsVO();
    result.setTrainingPlanId(longOr(root, "trainingPlanId",
                                    participant == null ? null : participant.getTrainingPlanId()));
    result.setTrainingParticipantId(longOr(root, "trainingParticipantId",
                                           participant == null ? null : participant.getTrainingParticipantId()));
    result.setResultStatus(text(root, "resultStatus",
                                participant == null ? null : participant.getResultStatus()));
    result.setOnlineHours(decimal(root, "onlineHours"));
    result.setOfflineHours(decimal(root, "offlineHours"));
    result.setCourseScore(decimal(root, "courseScore"));
    result.setExamScore(decimal(root, "examScore"));
    result.setOfflineScore(decimal(root, "offlineScore"));
    result.setCompositeScore(decimal(root, "compositeScore"));
    result.setOfficialCalculationRunId(longOrNull(root, "officialCalculationRunId"));
    result.setResultFinalizedTime(participant == null ? null : participant.getResultFinalizedTime());
    if (result.getResultFinalizedTime() == null) {
      result.setResultFinalizedTime(parseTime(text(root, "finalizedTime", null)));
    }
    Map<Long, TrainingPlanContent> contentById = (contents == null ? List.<TrainingPlanContent>of() : contents)
      .stream()
      .filter(item -> item.getPlanContentId() != null)
      .collect(Collectors.toMap(TrainingPlanContent::getPlanContentId, Function.identity(), (a, b) -> a));
    List<TrainingParticipantResultItemVO> items = new ArrayList<>();
    JsonNode itemsNode = root.path("items");
    if (itemsNode.isArray()) {
      for (JsonNode itemNode : itemsNode) {
        items.add(toItem(itemNode, version, contentById));
      }
    }
    result.setItems(items);
    return result;
  }

  private static TrainingParticipantResultItemVO toItem(JsonNode itemNode, int version,
                                                        Map<Long, TrainingPlanContent> contentById) {
    TrainingParticipantResultItemVO item = new TrainingParticipantResultItemVO();
    Long planContentId = longOrNull(itemNode, "planContentId");
    item.setPlanContentId(planContentId);
    TrainingPlanContent content = planContentId == null ? null : contentById.get(planContentId);
    if (content != null) {
      item.setContentName(content.getContentName());
      item.setExecutionType(content.getContentType());
    } else {
      item.setExecutionType(text(itemNode, "contentType", null));
    }
    item.setExecutionId(longOrNull(itemNode, "executionId"));
    item.setSourceRecordType(text(itemNode, "sourceRecordType", null));
    item.setSourceRecordId(longOrNull(itemNode, "sourceRecordId"));
    item.setFinalAttemptId(longOrNull(itemNode, "finalAttemptId"));
    item.setEvaluatedCount(integerOrNull(itemNode, "evaluatedCount"));
    item.setCorrectCount(integerOrNull(itemNode, "correctCount"));
    item.setWrongCount(integerOrNull(itemNode, "wrongCount"));
    item.setUnassessedCount(integerOrNull(itemNode, "unassessedCount"));
    item.setAccuracyRate(decimal(itemNode, "accuracyRate"));
    if (version >= 2 && itemNode.hasNonNull("sourceStatus")) {
      item.setSourceStatus(itemNode.get("sourceStatus").asText());
    }
    item.setResultStatus(text(itemNode, "resultStatus", null));
    if (itemNode.hasNonNull("effectiveDurationSeconds")) {
      item.setEffectiveDurationSeconds(itemNode.get("effectiveDurationSeconds").asInt());
    }
    item.setScore(decimal(itemNode, "score"));
    item.setCompletedTime(parseTime(text(itemNode, "completedTime", null)));
    return item;
  }

  private static Long longOr(JsonNode node, String field, Long fallback) {
    Long value = longOrNull(node, field);
    return value == null ? fallback : value;
  }

  private static Long longOrNull(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (value.isMissingNode() || value.isNull() || !value.isNumber()) {
      return null;
    }
    return value.asLong();
  }

  private static Integer integerOrNull(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (value.isMissingNode() || value.isNull() || !value.isInt()) {
      return null;
    }
    return value.asInt();
  }

  private static String text(JsonNode node, String field, String fallback) {
    JsonNode value = node.path(field);
    if (value.isMissingNode() || value.isNull()) {
      return fallback;
    }
    String text = value.asText();
    return StringUtils.hasText(text) ? text : fallback;
  }

  private static BigDecimal decimal(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (value.isMissingNode() || value.isNull()) {
      return null;
    }
    if (value.isNumber()) {
      return value.decimalValue();
    }
    String text = value.asText();
    if (!StringUtils.hasText(text)) {
      return null;
    }
    return new BigDecimal(text);
  }

  private static LocalDateTime parseTime(String text) {
    if (!StringUtils.hasText(text)) {
      return null;
    }
    try {
      return LocalDateTime.parse(text, TrainingResultSyncServiceImpl.SNAPSHOT_TIME);
    } catch (DateTimeParseException ex) {
      try {
        return LocalDateTime.parse(text);
      } catch (DateTimeParseException ignored) {
        return null;
      }
    }
  }
}
