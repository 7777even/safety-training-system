package com.sts.training;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.storage.StorageService;
import com.sts.admin.tenant.TenantContext;
import com.sts.training.support.TrainingMixedPlanItConfig;
import com.sts.training.service.*;
import com.sts.training.mapper.*;
import com.sts.training.entity.*;
import com.sts.training.dto.*;
import com.sts.person.dto.PersonSelectionRequest;
import com.sts.person.service.PersonSelectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 真库验证四项变更版本推进、独立快照和完整变更失败时的事务回滚。 */
@SpringBootTest(classes=TrainingPlanChangeIT.Config.class)
class TrainingPlanChangeIT extends AbstractMysqlIntegrationTest {
  // 与应用默认类代理一致，变更服务复用实现类的审批生效方法。
  @Configuration @org.springframework.transaction.annotation.EnableTransactionManagement(proxyTargetClass=true) @Import({TrainingMixedPlanItConfig.class,TrainingPlanChangeService.class,TrainingPlanChangeSafety.class,TrainingAnnualPlanService.class})
  static class Config { @Bean StorageService storage() {return mock(StorageService.class);} }
  @Autowired TrainingPlanMapper plans;
  @Autowired TrainingTypeMapper types;
  @Autowired TrainingPlanChangeMapper changes;
  @Autowired TrainingPlanServiceImpl service;
  @Autowired TrainingPlanChangeService workflow;
  @Autowired PersonSelectionService people;
  @Autowired JdbcTemplate jdbc;
  TrainingPlan seed(String status) {
    var p=new TrainingPlan();p.setOrganizationId(TenantContext.requireTenantId());p.setPlanCode("change-it-"+UUID.randomUUID());p.setPlanName("原计划");p.setTrainingTypeId(987654321L);p.setTrainingNature("INITIAL");p.setPublishStatus(status);p.setStatus(1);p.setStartTime(LocalDateTime.now().plusDays(1));p.setEndTime(LocalDateTime.now().plusDays(2));p.setPublishedTime(LocalDateTime.now().minusDays(1));p.setLocation("原地点");plans.insert(p);return plans.selectById(p.getTrainingPlanId());
  }
  TrainingPlanChangeRequest basic(TrainingPlan p) {
    var req=new TrainingPlanChangeRequest();req.setBaseVersion(p.getVersion());req.setChangeType("BASIC");var b=new TrainingPlanBasicChangeRequest();b.setPlanName("批准后名称");b.setLocation("新地点");req.setBasic(b);return req;
  }
  @Test @Transactional void basicChangesKeepHistoricalFieldsAndOptimisticVersion() {
    var p=seed("RUNNING");var time=plans.selectById(p.getTrainingPlanId()).getPublishedTime();
    service.applyApprovedChange(plans.lockForApproval(p.getTrainingPlanId(),TenantContext.requireTenantId()),basic(p));
    var after=plans.selectById(p.getTrainingPlanId());assertThat(after.getVersion()).isEqualTo(p.getVersion()+1);assertThat(after.getPublishStatus()).isEqualTo("RUNNING");assertThat(after.getPublishedTime()).isEqualTo(time);assertThat(after.getTrainingTypeId()).isEqualTo(p.getTrainingTypeId());assertThat(after.getPlanName()).isEqualTo("批准后名称");
  }
  @Test @Transactional void independentDraftSnapshotsDoNotEditEffectivePlanAndRejectStaleVersion() {
    var p=seed("PUBLISHED");Long id=workflow.save(p.getTrainingPlanId(),null,basic(p),901L);
    var row=changes.selectById(id);assertThat(row.getChangeStatus()).isEqualTo("DRAFT");assertThat(row.getBeforeSnapshotJson()).contains("原计划");assertThat(row.getBeforeParticipantsJson()).isEqualTo("[]");assertThat(plans.selectById(p.getTrainingPlanId()).getPlanName()).isEqualTo("原计划");
    var edited=plans.selectById(p.getTrainingPlanId());edited.setPlanName("另一版本");plans.updateById(edited);
    assertThatThrownBy(()->workflow.save(p.getTrainingPlanId(),null,basic(p),901L)).hasMessageContaining("版本冲突");
  }
  @Test @Transactional void fullChangeReplacesSyllabusAndRosterAtomicallyWithoutChangingPublicationTime() {
    var type=new TrainingType();type.setOrganizationId(TenantContext.requireTenantId());type.setTypeCode("change-"+UUID.randomUUID());type.setTypeName("变更培训类型");type.setStatus(1);type.setDescription("");type.setInitialHours(java.math.BigDecimal.ONE);type.setReviewHours(java.math.BigDecimal.ONE);types.insert(type);
    var p=seed("PUBLISHED");p.setTrainingTypeId(type.getTrainingTypeId());plans.updateById(p);p=plans.selectById(p.getTrainingPlanId());var published=p.getPublishedTime();
    var full=new TrainingPlanFullChangeRequest();var base=new TrainingPlanUpdateRequest();base.setVersion(p.getVersion());base.setPlanName("完整变更后");base.setTrainingTypeId(type.getTrainingTypeId());base.setTrainingNature("INITIAL");base.setStartTime(p.getStartTime());base.setEndTime(p.getEndTime());base.setStatus(1);full.setPlan(base);
    var content=new TrainingPlanContentRequest();content.setContentType("OFFLINE");content.setContentName("线下内容");content.setIsRequired(1);content.setRequiredHours(java.math.BigDecimal.ONE);content.setSortOrder(1);
    var config=new TrainingContentExecutionConfig();config.setTeacherUserId(902L);config.setLocation("教室");config.setStartTime(p.getStartTime());config.setEndTime(p.getStartTime().plusHours(1));config.setPassScore(java.math.BigDecimal.valueOf(60));content.setExecutionConfig(config);
    var stage=new TrainingPlanStageRequest();stage.setStageName("新阶段");stage.setStageType("OFFLINE");stage.setSortOrder(1);stage.setContents(List.of(content));full.setStages(List.of(stage));full.setForms(List.of());full.setParticipantUserIds(List.of(902L));when(people.resolve(any(PersonSelectionRequest.class))).thenReturn(List.of(902L));
    var req=new TrainingPlanChangeRequest();req.setBaseVersion(p.getVersion());req.setChangeType("FULL");req.setFull(full);service.applyApprovedChange(plans.lockForApproval(p.getTrainingPlanId(),TenantContext.requireTenantId()),req);
    var after=service.get(p.getTrainingPlanId());assertThat(after.getPublishStatus()).isEqualTo("PUBLISHED");assertThat(after.getPublishedTime()).isEqualTo(published);assertThat(after.getPlanName()).isEqualTo("完整变更后");assertThat(after.getStages()).hasSize(1);assertThat(after.getStages().getFirst().getContents()).hasSize(1);assertThat(service.listParticipants(p.getTrainingPlanId())).extracting(com.sts.training.vo.TrainingParticipantVO::getUserId).containsExactly(902L);
  }

  @Test void fullChangeFailureRollsBackTemporaryDraftAndKeepsOriginalArrangement() {
    var p=seed("PUBLISHED");var req=new TrainingPlanChangeRequest();req.setBaseVersion(p.getVersion());req.setChangeType("FULL");var full=new TrainingPlanFullChangeRequest();var base=new TrainingPlanUpdateRequest();base.setTrainingTypeId(p.getTrainingTypeId());base.setPlanName("不得生效");base.setTrainingNature("INITIAL");base.setVersion(p.getVersion());base.setStartTime(p.getStartTime());base.setEndTime(p.getEndTime());base.setStatus(1);full.setPlan(base);full.setStages(List.of());full.setForms(List.of());full.setParticipantUserIds(List.of(902L));req.setFull(full);
    // 不存在的培训类型将在临时DRAFT之后触发复用编辑校验异常，整笔事务必须恢复。
    try {assertThatThrownBy(()->service.applyApprovedChange(plans.selectById(p.getTrainingPlanId()),req)).isInstanceOf(com.sts.common.exception.BusinessException.class);
    var after=plans.selectById(p.getTrainingPlanId());assertThat(after.getPublishStatus()).isEqualTo("PUBLISHED");assertThat(after.getVersion()).isEqualTo(p.getVersion());assertThat(after.getPlanName()).isEqualTo("原计划");
    } finally {jdbc.update("DELETE FROM sts_training_plan WHERE training_plan_id=?",p.getTrainingPlanId());}
  }
}
