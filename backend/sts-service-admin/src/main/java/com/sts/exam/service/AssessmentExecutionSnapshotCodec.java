package com.sts.exam.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.sts.common.exception.BusinessException;
import com.sts.exam.entity.AssessmentExecution;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * 统一编码、规范化并校验考核执行快照，隔离 MySQL JSON 的物理文本差异。
 */
@Component
public final class AssessmentExecutionSnapshotCodec {

  public static final int CURRENT_SCHEMA_VERSION = 1;

  private final ObjectMapper objectMapper;
  private final CanonicalJsonHasher canonicalJsonHasher;

  public AssessmentExecutionSnapshotCodec(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy()
      .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
      .setSerializationInclusion(JsonInclude.Include.ALWAYS)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    this.canonicalJsonHasher = new CanonicalJsonHasher(this.objectMapper);
  }

  /**
   * 根据执行对象当前构建视图生成 v1 完整快照和规范化摘要。
   */
  public AssessmentExecutionSnapshot encode(AssessmentExecution execution) {
    if (execution == null) {
      throw corrupted("考核执行快照不完整");
    }
    try {
      JsonNode policy = StringUtils.hasText(execution.getPolicySnapshotJson())
        ? objectMapper.readTree(execution.getPolicySnapshotJson()) : objectMapper.createObjectNode();
      if (policy == null || !policy.isObject()) {
        throw corrupted("考核执行策略快照必须是 JSON 对象");
      }
      AssessmentExecutionSnapshot snapshot = new AssessmentExecutionSnapshot(
        CURRENT_SCHEMA_VERSION,
        execution.getOrganizationId(),
        new AssessmentExecutionSnapshot.Source(
          execution.getSourceType(), execution.getSourceBusinessId(), execution.getExamId(),
          execution.getCourseOfferingId(), execution.getCourseVersionId(), execution.getCourseItemId()),
        execution.getAssessmentKind(),
        new AssessmentExecutionSnapshot.Resource(
          execution.getResourceType(), execution.getResourceId(), execution.getResourceNameSnapshot()),
        new AssessmentExecutionSnapshot.Rules(
          execution.getDurationSecondsSnapshot(), decimal(execution.getTotalScoreSnapshot()),
          decimal(execution.getPassScoreSnapshot()), execution.getQuestionOrderModeSnapshot(),
          execution.getMultiSelectScoringSnapshot(), policy));
      validateRequired(snapshot);
      String json = objectMapper.writeValueAsString(snapshot);
      execution.setSnapshotSchemaVersion(CURRENT_SCHEMA_VERSION);
      execution.setSnapshotJson(json);
      execution.setSnapshotHash(hash(snapshot));
      return snapshot;
    } catch (JsonProcessingException ex) {
      throw corrupted("考核执行快照无法解析");
    }
  }

  /**
   * 校验数据库快照的版本、摘要和查询投影，并回填不落库的已验证视图。
   */
  public AssessmentExecutionSnapshot decodeAndVerify(AssessmentExecution execution) {
    if (execution == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 7061, "考核执行不存在");
    }
    if (!Objects.equals(execution.getSnapshotSchemaVersion(), CURRENT_SCHEMA_VERSION)
      || !StringUtils.hasText(execution.getSnapshotJson())
      || !StringUtils.hasText(execution.getSnapshotHash())) {
      throw corrupted("考核执行快照不完整或版本不支持");
    }
    try {
      AssessmentExecutionSnapshot snapshot = objectMapper.readValue(
        execution.getSnapshotJson(), AssessmentExecutionSnapshot.class);
      validateRequired(snapshot);
      if (snapshot.schemaVersion() != execution.getSnapshotSchemaVersion()
        || !MessageDigest.isEqual(execution.getSnapshotHash().getBytes(StandardCharsets.UTF_8),
                                  hash(snapshot).getBytes(StandardCharsets.UTF_8))) {
        throw corrupted("考核执行快照已损坏");
      }
      assertProjection(execution, snapshot);
      assertExistingView(execution, snapshot);
      hydrateVerifiedView(execution, snapshot);
      return snapshot;
    } catch (JsonProcessingException | IllegalArgumentException ex) {
      throw corrupted("考核执行快照无法解析");
    }
  }

  /**
   * 对快照语义生成与存储文本无关的 SHA-256。
   */
  public String hash(AssessmentExecutionSnapshot snapshot) {
    return canonicalJsonHasher.hash(snapshot);
  }

  private void validateRequired(AssessmentExecutionSnapshot snapshot) {
    if (snapshot == null || snapshot.schemaVersion() != CURRENT_SCHEMA_VERSION
      || snapshot.organizationId() == null || snapshot.source() == null
      || !StringUtils.hasText(snapshot.source().type()) || snapshot.source().businessId() == null
      || snapshot.assessmentKind() == null || snapshot.resource() == null
      || !StringUtils.hasText(snapshot.resource().type()) || snapshot.resource().id() == null
      || !StringUtils.hasText(snapshot.resource().name()) || snapshot.rules() == null
      || snapshot.rules().durationSeconds() == null || snapshot.rules().policy() == null
      || !snapshot.rules().policy().isObject()) {
      throw corrupted("考核执行快照不完整");
    }
  }

  private void assertProjection(AssessmentExecution execution, AssessmentExecutionSnapshot snapshot) {
    AssessmentExecutionSnapshot.Source source = snapshot.source();
    AssessmentExecutionSnapshot.Resource resource = snapshot.resource();
    if (!Objects.equals(execution.getOrganizationId(), snapshot.organizationId())
      || !Objects.equals(execution.getSourceType(), source.type())
      || !Objects.equals(execution.getSourceBusinessId(), source.businessId())
      || !Objects.equals(execution.getExamId(), source.examId())
      || !Objects.equals(execution.getCourseOfferingId(), source.courseOfferingId())
      || !Objects.equals(execution.getCourseVersionId(), source.courseVersionId())
      || !Objects.equals(execution.getCourseItemId(), source.courseItemId())
      || !Objects.equals(execution.getAssessmentKind(), snapshot.assessmentKind())
      || !Objects.equals(execution.getResourceType(), resource.type())
      || !Objects.equals(execution.getResourceId(), resource.id())) {
      throw corrupted("考核执行快照与查询投影不一致");
    }
  }

  /**
   * 测试或内存调用已携带视图时，同样禁止视图绕过完整快照。
   */
  private void assertExistingView(AssessmentExecution execution, AssessmentExecutionSnapshot snapshot) {
    AssessmentExecutionSnapshot.Rules rules = snapshot.rules();
    if ((execution.getResourceNameSnapshot() != null
      && !Objects.equals(execution.getResourceNameSnapshot(), snapshot.resource().name()))
      || (execution.getDurationSecondsSnapshot() != null
      && !Objects.equals(execution.getDurationSecondsSnapshot(), rules.durationSeconds()))
      || (execution.getTotalScoreSnapshot() != null
      && !Objects.equals(decimal(execution.getTotalScoreSnapshot()), rules.totalScore()))
      || (execution.getPassScoreSnapshot() != null
      && !Objects.equals(decimal(execution.getPassScoreSnapshot()), rules.passScore()))
      || (execution.getQuestionOrderModeSnapshot() != null
      && !Objects.equals(execution.getQuestionOrderModeSnapshot(), rules.questionOrderMode()))
      || (execution.getMultiSelectScoringSnapshot() != null
      && !Objects.equals(execution.getMultiSelectScoringSnapshot(), rules.multiSelectScoring()))
      || (execution.getPolicySnapshotJson() != null
      && !canonicalizePolicy(execution.getPolicySnapshotJson())
      .equals(canonicalJsonHasher.canonicalize(rules.policy())))) {
      throw corrupted("考核执行快照与已加载视图不一致");
    }
  }

  private JsonNode canonicalizePolicy(String json) {
    try {
      return canonicalJsonHasher.canonicalize(objectMapper.readTree(json));
    } catch (JsonProcessingException ex) {
      throw corrupted("考核执行策略快照无法解析");
    }
  }

  private void hydrateVerifiedView(AssessmentExecution execution, AssessmentExecutionSnapshot snapshot)
    throws JsonProcessingException {
    AssessmentExecutionSnapshot.Rules rules = snapshot.rules();
    execution.setResourceNameSnapshot(snapshot.resource().name());
    execution.setDurationSecondsSnapshot(rules.durationSeconds());
    execution.setTotalScoreSnapshot(parseDecimal(rules.totalScore()));
    execution.setPassScoreSnapshot(parseDecimal(rules.passScore()));
    execution.setQuestionOrderModeSnapshot(rules.questionOrderMode());
    execution.setMultiSelectScoringSnapshot(rules.multiSelectScoring());
    execution.setPolicySnapshotJson(objectMapper.writeValueAsString(rules.policy()));
  }

  private String decimal(BigDecimal value) {
    return value == null ? null : value.stripTrailingZeros().toPlainString();
  }

  private BigDecimal parseDecimal(String value) {
    return value == null ? null : new BigDecimal(value);
  }

  private BusinessException corrupted(String message) {
    return new BusinessException(HttpStatus.BAD_REQUEST, 7063, message);
  }
}
