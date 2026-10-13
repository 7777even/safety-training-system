package com.sts.training;
import com.sts.admin.support.*;
import com.sts.admin.mapper.*;
import com.sts.admin.entity.SysUser;
import com.sts.admin.storage.StorageService;
import com.sts.admin.tenant.TenantContext;
import com.sts.training.entity.*;
import com.sts.training.dto.*;
import com.sts.training.mapper.*;
import com.sts.training.service.*;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 真库验证行锁串行性、审批版本、租户及年度关联事务回滚。 */
@SpringBootTest(classes=TrainingPlanApprovalIT.Config.class)
class TrainingPlanApprovalIT extends AbstractMysqlIntegrationTest {
  @Configuration @EnableTransactionManagement @Import({MapperItConfig.class,TrainingPlanApprovalService.class,TrainingAnnualPlanService.class})
  @MapperScan("com.sts.training.mapper") static class Config {
    @Bean @Primary SysUserMapper approvalUsers() {return mock(SysUserMapper.class);}
    @Bean @Primary SysMenuMapper approvalMenus() {return mock(SysMenuMapper.class);}
    @Bean StorageService storage() {return mock(StorageService.class);}
  }
  @Autowired TrainingPlanMapper plans;
  @Autowired TrainingPlanApprovalMapper records;
  @Autowired TrainingPlanApprovalService approval;
  @Autowired TrainingAnnualPlanService annual;
  @Autowired SysUserMapper users;
  @Autowired SysMenuMapper menus;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;
  TrainingPlan seed() {
    var p=new TrainingPlan();p.setOrganizationId(TenantContext.requireTenantId());p.setPlanCode("approval-it-"+UUID.randomUUID());p.setPlanName("审批测试");p.setTrainingTypeId(1L);p.setTrainingNature("INITIAL");p.setPublishStatus("DRAFT");p.setStatus(1);p.setStartTime(LocalDateTime.now().plusDays(1));p.setEndTime(LocalDateTime.now().plusDays(2));plans.insert(p);return plans.selectById(p.getTrainingPlanId());
  }
  void authorizedApprover() {var u=new SysUser();u.setUserId(9002L);u.setOrganizationId(TenantContext.requireTenantId());u.setStatus(1);when(users.selectById(9002L)).thenReturn(u);when(menus.selectPermsByUserId(9002L)).thenReturn(List.of("training:plan:approve"));}
  void clean(Long id) {jdbc.update("DELETE FROM sts_training_plan_approval WHERE training_plan_id=?",id);jdbc.update("DELETE FROM sts_training_annual_plan_link WHERE training_plan_id=?",id);jdbc.update("DELETE FROM sts_training_plan WHERE training_plan_id=?",id);}
  @Test void realApprovalAndEditingShareOnePlanVersion() {
    var p=seed();authorizedApprover();try {
      approval.submit(p.getTrainingPlanId(),p.getVersion(),9002L,9001L);
      var tx=new TransactionTemplate(transactions);
      assertThatThrownBy(()->tx.execute(s->approval.lockForEdit(p.getTrainingPlanId()))).hasMessageContaining("待审批");
      approval.decide(p.getTrainingPlanId(),true,"同意",9002L);
      tx.executeWithoutResult(s->{var edit=approval.lockForEdit(p.getTrainingPlanId());approval.requireApproved(edit);edit.setPlanName("已修改");assertThat(plans.updateById(edit)).isEqualTo(1);});
      assertThatThrownBy(()->approval.requireApproved(plans.selectById(p.getTrainingPlanId()))).hasMessageContaining("审批");
      Long tenant=TenantContext.requireTenantId();TenantContext.setTenantId(tenant+10000);
      try {assertThat(plans.lockForApproval(p.getTrainingPlanId(),tenant+10000)).isNull();assertThat(records.selectList(null)).isEmpty();}
      finally {TenantContext.setTenantId(tenant);}
    } finally {clean(p.getTrainingPlanId());}
  }
  @Test void submissionWaitsForEditingTransactionAndRejectsStaleVersion() throws Exception {
    var p=seed();authorizedApprover();Long tenant=TenantContext.requireTenantId();var pool=Executors.newFixedThreadPool(2);
    var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var submitting=new CountDownLatch(1);
    try {
      var editor=pool.submit(()->TenantContext.callWithTenant(tenant,()->new TransactionTemplate(transactions).execute(s->{var edit=approval.lockForEdit(p.getTrainingPlanId());locked.countDown();try {if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("等待超时");}catch(InterruptedException e){throw new IllegalStateException(e);}edit.setPlanName("编辑完成");plans.updateById(edit);return null;})));
      assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
      var submit=pool.submit(()->TenantContext.callWithTenant(tenant,()->{submitting.countDown();approval.submit(p.getTrainingPlanId(),p.getVersion(),9002L,9001L);return null;}));
      assertThat(submitting.await(5,TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(()->submit.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
      release.countDown();editor.get(5,TimeUnit.SECONDS);
      assertThatThrownBy(()->submit.get(5,TimeUnit.SECONDS)).hasCauseInstanceOf(com.sts.common.exception.BusinessException.class);
      assertThat(approval.history(p.getTrainingPlanId())).isEmpty();
    } finally {release.countDown();pool.shutdownNow();pool.awaitTermination(5,TimeUnit.SECONDS);clean(p.getTrainingPlanId());}
  }
  @Test void annualAssociationAndPlanVersionRollBackTogether() {
    var p=seed();Long tenant=TenantContext.requireTenantId();String code="annual-it-"+UUID.randomUUID();
    jdbc.update("INSERT INTO us_sys_file_info(organization_id,file_name,bucket_name,object_name,file_size,file_url) VALUES(?,?,?, ?,?,?)",tenant,code+".pdf","test",code,1,"https://example.invalid/file");
    Long fileId=jdbc.queryForObject("SELECT file_info_id FROM us_sys_file_info WHERE object_name=?",Long.class,code);
    var req=new TrainingAnnualPlanRequest();req.setPlanYear(2026);req.setPlanName(code);req.setFileInfoId(fileId);Long annualId=annual.save(null,req);
    var bind=new TrainingAnnualPlanBindRequest();bind.setVersion(p.getVersion());bind.setAnnualPlanId(annualId);
    try {
      assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(s->{annual.bind(p.getTrainingPlanId(),bind);throw new IllegalStateException("回滚验证");})).hasMessage("回滚验证");
      assertThat(annual.linked(p.getTrainingPlanId())).isNull();assertThat(plans.selectById(p.getTrainingPlanId()).getVersion()).isEqualTo(p.getVersion());
      annual.bind(p.getTrainingPlanId(),bind);jdbc.update("UPDATE sts_training_annual_plan SET file_name='替换.pdf' WHERE annual_plan_id=?",annualId);
      assertThat(annual.linked(p.getTrainingPlanId()).getFileName()).isEqualTo(code+".pdf");
    } finally {clean(p.getTrainingPlanId());jdbc.update("DELETE FROM sts_training_annual_plan WHERE annual_plan_id=?",annualId);jdbc.update("DELETE FROM us_sys_file_info WHERE file_info_id=?",fileId);}
  }
}
