package com.sts.training.service;
import com.sts.admin.mapper.*;
import com.sts.admin.entity.SysUser;
import com.sts.admin.tenant.TenantContext;
import com.sts.training.dto.*;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.port.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.*;
/** 变更审批前原计划不变，生效时校验版本和安全同步边界。 */
class TrainingPlanChangeServiceTest {
  TrainingPlanMapper plans=mock(TrainingPlanMapper.class);TrainingPlanChangeMapper changes=mock(TrainingPlanChangeMapper.class);
  TrainingPlanApprovalService approval=mock(TrainingPlanApprovalService.class);
  TrainingPlanServiceImpl planService=mock(TrainingPlanServiceImpl.class);
  TrainingPlanChangeSafety safety=mock(TrainingPlanChangeSafety.class);
  SysUserMapper users=mock(SysUserMapper.class);
  TrainingPlanChangeService service=new TrainingPlanChangeService(plans,changes,approval,planService,safety,new ObjectMapper().findAndRegisterModules(),mock(TrainingAnnualPlanService.class));
  TrainingPlan plan;
  @BeforeEach void setup() {TenantContext.setTenantId(1L);plan=new TrainingPlan();plan.setTrainingPlanId(5L);plan.setOrganizationId(1L);plan.setPublishStatus("RUNNING");plan.setVersion(2);when(approval.lock(5L)).thenReturn(plan);when(plans.selectById(5L)).thenReturn(plan);}
  @AfterEach void clear() {TenantContext.clear();}
  TrainingPlanChangeRequest request(String type) {var r=new TrainingPlanChangeRequest();r.setBaseVersion(2);r.setChangeType(type);var basic=new TrainingPlanBasicChangeRequest();basic.setPlanName("新名称");r.setBasic(basic);return r;}
  @Test void pendingChangeNeverMutatesEffectivePlan() {
    when(planService.get(5L)).thenReturn(new com.sts.training.vo.TrainingPlanVO());
    service.save(5L,null,request("BASIC"),8L);verify(plans,never()).updateById(any(TrainingPlan.class));verify(planService,never()).applyApprovedChange(any(),any());
  }
  @Test void runningPlanCannotSaveFullChange() {assertThatThrownBy(()->service.save(5L,null,request("FULL"),8L)).hasMessageContaining("四项");}
  @Test void endedPlanCannotChange() {plan.setPublishStatus("ENDED");assertThatThrownBy(()->service.save(5L,null,request("BASIC"),8L)).hasMessageContaining("结束");}
  @Test void staleDraftCannotOverwriteNewPlan() {var r=request("BASIC");r.setBaseVersion(1);assertThatThrownBy(()->service.save(5L,null,r,8L)).hasMessageContaining("版本");}
  @Test void decisionRechecksBaseVersionAndDoesNotApplyStaleRequest() throws Exception {
    var row=new TrainingPlanChange();row.setOrganizationId(1L);row.setTrainingPlanId(5L);row.setChangeId(7L);row.setBaseVersion(1);row.setSubmitterUserId(8L);row.setApproverUserId(9L);row.setChangeStatus("PENDING");when(changes.selectById(7L)).thenReturn(row);
    assertThatThrownBy(()->service.decide(5L,7L,true,"同意",9L)).hasMessageContaining("版本");verify(planService,never()).applyApprovedChange(any(),any());
  }
  @Test void differentPlanChangeCannotBeApproved() {
    var r=new TrainingPlanChange();r.setOrganizationId(2L);r.setTrainingPlanId(5L);when(changes.selectById(7L)).thenReturn(r);
    assertThatThrownBy(()->service.decide(5L,7L,true,"同意",9L)).hasMessageContaining("不存在");
  }
  @Test void approvedBasicChangeAppliesOnlyAfterDesignatedDecision() throws Exception {
    var r=new TrainingPlanChange();r.setOrganizationId(1L);r.setTrainingPlanId(5L);r.setBaseVersion(2);r.setSubmitterUserId(8L);r.setApproverUserId(9L);r.setChangeStatus("PENDING");r.setRequestJson(new ObjectMapper().writeValueAsString(request("BASIC")));when(changes.selectById(7L)).thenReturn(r);
    when(changes.updateById(any(TrainingPlanChange.class))).thenReturn(1);
    assertThatThrownBy(()->service.decide(5L,7L,true,"同意",10L)).hasMessageContaining("指定");
    verify(planService,never()).applyApprovedChange(any(),any());
    service.decide(5L,7L,true,"同意",9L);verify(planService).applyApprovedChange(eq(plan),any());assertThat(r.getChangeStatus()).isEqualTo("APPROVED");
  }
  @Test void rejectedChangeDoesNotTouchOriginalEvenWhenPlanAdvanced() {
    var r=new TrainingPlanChange();r.setOrganizationId(1L);r.setTrainingPlanId(5L);r.setBaseVersion(1);r.setSubmitterUserId(8L);r.setApproverUserId(9L);r.setChangeStatus("PENDING");when(changes.selectById(7L)).thenReturn(r);when(changes.updateById(any(TrainingPlanChange.class))).thenReturn(1);
    service.decide(5L,7L,false,"版本过期",9L);verify(planService,never()).applyApprovedChange(any(),any());assertThat(r.getChangeStatus()).isEqualTo("REJECTED");
  }
  @Test void pendingDraftCannotBeEditedBySubmitter() {
    var r=new TrainingPlanChange();r.setOrganizationId(1L);r.setTrainingPlanId(5L);r.setSubmitterUserId(8L);r.setChangeStatus("PENDING");when(changes.selectById(7L)).thenReturn(r);
    assertThatThrownBy(()->service.save(5L,7L,request("BASIC"),8L)).hasMessageContaining("草稿");verify(changes,never()).updateById(any(TrainingPlanChange.class));
  }
}
