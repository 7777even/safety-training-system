package com.sts.exam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sts.common.exception.BusinessException;
import com.sts.exam.entity.AssessmentExecution;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssessmentExecutionSnapshotCodecTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final AssessmentExecutionSnapshotCodec codec = new AssessmentExecutionSnapshotCodec(objectMapper);

  @Test
  void mysqlJsonTextNormalization_doesNotChangeSemanticHash() throws Exception {
    AssessmentExecution execution = courseExecution();
    codec.encode(execution);
    String originalHash = execution.getSnapshotHash();

    JsonNode stored = objectMapper.readTree(execution.getSnapshotJson());
    ObjectNode reordered = objectMapper.createObjectNode();
    reordered.set("rules", stored.get("rules"));
    reordered.set("resource", stored.get("resource"));
    reordered.set("assessmentKind", stored.get("assessmentKind"));
    reordered.set("source", stored.get("source"));
    reordered.set("organizationId", stored.get("organizationId"));
    reordered.set("schemaVersion", stored.get("schemaVersion"));
    execution.setSnapshotJson(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(reordered));

    codec.decodeAndVerify(execution);

    assertThat(execution.getSnapshotHash()).isEqualTo(originalHash);
    assertThat(execution.getPolicySnapshotJson()).contains("completionRule");
    assertThat(execution.getTotalScoreSnapshot()).isEqualByComparingTo("100");
  }

  @Test
  void queryProjectionChanged_withoutSnapshotChange_isRejected() {
    AssessmentExecution execution = courseExecution();
    codec.encode(execution);
    execution.setResourceId(999L);

    assertThatThrownBy(() -> codec.decodeAndVerify(execution))
      .isInstanceOfSatisfying(BusinessException.class,
                              ex -> assertThat(ex.getCode()).isEqualTo(7063));
  }

  @Test
  void equivalentPolicyNumbersAndObjectOrder_haveSameHashButArrayOrderDoesNot() {
    AssessmentExecution first = courseExecution();
    first.setPolicySnapshotJson("{\"threshold\":1.0,\"drawRules\":[1,2]}");
    AssessmentExecution equivalent = courseExecution();
    equivalent.setPolicySnapshotJson("{\"drawRules\":[1,2],\"threshold\":1.00}");
    AssessmentExecution reorderedArray = courseExecution();
    reorderedArray.setPolicySnapshotJson("{\"threshold\":1,\"drawRules\":[2,1]}");

    codec.encode(first);
    codec.encode(equivalent);
    codec.encode(reorderedArray);

    assertThat(equivalent.getSnapshotHash()).isEqualTo(first.getSnapshotHash());
    assertThat(reorderedArray.getSnapshotHash()).isNotEqualTo(first.getSnapshotHash());
  }

  @Test
  void unknownVersionAndChangedSnapshotContent_areRejected() throws Exception {
    AssessmentExecution unknownVersion = courseExecution();
    codec.encode(unknownVersion);
    unknownVersion.setSnapshotSchemaVersion(2);

    assertThatThrownBy(() -> codec.decodeAndVerify(unknownVersion))
      .isInstanceOfSatisfying(BusinessException.class,
                              ex -> assertThat(ex.getCode()).isEqualTo(7063));

    AssessmentExecution changed = courseExecution();
    codec.encode(changed);
    ObjectNode json = (ObjectNode) objectMapper.readTree(changed.getSnapshotJson());
    ((ObjectNode) json.get("rules")).put("durationSeconds", 60);
    changed.setSnapshotJson(objectMapper.writeValueAsString(json));

    assertThatThrownBy(() -> codec.decodeAndVerify(changed))
      .isInstanceOfSatisfying(BusinessException.class,
                              ex -> assertThat(ex.getCode()).isEqualTo(7063));
  }

  private AssessmentExecution courseExecution() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setOrganizationId(1L);
    execution.setSourceType("COURSE_QUIZ");
    execution.setSourceBusinessId(58L);
    execution.setCourseOfferingId(9L);
    execution.setCourseVersionId(10L);
    execution.setCourseItemId(58L);
    execution.setAssessmentKind("QUIZ");
    execution.setResourceType("PAPER_VERSION");
    execution.setResourceId(11L);
    execution.setResourceNameSnapshot("课程测验卷");
    execution.setDurationSecondsSnapshot(1800);
    execution.setTotalScoreSnapshot(new BigDecimal("100.00"));
    execution.setPassScoreSnapshot(new BigDecimal("60.00"));
    execution.setQuestionOrderModeSnapshot("IMPORT");
    execution.setMultiSelectScoringSnapshot("ALL_OR_NOTHING");
    execution.setPolicySnapshotJson("{\"completionRule\":\"ANSWER_ALL\",\"accuracyThreshold\":0.0}");
    execution.setExecutionStatus("READY");
    return execution;
  }
}
