package com.sts.training.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.training.entity.*;
import com.sts.training.support.TrainingMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TrainingMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TrainingPlanMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private TrainingPlanMapper trainingPlanMapper;
  @Autowired
  private TrainingTypeMapper trainingTypeMapper;
  @Autowired
  private TrainingParticipantMapper trainingParticipantMapper;
  @Autowired
  private TrainingPlanStageMapper trainingPlanStageMapper;
  @Autowired
  private TrainingPlanContentMapper trainingPlanContentMapper;
  @Autowired
  private TrainingOfflineSessionMapper offlineSessionMapper;
  @Autowired
  private TrainingParticipantContentResultMapper resultMapper;
  @Autowired
  private OrganizationMapper organizationMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("培训计划可持久化培训性质和发布冻结最低学时")
  void planPersistsTrainingNatureAndApplicableHours() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Long typeId = insertType(orgId, "IT-PLAN-TYPE-NATURE");
    TrainingPlan plan = plan(orgId, typeId, "IT-PLAN-NATURE");
    plan.setTrainingNature("REVIEW");
    plan.setApplicableRequiredHours(new BigDecimal("24.00"));
    trainingPlanMapper.insert(plan);

    TrainingPlan loaded = trainingPlanMapper.selectById(plan.getTrainingPlanId());
    assertThat(loaded.getTrainingNature()).isEqualTo("REVIEW");
    assertThat(loaded.getApplicableRequiredHours()).isEqualByComparingTo("24.00");
  }

  @Test
  @DisplayName("uk_org_plan_code_deleted：逻辑删除后可复用计划编码")
  void planCodeReuseAfterLogicDelete() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Long typeId = insertType(orgId, "IT-PLAN-TYPE-1");
    TrainingPlan first = plan(orgId, typeId, "IT-PLAN-1");
    trainingPlanMapper.insert(first);
    Long id = first.getTrainingPlanId();
    jdbcTemplate.update("UPDATE sts_training_plan SET deleted = ? WHERE training_plan_id = ?", id, id);

    TrainingPlan second = plan(orgId, typeId, "IT-PLAN-1");
    trainingPlanMapper.insert(second);
    assertThat(second.getTrainingPlanId()).isNotNull();
  }

  @Test
  @DisplayName("uk_plan_user_deleted：参训人唯一")
  void participantUnique() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Long typeId = insertType(orgId, "IT-PLAN-TYPE-2");
    TrainingPlan plan = plan(orgId, typeId, "IT-PLAN-2");
    trainingPlanMapper.insert(plan);

    TrainingParticipant first = participant(orgId, plan.getTrainingPlanId(), 100L);
    trainingParticipantMapper.insert(first);

    assertThatThrownBy(() -> trainingParticipantMapper.insert(participant(orgId, plan.getTrainingPlanId(), 100L)))
      .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("计划内容可持久化资源版本、考试模式、执行配置和快照哈希")
  void planContentPersistsFrozenResourceFields() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Long typeId = insertType(orgId, "IT-PLAN-TYPE-3");
    TrainingPlan plan = plan(orgId, typeId, "IT-PLAN-3");
    trainingPlanMapper.insert(plan);

    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setTrainingPlanId(plan.getTrainingPlanId());
    stage.setStageName("阶段");
    stage.setStageType("ONLINE");
    stage.setSortOrder(1);
    stage.setStageStatus("PENDING");
    trainingPlanStageMapper.insert(stage);

    TrainingPlanContent content = new TrainingPlanContent();
    content.setPlanStageId(stage.getPlanStageId());
    content.setContentType("EXAM");
    content.setResourceId(501L);
    content.setResourceVersionId(88L);
    content.setAssessmentMode("QUIZ");
    content.setExecutionConfigJson(null);
    content.setResourceSnapshotJson("{\"contentType\":\"EXAM\",\"resourceVersionId\":88}");
    content.setResourceSnapshotHash("abc123");
    content.setContentName("测验");
    content.setIsRequired(1);
    content.setIsScored(1);
    content.setSortOrder(1);
    content.setContentStatus("PENDING");
    trainingPlanContentMapper.insert(content);

    TrainingPlanContent loaded = trainingPlanContentMapper.selectById(content.getPlanContentId());
    assertThat(loaded.getResourceVersionId()).isEqualTo(88L);
    assertThat(loaded.getAssessmentMode()).isEqualTo("QUIZ");
    assertThat(loaded.getResourceSnapshotHash()).isEqualTo("abc123");
    assertThat(loaded.getResourceSnapshotJson()).contains("EXAM");
  }

  @Test
  @DisplayName("uk_plan_content_deleted：同一计划内容只能有一条线下场次")
  void offlineSessionPlanContentUnique() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Long typeId = insertType(orgId, "IT-PLAN-TYPE-4");
    TrainingPlan plan = plan(orgId, typeId, "IT-PLAN-4");
    trainingPlanMapper.insert(plan);
    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setTrainingPlanId(plan.getTrainingPlanId());
    stage.setStageName("线下");
    stage.setStageType("OFFLINE");
    stage.setSortOrder(1);
    stage.setStageStatus("PENDING");
    trainingPlanStageMapper.insert(stage);

    TrainingOfflineSession first = session(orgId, plan.getTrainingPlanId(), stage.getPlanStageId(), 77001L, "场次1");
    offlineSessionMapper.insert(first);
    TrainingOfflineSession second = session(orgId, plan.getTrainingPlanId(), stage.getPlanStageId(), 77001L, "场次2");
    assertThatThrownBy(() -> offlineSessionMapper.insert(second)).isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("uk_participant_content_deleted：同一参训人同一内容只有一条结果投影")
  void participantContentResultUnique() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Long typeId = insertType(orgId, "IT-PLAN-TYPE-5");
    TrainingPlan plan = plan(orgId, typeId, "IT-PLAN-5");
    trainingPlanMapper.insert(plan);
    TrainingParticipant person = participant(orgId, plan.getTrainingPlanId(), 501L);
    trainingParticipantMapper.insert(person);

    TrainingParticipantContentResult first = projection(orgId, plan.getTrainingPlanId(),
                                                        person.getTrainingParticipantId(), 88001L);
    resultMapper.insert(first);
    TrainingParticipantContentResult dup = projection(orgId, plan.getTrainingPlanId(),
                                                      person.getTrainingParticipantId(), 88001L);
    assertThatThrownBy(() -> resultMapper.insert(dup)).isInstanceOf(DuplicateKeyException.class);

    jdbcTemplate.update("UPDATE sts_training_participant_content_result SET deleted = ? "
                          + "WHERE training_participant_content_result_id = ?",
                        first.getTrainingParticipantContentResultId(),
                        first.getTrainingParticipantContentResultId());
    TrainingParticipantContentResult reused = projection(orgId, plan.getTrainingPlanId(),
                                                         person.getTrainingParticipantId(), 88001L);
    resultMapper.insert(reused);
    assertThat(reused.getTrainingParticipantContentResultId()).isNotNull();
  }

  @Test
  @DisplayName("投影可持久化可空 sourceStatus，与 resultStatus 分离")
  void participantContentResultPersistsSourceStatus() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Long typeId = insertType(orgId, "IT-PLAN-TYPE-7");
    TrainingPlan plan = plan(orgId, typeId, "IT-PLAN-7");
    trainingPlanMapper.insert(plan);
    TrainingParticipant person = participant(orgId, plan.getTrainingPlanId(), 701L);
    trainingParticipantMapper.insert(person);
    TrainingParticipantContentResult row = projection(orgId, plan.getTrainingPlanId(),
                                                      person.getTrainingParticipantId(), 88002L);
    row.setSourceRecordType("EXAM_CANDIDATE");
    row.setSourceRecordId(91L);
    row.setSourceStatus("PASSED");
    row.setResultStatus("COMPLETED");
    resultMapper.insert(row);

    TrainingParticipantContentResult loaded = resultMapper.selectById(row.getTrainingParticipantContentResultId());
    assertThat(loaded.getSourceStatus()).isEqualTo("PASSED");
    assertThat(loaded.getResultStatus()).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("参训人可持久化最终结果快照字段")
  void participantPersistsFinalResultSnapshot() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Long typeId = insertType(orgId, "IT-PLAN-TYPE-6");
    TrainingPlan plan = plan(orgId, typeId, "IT-PLAN-6");
    trainingPlanMapper.insert(plan);
    TrainingParticipant person = participant(orgId, plan.getTrainingPlanId(), 601L);
    person.setFinalResultSnapshotJson("{\"schemaVersion\":1}");
    person.setFinalResultSnapshotHash("a".repeat(64));
    person.setResultFinalizedTime(LocalDateTime.now());
    trainingParticipantMapper.insert(person);

    TrainingParticipant loaded = trainingParticipantMapper.selectById(person.getTrainingParticipantId());
    assertThat(loaded.getFinalResultSnapshotJson()).contains("schemaVersion");
    assertThat(loaded.getFinalResultSnapshotHash()).hasSize(64);
    assertThat(loaded.getResultFinalizedTime()).isNotNull();
  }

  private TrainingParticipantContentResult projection(Long orgId, Long planId, Long participantId, Long contentId) {
    TrainingParticipantContentResult row = new TrainingParticipantContentResult();
    row.setOrganizationId(orgId);
    row.setTrainingPlanId(planId);
    row.setTrainingParticipantId(participantId);
    row.setPlanContentId(contentId);
    row.setContentType("COURSE");
    row.setExecutionId(1L);
    row.setResultStatus("PENDING");
    row.setEffectiveDurationSeconds(0);
    row.setResultRevision(0);
    return row;
  }

  private Long insertType(Long orgId, String code) {
    TrainingType type = new TrainingType();
    type.setOrganizationId(orgId);
    type.setTypeCode(code);
    type.setTypeName("类型");
    type.setStatus(1);
    type.setDescription("IT");
    trainingTypeMapper.insert(type);
    return type.getTrainingTypeId();
  }

  private TrainingPlan plan(Long orgId, Long typeId, String code) {
    TrainingPlan plan = new TrainingPlan();
    plan.setOrganizationId(orgId);
    plan.setPlanCode(code);
    plan.setPlanName("计划-" + code);
    plan.setTrainingTypeId(typeId);
    plan.setTrainingNature("INITIAL");
    plan.setStartTime(LocalDateTime.now());
    plan.setEndTime(LocalDateTime.now().plusDays(1));
    plan.setPublishStatus("DRAFT");
    plan.setStatus(1);
    return plan;
  }

  private TrainingParticipant participant(Long orgId, Long planId, Long userId) {
    TrainingParticipant p = new TrainingParticipant();
    p.setOrganizationId(orgId);
    p.setTrainingPlanId(planId);
    p.setUserId(userId);
    p.setParticipantOrgId(orgId);
    p.setJoinSource("ASSIGN");
    p.setJoinedTime(LocalDateTime.now());
    p.setParticipantStatus("PENDING");
    p.setOnlineHours(BigDecimal.ZERO);
    p.setOfflineHours(BigDecimal.ZERO);
    return p;
  }

  private TrainingOfflineSession session(Long orgId, Long planId, Long stageId, Long planContentId, String name) {
    TrainingOfflineSession session = new TrainingOfflineSession();
    session.setTrainingPlanId(planId);
    session.setPlanStageId(stageId);
    session.setPlanContentId(planContentId);
    session.setSessionName(name);
    session.setLocation("车间");
    session.setStartTime(LocalDateTime.now());
    session.setEndTime(LocalDateTime.now().plusHours(2));
    session.setSessionStatus("SCHEDULED");
    session.setStatus(1);
    return session;
  }
}
