package com.sts.training.service;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.storage.StorageService;
import com.sts.admin.tenant.TenantContext;
import com.sts.training.dto.*;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.*;
/** 年度关联必须冻结文件并参与计划版本审批。 */
class TrainingAnnualPlanServiceTest {
  TrainingAnnualPlanMapper annuals=mock(TrainingAnnualPlanMapper.class);
  TrainingAnnualPlanLinkMapper links=mock(TrainingAnnualPlanLinkMapper.class);
  TrainingPlanMapper plans=mock(TrainingPlanMapper.class);
  SysFileInfoMapper files=mock(SysFileInfoMapper.class);
  TrainingPlanApprovalService approval=mock(TrainingPlanApprovalService.class);
  TrainingAnnualPlanService service=new TrainingAnnualPlanService(annuals,links,plans,files,mock(StorageService.class),approval);
  TrainingPlan plan;TrainingAnnualPlan annual;
  @BeforeEach void setup() {
    TenantContext.setTenantId(1L);plan=new TrainingPlan();plan.setTrainingPlanId(5L);plan.setOrganizationId(1L);plan.setVersion(2);plan.setPublishStatus("DRAFT");
    when(approval.lockForEdit(5L)).thenReturn(plan);when(plans.selectById(5L)).thenReturn(plan);when(plans.updateById(any(TrainingPlan.class))).thenReturn(1);
    annual=new TrainingAnnualPlan();annual.setAnnualPlanId(10L);annual.setOrganizationId(1L);annual.setPlanYear(2026);annual.setPlanName("年度计划");annual.setFileInfoId(8L);annual.setFileName("原文件.pdf");annual.setVersion(0);when(annuals.selectById(10L)).thenReturn(annual);
    var file=new SysFileInfo();file.setFileInfoId(8L);file.setObjectName("tenant/annual.pdf");file.setFileName("原文件.pdf");when(files.selectById(8L)).thenReturn(file);
  }
  @AfterEach void clear() {TenantContext.clear();}
  @Test void annualBindFreezesFileAndBumpsPlan() {
    var req=new TrainingAnnualPlanBindRequest();req.setVersion(2);req.setAnnualPlanId(10L);service.bind(5L,req);
    var cap=org.mockito.ArgumentCaptor.forClass(TrainingAnnualPlanLink.class);verify(links).insert(cap.capture());annual.setFileInfoId(99L);
    assertThat(cap.getValue().getFileInfoId()).isEqualTo(8L);assertThat(cap.getValue().getFileName()).isEqualTo("原文件.pdf");verify(plans).updateById(plan);
  }
  @Test void staleVersionRejectsBeforeCreatingLink() {
    var req=new TrainingAnnualPlanBindRequest();req.setVersion(1);req.setAnnualPlanId(10L);
    assertThatThrownBy(()->service.bind(5L,req)).hasMessageContaining("版本");verify(links,never()).insert(any(TrainingAnnualPlanLink.class));
  }
  @Test void annualFromOtherTenantIsRejected() {
    annual.setOrganizationId(2L);var req=new TrainingAnnualPlanBindRequest();req.setVersion(2);req.setAnnualPlanId(10L);
    assertThatThrownBy(()->service.bind(5L,req)).hasMessageContaining("不存在");
  }
  @Test void invisibleFileCannotBeLinked() {
    when(files.selectById(8L)).thenReturn(null);var req=new TrainingAnnualPlanBindRequest();req.setVersion(2);req.setAnnualPlanId(10L);
    assertThatThrownBy(()->service.bind(5L,req)).hasMessageContaining("不可见");
  }
}
