package com.sts.training.service;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.admin.mapper.*;
import com.sts.admin.entity.SysUser;
import com.sts.admin.tenant.TenantContext;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.*;
/** 首次审批门禁的状态、权限和版本测试。 */
class TrainingPlanApprovalServiceTest {
  TrainingPlanMapper plans = mock(TrainingPlanMapper.class);
  TrainingPlanApprovalMapper approvals = mock(TrainingPlanApprovalMapper.class);
  SysUserMapper users = mock(SysUserMapper.class);
  SysMenuMapper menus = mock(SysMenuMapper.class);
  TrainingPlanApprovalService service = new TrainingPlanApprovalService(plans, approvals, users, menus);
  TrainingPlan plan;
  @BeforeEach void setup() {
    TenantContext.setTenantId(1L);
    plan = new TrainingPlan(); plan.setOrganizationId(1L); plan.setTrainingPlanId(5L); plan.setVersion(2); plan.setPublishStatus("DRAFT");
    when(plans.lockForApproval(5L, 1L)).thenReturn(plan); when(plans.selectById(5L)).thenReturn(plan);
  }
  @AfterEach void cleanup() { TenantContext.clear(); }
  @Test void selfApprovalIsRejected() { assertThatThrownBy(() -> service.submit(5L, 2, 8L, 8L)).hasMessageContaining("不同"); verify(approvals, never()).insert(any(TrainingPlanApproval.class)); }
  @Test void approverMustBeEnabledTenantMemberWithPermission() {
    var u = new SysUser(); u.setUserId(9L); u.setOrganizationId(2L); u.setStatus(1); when(users.selectById(9L)).thenReturn(u);
    assertThatThrownBy(() -> service.submit(5L, 2, 9L, 8L)).hasMessageContaining("审批权限");
    u.setOrganizationId(1L); when(menus.selectPermsByUserId(9L)).thenReturn(List.of());
    assertThatThrownBy(() -> service.submit(5L, 2, 9L, 8L)).hasMessageContaining("审批权限");
  }
  @Test void pendingBlocksEveryEditAndPublication() {
    when(approvals.selectList(any())).thenReturn(List.of(record("PENDING", 2)));
    assertThatThrownBy(() -> service.lockForEdit(5L)).hasMessageContaining("待审批");
    assertThatThrownBy(() -> service.requireApproved(plan)).hasMessageContaining("审批");
  }
  @Test void approvalIsBoundToExactPlanVersion() {
    when(approvals.selectList(any())).thenReturn(List.of(record("APPROVED", 1)));
    assertThatThrownBy(() -> service.requireApproved(plan)).hasMessageContaining("审批");
    when(approvals.selectList(any())).thenReturn(List.of(record("APPROVED", 2)));
    service.requireApproved(plan);
  }
  @Test void wrongApproverAndStalePlanCannotDecide() {
    var row=record("PENDING",2); row.setApproverUserId(9L); when(approvals.selectList(any())).thenReturn(List.of(row));
    assertThatThrownBy(() -> service.decide(5L, true, "通过", 8L)).hasMessageContaining("指定审批人");
    row.setPlanVersion(1);
    assertThatThrownBy(() -> service.decide(5L, true, "通过", 9L)).hasMessageContaining("版本");
    verify(approvals, never()).updateById(any(TrainingPlanApproval.class));
  }
  @Test void crossTenantPlanIsRejected() {
    plan.setOrganizationId(2L);
    assertThatThrownBy(() -> service.lockForEdit(5L)).hasMessageContaining("不存在");
  }
  @Test void rejectAndResubmitPreservesHistory() {
    var u=new SysUser();u.setUserId(9L);u.setOrganizationId(1L);u.setStatus(1);when(users.selectById(9L)).thenReturn(u);
    when(menus.selectPermsByUserId(9L)).thenReturn(List.of("training:plan:approve"));
    var previous=record("REJECTED",2);when(approvals.selectList(any())).thenReturn(List.of(previous));
    service.submit(5L,2,9L,8L);
    verify(approvals).insert(argThat((TrainingPlanApproval r)->r.getSubmitterUserId().equals(8L) && r.getApproverUserId().equals(9L) && r.getApprovalStatus().equals("PENDING")));
    assertThat(previous.getApprovalStatus()).isEqualTo("REJECTED");
  }
  @Test void decisionRechecksPermissionAndRequiresRejectionReason() {
    var row=record("PENDING",2);row.setApproverUserId(9L);row.setSubmitterUserId(8L);when(approvals.selectList(any())).thenReturn(List.of(row));
    assertThatThrownBy(()->service.decide(5L,true,"",9L)).hasMessageContaining("权限");
    var u=new SysUser();u.setUserId(9L);u.setOrganizationId(1L);u.setStatus(1);when(users.selectById(9L)).thenReturn(u);
    when(menus.selectPermsByUserId(9L)).thenReturn(List.of("training:plan:approve"));
    assertThatThrownBy(()->service.decide(5L,false," ",9L)).hasMessageContaining("意见");
    when(approvals.updateById(any(TrainingPlanApproval.class))).thenReturn(1);
    service.decide(5L,false,"补齐年度依据",9L);assertThat(row.getApprovalStatus()).isEqualTo("REJECTED");
  }
  @Test void approvedVersionEditIsAllowedAndThenCannotPublish() {
    when(approvals.selectList(any())).thenReturn(List.of(record("APPROVED",2)));
    service.lockForEdit(5L);plan.setVersion(3);
    assertThatThrownBy(()->service.requireApproved(plan)).hasMessageContaining("审批");
  }
  TrainingPlanApproval record(String state,int version) { var r=new TrainingPlanApproval(); r.setApprovalStatus(state); r.setPlanVersion(version); return r; }
}
