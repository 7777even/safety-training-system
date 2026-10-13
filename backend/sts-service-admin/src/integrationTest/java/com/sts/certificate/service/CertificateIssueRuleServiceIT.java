package com.sts.certificate.service;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.certificate.dto.CertificateIssueRuleUpdateRequest;
import com.sts.certificate.dto.CertificateRuleSourceQuery;
import com.sts.certificate.entity.CertificateIssueRule;
import com.sts.certificate.entity.CertificateTemplate;
import com.sts.certificate.entity.CertificateTemplateVersion;
import com.sts.certificate.mapper.CertificateIssueRuleMapper;
import com.sts.certificate.mapper.CertificateTemplateMapper;
import com.sts.certificate.mapper.CertificateTemplateVersionMapper;
import com.sts.certificate.support.CertificateMapperItConfig;
import com.sts.common.api.PageRequest;
import com.sts.common.exception.BusinessException;
import com.sts.course.entity.Course;
import com.sts.course.mapper.CourseMapper;
import com.sts.exam.entity.Exam;
import com.sts.exam.mapper.ExamMapper;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.mapper.TrainingPlanMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = CertificateMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CertificateIssueRuleServiceIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private CertificateIssueRuleServiceImpl service;
  @Autowired
  private CertificateIssueRuleMapper ruleMapper;
  @Autowired
  private CertificateTemplateMapper templateMapper;
  @Autowired
  private CertificateTemplateVersionMapper versionMapper;
  @Autowired
  private CourseMapper courseMapper;
  @Autowired
  private ExamMapper examMapper;
  @Autowired
  private TrainingPlanMapper trainingPlanMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("三类统一候选仅返回当前租户可绑定来源")
  void candidates_filterTenantTypeAndStatus() {
    insertCourse("C-PUB", "PUBLISHED");
    insertCourse("C-DRAFT", "DRAFT");
    insertExam("E-ONLINE", "ONLINE", "PUBLISHED");
    insertExam("E-PRACTICE", "BANK_PRACTICE", "PUBLISHED");
    insertPlan("T-RUN", "RUNNING");
    insertPlan("T-END", "ENDED");
    Long other = otherTenantId();
    TenantContext.runWithTenant(other, () -> insertCourse("C-OTHER", "PUBLISHED"));

    var courses = service.pageSourceCandidates(page("COURSE"));
    var exams = service.pageSourceCandidates(page("EXAM"));
    var trainings = service.pageSourceCandidates(page("TRAINING"));

    assertThat(courses.list()).extracting("sourceCode").containsExactly("C-PUB");
    assertThat(exams.list()).extracting("sourceCode").containsExactly("E-ONLINE");
    assertThat(trainings.list()).extracting("sourceCode").containsExactly("T-RUN");
  }

  @Test
  @DisplayName("规则更新使用真实乐观锁且保留已发布模板约束")
  void update_usesOptimisticLock() {
    CertificateTemplate template = insertPublishedTemplate();
    Course course = insertCourse("C-RULE", "PUBLISHED");
    CertificateIssueRule rule = new CertificateIssueRule();
    rule.setRuleName("原规则");
    rule.setCertificateTemplateId(template.getCertificateTemplateId());
    rule.setSourceType("COURSE");
    rule.setSourceId(course.getCourseId());
    rule.setConditionType("PASS");
    rule.setIsAutoIssue(1);
    rule.setStatus(1);
    ruleMapper.insert(rule);
    CertificateIssueRule stored = ruleMapper.selectById(rule.getCertificateRuleId());
    CertificateIssueRuleUpdateRequest request = update(stored.getVersion(), template.getCertificateTemplateId(),
                                                       course.getCourseId());

    service.update(stored.getCertificateRuleId(), request);
    assertThat(ruleMapper.selectById(stored.getCertificateRuleId()).getRuleName()).isEqualTo("更新规则");
    assertThatThrownBy(() -> service.update(stored.getCertificateRuleId(), request))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT)
      .hasFieldOrPropertyWithValue("code", 9409);
  }

  private PageRequest<CertificateRuleSourceQuery> page(String type) {
    CertificateRuleSourceQuery query = new CertificateRuleSourceQuery();
    query.setSourceType(type);
    return PageRequest.of(1, 20, query);
  }

  private CertificateTemplate insertPublishedTemplate() {
    CertificateTemplate template = new CertificateTemplate();
    template.setTemplateCode("IT-RULE-TPL");
    template.setTemplateName("规则模板");
    template.setCurrentVersionNo(1);
    template.setStatus(1);
    template.setDescription("IT");
    templateMapper.insert(template);
    CertificateTemplateVersion version = new CertificateTemplateVersion();
    version.setCertificateTemplateId(template.getCertificateTemplateId());
    version.setVersionNo(1);
    version.setBackgroundFileId(1L);
    version.setWidthPx(800);
    version.setHeightPx(600);
    version.setPublishedStatus("PUBLISHED");
    versionMapper.insert(version);
    return template;
  }

  private Course insertCourse(String code, String publishStatus) {
    Course value = new Course();
    value.setCourseCode(code);
    value.setCourseName("课程-" + code);
    value.setContentMode("SINGLE");
    value.setCredit(BigDecimal.ONE);
    value.setPointMode("NONE");
    value.setPointValue(BigDecimal.ZERO);
    value.setSourceDownloadMode("NONE");
    value.setIsSpeedPlayAllowed(0);
    value.setIsStudyAnticheatEnabled(0);
    value.setIsStudyDescriptionEnabled(0);
    value.setIsWatermarkEnabled(0);
    value.setPublishStatus(publishStatus);
    value.setPublishedTime("PUBLISHED".equals(publishStatus) ? LocalDateTime.now() : null);
    value.setStatus(1);
    courseMapper.insert(value);
    return value;
  }

  private Exam insertExam(String code, String type, String publishStatus) {
    Exam value = new Exam();
    value.setExamCode(code);
    value.setExamName("考试-" + code);
    value.setExamType(type);
    value.setDurationSeconds(3600);
    value.setPassRatio(new BigDecimal("0.60"));
    value.setPassScore(new BigDecimal("60"));
    value.setCredit(BigDecimal.ONE);
    value.setQuestionOrderMode("TYPE");
    value.setMultiSelectScoring("ALL_OR_NOTHING");
    value.setIsNoticeEnabled(0);
    value.setPublishStatus(publishStatus);
    value.setPublishedTime(LocalDateTime.now());
    value.setStatus(1);
    examMapper.insert(value);
    return value;
  }

  private TrainingPlan insertPlan(String code, String publishStatus) {
    TrainingPlan value = new TrainingPlan();
    value.setPlanCode(code);
    value.setPlanName("培训-" + code);
    value.setTrainingTypeId(1L);
    value.setTrainingNature("INITIAL");
    value.setStartTime(LocalDateTime.now().plusDays(1));
    value.setEndTime(LocalDateTime.now().plusDays(2));
    value.setPublishStatus(publishStatus);
    value.setPublishedTime(LocalDateTime.now());
    value.setStatus(1);
    trainingPlanMapper.insert(value);
    return value;
  }

  private Long otherTenantId() {
    jdbcTemplate.update(
      "INSERT INTO sts_org_organization (org_code, org_name, org_type, status, description)"
        + " VALUES ('CERT-RULE-OTHER', '规则候选其他企业', 'ENTERPRISE', 1, '')");
    return jdbcTemplate.queryForObject(
      "SELECT organization_id FROM sts_org_organization WHERE org_code = 'CERT-RULE-OTHER'", Long.class);
  }

  private static CertificateIssueRuleUpdateRequest update(int version, Long templateId, Long sourceId) {
    CertificateIssueRuleUpdateRequest request = new CertificateIssueRuleUpdateRequest();
    request.setVersion(version);
    request.setRuleName("更新规则");
    request.setCertificateTemplateId(templateId);
    request.setSourceType("COURSE");
    request.setSourceId(sourceId);
    request.setConditionType("PASS");
    request.setIsAutoIssue(1);
    request.setStatus(1);
    return request;
  }
}
