package com.sts.certificate.service;

import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.storage.GeneratedFileStore;
import com.sts.certificate.dto.CertificateIssueRequest;
import com.sts.certificate.entity.CertificateCertificate;
import com.sts.certificate.entity.CertificateTemplate;
import com.sts.certificate.entity.CertificateTemplateVersion;
import com.sts.certificate.mapper.CertificateCertificateMapper;
import com.sts.certificate.mapper.CertificateTemplateElementMapper;
import com.sts.certificate.mapper.CertificateTemplateMapper;
import com.sts.certificate.mapper.CertificateTemplateVersionMapper;
import com.sts.common.api.PageRequest;
import com.sts.course.entity.Course;
import com.sts.course.mapper.CourseMapper;
import com.sts.exam.entity.Exam;
import com.sts.exam.mapper.ExamMapper;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.mapper.TrainingPlanMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CertificateServiceImplTest {

  private CertificateCertificateMapper certificateMapper;
  private CertificateTemplateMapper templateMapper;
  private CertificateTemplateVersionMapper templateVersionMapper;
  private SysUserMapper userMapper;
  private GeneratedFileStore generatedFileStore;
  private CertificateImageRenderer renderer;
  private CertificateServiceImpl service;
  private CourseMapper courseMapper;
  private ExamMapper examMapper;
  private TrainingPlanMapper trainingPlanMapper;

  @BeforeEach
  void setUp() {
    certificateMapper = mock(CertificateCertificateMapper.class);
    templateMapper = mock(CertificateTemplateMapper.class);
    templateVersionMapper = mock(CertificateTemplateVersionMapper.class);
    userMapper = mock(SysUserMapper.class);
    generatedFileStore = mock(GeneratedFileStore.class);
    renderer = mock(CertificateImageRenderer.class);
    courseMapper = mock(CourseMapper.class);
    examMapper = mock(ExamMapper.class);
    trainingPlanMapper = mock(TrainingPlanMapper.class);
    when(renderer.render(any(), anyList(), anyMap())).thenReturn(new byte[]{1, 2, 3, 4});
    service = new CertificateServiceImpl(
      certificateMapper,
      templateMapper,
      templateVersionMapper,
      mock(CertificateTemplateElementMapper.class),
      userMapper,
      new ObjectMapper(),
      renderer,
      generatedFileStore,
      courseMapper,
      examMapper,
      trainingPlanMapper);
  }

  @Test
  @DisplayName("证书列表按本页来源批量回填名称，来源缺失保持为空")
  void pageMine_fillsSourceNames() {
    CertificateCertificate courseCert = certificate(1L, "COURSE", 10L);
    CertificateCertificate examCert = certificate(2L, "EXAM", 20L);
    CertificateCertificate trainingCert = certificate(3L, "TRAINING", 30L);
    CertificateCertificate missingCert = certificate(4L, "COURSE", 11L);
    Page<CertificateCertificate> page = new Page<>(1, 20);
    page.setRecords(List.of(courseCert, examCert, trainingCert, missingCert));
    page.setTotal(4);
    when(certificateMapper.selectPage(any(), any())).thenReturn(page);
    Course course = new Course();
    course.setCourseId(10L);
    course.setCourseName("有限空间课程");
    Exam exam = new Exam();
    exam.setExamId(20L);
    exam.setExamName("安全理论考试");
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(30L);
    plan.setPlanName("新员工培训");
    when(courseMapper.selectBatchIds(any())).thenReturn(List.of(course));
    when(examMapper.selectBatchIds(any())).thenReturn(List.of(exam));
    when(trainingPlanMapper.selectBatchIds(any())).thenReturn(List.of(plan));

    var result = service.pageMine(7L, PageRequest.of(1, 20, null));

    assertThat(result.list()).extracting("sourceName")
      .containsExactly("有限空间课程", "安全理论考试", "新员工培训", null);
    verify(courseMapper).selectBatchIds(any());
    verify(examMapper).selectBatchIds(any());
    verify(trainingPlanMapper).selectBatchIds(any());
  }

  private static CertificateCertificate certificate(Long id, String sourceType, Long sourceId) {
    CertificateCertificate value = new CertificateCertificate();
    value.setCertificateId(id);
    value.setSourceType(sourceType);
    value.setSourceId(sourceId);
    return value;
  }

  @Test
  @DisplayName("issue：幂等返回已有证书且不重复生成文件")
  void issue_idempotent() {
    CertificateCertificate existing = new CertificateCertificate();
    existing.setCertificateId(1L);
    existing.setCertificateNo("CERT-EXIST");
    existing.setCertificateStatus("ISSUED");
    existing.setCertificateFileId(88L);
    when(certificateMapper.selectOne(any())).thenReturn(existing);

    CertificateIssueRequest req = issueRequest();

    var vo = service.issue(req);
    assertThat(vo.getCertificateNo()).isEqualTo("CERT-EXIST");
    assertThat(vo.getCertificateFileId()).isEqualTo(88L);
    verify(certificateMapper, never()).insert(any(CertificateCertificate.class));
    verify(generatedFileStore, never()).store(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("issue：首次签发写入真实文件 ID")
  void issue_storesRealFile() {
    when(certificateMapper.selectOne(any())).thenReturn(null);
    SysUser holder = new SysUser();
    holder.setUserId(100L);
    holder.setUserName("张三");
    when(userMapper.selectById(100L)).thenReturn(holder);
    CertificateTemplate template = new CertificateTemplate();
    template.setCertificateTemplateId(2L);
    template.setStatus(1);
    when(templateMapper.selectById(2L)).thenReturn(template);
    CertificateTemplateVersion version = new CertificateTemplateVersion();
    version.setTemplateVersionId(3L);
    version.setCertificateTemplateId(2L);
    version.setWidthPx(400);
    version.setHeightPx(300);
    when(templateVersionMapper.selectById(3L)).thenReturn(version);
    when(generatedFileStore.store(any(), any(), eq("certificate"), eq("image/png"), eq("png"))).thenReturn(99L);
    when(certificateMapper.insert(any(CertificateCertificate.class))).thenAnswer(inv -> {
      CertificateCertificate cert = inv.getArgument(0);
      cert.setCertificateId(7L);
      return 1;
    });

    var vo = service.issue(issueRequest());
    assertThat(vo.getCertificateFileId()).isEqualTo(99L);
    assertThat(vo.getCertificateFileId()).isNotEqualTo(1L);
    verify(generatedFileStore).store(any(), any(), eq("certificate"), eq("image/png"), eq("png"));
  }

  @Test
  @DisplayName("issue：证书变量 SOURCE_TYPE 按中文口径渲染，证书上不得出现库内英文编码")
  void issue_rendersSourceTypeInChinese() {
    stubAvailableTemplate();

    service.issue(issueRequest());

    assertThat(renderedVariables()).containsEntry("SOURCE_TYPE", "培训计划");
  }

  @Test
  @DisplayName("preview：未签发的示例预览与正式签发口径一致，来源类型同样为中文")
  void preview_rendersSourceTypeInChinese() {
    stubAvailableTemplate();

    service.preview(2L);

    assertThat(renderedVariables()).containsEntry("SOURCE_TYPE", "培训计划");
  }

  /** 模板、已发布版本与持有人都可用，issue 与 preview 共用同一份桩。 */
  private void stubAvailableTemplate() {
    when(certificateMapper.selectOne(any())).thenReturn(null);
    SysUser holder = new SysUser();
    holder.setUserId(100L);
    holder.setUserName("张三");
    when(userMapper.selectById(100L)).thenReturn(holder);
    CertificateTemplate template = new CertificateTemplate();
    template.setCertificateTemplateId(2L);
    template.setStatus(1);
    template.setCurrentVersionNo(1);
    when(templateMapper.selectById(2L)).thenReturn(template);
    CertificateTemplateVersion version = new CertificateTemplateVersion();
    version.setTemplateVersionId(3L);
    version.setCertificateTemplateId(2L);
    version.setWidthPx(400);
    version.setHeightPx(300);
    version.setPublishedStatus("PUBLISHED");
    when(templateVersionMapper.selectById(3L)).thenReturn(version);
    when(templateVersionMapper.selectOne(any())).thenReturn(version);
    when(generatedFileStore.store(any(), any(), eq("certificate"), eq("image/png"), eq("png"))).thenReturn(99L);
  }

  /** 取渲染器实际收到的变量表：证书上印什么由它决定。 */
  @SuppressWarnings("unchecked")
  private Map<String, String> renderedVariables() {
    ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
    verify(renderer).render(any(), anyList(), captor.capture());
    return captor.getValue();
  }

  private static CertificateIssueRequest issueRequest() {
    CertificateIssueRequest req = new CertificateIssueRequest();
    req.setCertificateTemplateId(2L);
    req.setTemplateVersionId(3L);
    req.setHolderUserId(100L);
    req.setSourceType("TRAINING");
    req.setSourceId(10L);
    return req;
  }
}
