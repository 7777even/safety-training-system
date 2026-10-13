package com.sts.certificate.service;

import com.sts.certificate.dto.CertificateIssueRuleCreateRequest;
import com.sts.certificate.dto.CertificateIssueRuleUpdateRequest;
import com.sts.certificate.dto.CertificateRuleSourceQuery;
import com.sts.certificate.entity.CertificateIssueRule;
import com.sts.certificate.entity.CertificateTemplate;
import com.sts.certificate.entity.CertificateTemplateVersion;
import com.sts.certificate.mapper.CertificateIssueRuleMapper;
import com.sts.certificate.mapper.CertificateTemplateMapper;
import com.sts.certificate.mapper.CertificateTemplateVersionMapper;
import com.sts.common.api.PageRequest;
import com.sts.common.exception.BusinessException;
import com.sts.course.entity.Course;
import com.sts.course.mapper.CourseMapper;
import com.sts.exam.mapper.ExamMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CertificateIssueRuleServiceImplTest {

  private CertificateIssueRuleMapper ruleMapper;
  private CertificateTemplateMapper templateMapper;
  private CertificateTemplateVersionMapper versionMapper;
  private CourseMapper courseMapper;
  private ExamMapper examMapper;
  private TrainingPlanMapper trainingPlanMapper;
  private CertificateIssueRuleServiceImpl service;

  @BeforeEach
  void setUp() {
    ruleMapper = mock(CertificateIssueRuleMapper.class);
    templateMapper = mock(CertificateTemplateMapper.class);
    versionMapper = mock(CertificateTemplateVersionMapper.class);
    courseMapper = mock(CourseMapper.class);
    examMapper = mock(ExamMapper.class);
    trainingPlanMapper = mock(TrainingPlanMapper.class);
    // 默认「同来源无其他启用规则」，冲突场景在各用例内单独打桩
    when(ruleMapper.selectCount(any())).thenReturn(0L);
    service = new CertificateIssueRuleServiceImpl(
      ruleMapper, templateMapper, versionMapper, courseMapper, examMapper, trainingPlanMapper);
  }

  @Test
  @DisplayName("get：规则详情返回模板名称、来源名称、自动签发与版本")
  void get_returnsReliableReadback() {
    CertificateIssueRule rule = rule();
    rule.setVersion(4);
    when(ruleMapper.selectById(3L)).thenReturn(rule);
    when(templateMapper.selectById(1L)).thenReturn(template());
    Course course = new Course();
    course.setCourseId(20L);
    course.setCourseName("有限空间作业课程");
    when(courseMapper.selectById(20L)).thenReturn(course);

    var result = service.get(3L);

    assertThat(result.getCertificateTemplateName()).isEqualTo("安全培训合格证");
    assertThat(result.getSourceName()).isEqualTo("有限空间作业课程");
    assertThat(result.getIsAutoIssue()).isEqualTo(1);
    assertThat(result.getVersion()).isEqualTo(4);
  }

  @Test
  @DisplayName("update：已发布模板和已发布课程可更新规则")
  void update_acceptsBindableSource() {
    CertificateIssueRule stored = rule();
    stored.setVersion(2);
    when(ruleMapper.selectById(3L)).thenReturn(stored);
    when(templateMapper.selectById(1L)).thenReturn(template());
    CertificateTemplateVersion published = new CertificateTemplateVersion();
    published.setCertificateTemplateId(1L);
    published.setVersionNo(2);
    published.setPublishedStatus("PUBLISHED");
    when(versionMapper.selectOne(any())).thenReturn(published);
    Course course = new Course();
    course.setCourseId(20L);
    course.setPublishStatus("PUBLISHED");
    course.setStatus(1);
    when(courseMapper.selectById(20L)).thenReturn(course);
    when(ruleMapper.updateById(any(CertificateIssueRule.class))).thenReturn(1);

    service.update(3L, updateRequest(2));

    verify(ruleMapper).updateById(stored);
    assertThat(stored.getRuleName()).isEqualTo("新版规则");
  }

  @Test
  @DisplayName("create：已发布模板和可绑定来源通过同一校验")
  void create_acceptsBindableSource() {
    when(templateMapper.selectById(1L)).thenReturn(template());
    CertificateTemplateVersion published = new CertificateTemplateVersion();
    published.setCertificateTemplateId(1L);
    published.setVersionNo(2);
    published.setPublishedStatus("PUBLISHED");
    when(versionMapper.selectOne(any())).thenReturn(published);
    Course course = new Course();
    course.setCourseId(20L);
    course.setPublishStatus("PUBLISHED");
    course.setStatus(1);
    when(courseMapper.selectById(20L)).thenReturn(course);
    when(ruleMapper.insert(any(CertificateIssueRule.class))).thenAnswer(invocation -> {
      CertificateIssueRule value = invocation.getArgument(0);
      value.setCertificateRuleId(3L);
      return 1;
    });

    Long result = service.create(createRequest());

    assertThat(result).isEqualTo(3L);
    verify(ruleMapper).insert(any(CertificateIssueRule.class));
  }

  @Test
  @DisplayName("candidates：课程候选只通过分页查询返回统一模型")
  void candidates_returnsUnifiedCourseModel() {
    Course course = new Course();
    course.setCourseId(20L);
    course.setCourseCode("COURSE-20");
    course.setCourseName("有限空间作业课程");
    course.setPublishStatus("PUBLISHED");
    var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Course>(1, 20, 1);
    page.setRecords(List.of(course));
    when(courseMapper.selectPage(any(), any())).thenReturn(page);
    CertificateRuleSourceQuery query = new CertificateRuleSourceQuery();
    query.setSourceType("COURSE");
    query.setKeyword("有限空间");

    var result = service.pageSourceCandidates(PageRequest.of(1, 20, query));

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.list()).singleElement().satisfies(item -> {
      assertThat(item.getSourceType()).isEqualTo("COURSE");
      assertThat(item.getSourceId()).isEqualTo(20L);
      assertThat(item.getSourceCode()).isEqualTo("COURSE-20");
      assertThat(item.getSourceName()).isEqualTo("有限空间作业课程");
    });
  }

  @Test
  @DisplayName("create：同一来源已存在启用规则时拒绝新增启用规则")
  void create_rejectsSecondActiveRuleForSameSource() {
    stubBindableCourse();
    when(ruleMapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> service.create(createRequest()))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 9418);

    verify(ruleMapper, never()).insert(any(CertificateIssueRule.class));
  }

  @Test
  @DisplayName("create：同来源已有启用规则时仍允许保存禁用态规则")
  void create_allowsDisabledRuleForSourceWithActiveRule() {
    stubBindableCourse();
    when(ruleMapper.selectCount(any())).thenReturn(1L);
    when(ruleMapper.insert(any(CertificateIssueRule.class))).thenAnswer(invocation -> {
      CertificateIssueRule value = invocation.getArgument(0);
      value.setCertificateRuleId(9L);
      return 1;
    });
    CertificateIssueRuleCreateRequest request = createRequest();
    request.setStatus(0);

    assertThat(service.create(request)).isEqualTo(9L);

    verify(ruleMapper).insert(any(CertificateIssueRule.class));
  }

  @Test
  @DisplayName("update：把规则改为启用时，同来源已有其他启用规则则拒绝")
  void update_rejectsActivatingWhenAnotherActiveRuleExists() {
    CertificateIssueRule stored = rule();
    stored.setVersion(2);
    stored.setStatus(0);
    when(ruleMapper.selectById(3L)).thenReturn(stored);
    stubBindableCourse();
    when(ruleMapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> service.update(3L, updateRequest(2)))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 9418);

    verify(ruleMapper, never()).updateById(any(CertificateIssueRule.class));
  }

  @Test
  @DisplayName("update：同来源仅自身启用时允许保存，校验需排除自身")
  void update_allowsActivatingOwnRule() {
    CertificateIssueRule stored = rule();
    stored.setVersion(2);
    when(ruleMapper.selectById(3L)).thenReturn(stored);
    stubBindableCourse();
    when(ruleMapper.selectCount(any())).thenReturn(0L);
    when(ruleMapper.updateById(any(CertificateIssueRule.class))).thenReturn(1);

    service.update(3L, updateRequest(2));

    verify(ruleMapper).updateById(stored);
    verify(ruleMapper, times(1)).selectCount(any());
  }

  /** 打桩一个「已发布且启用」的课程来源，供规则绑定校验通过。 */
  private void stubBindableCourse() {
    when(templateMapper.selectById(1L)).thenReturn(template());
    CertificateTemplateVersion published = new CertificateTemplateVersion();
    published.setCertificateTemplateId(1L);
    published.setVersionNo(2);
    published.setPublishedStatus("PUBLISHED");
    when(versionMapper.selectOne(any())).thenReturn(published);
    Course course = new Course();
    course.setCourseId(20L);
    course.setPublishStatus("PUBLISHED");
    course.setStatus(1);
    when(courseMapper.selectById(20L)).thenReturn(course);
  }

  private static CertificateIssueRule rule() {
    CertificateIssueRule rule = new CertificateIssueRule();
    rule.setCertificateRuleId(3L);
    rule.setRuleName("课程规则");
    rule.setCertificateTemplateId(1L);
    rule.setSourceType("COURSE");
    rule.setSourceId(20L);
    rule.setConditionType("PASS");
    rule.setIsAutoIssue(1);
    rule.setStatus(1);
    return rule;
  }

  private static CertificateTemplate template() {
    CertificateTemplate template = new CertificateTemplate();
    template.setCertificateTemplateId(1L);
    template.setTemplateName("安全培训合格证");
    template.setCurrentVersionNo(2);
    template.setStatus(1);
    return template;
  }

  private static CertificateIssueRuleUpdateRequest updateRequest(int version) {
    CertificateIssueRuleUpdateRequest request = new CertificateIssueRuleUpdateRequest();
    request.setVersion(version);
    request.setRuleName("新版规则");
    request.setCertificateTemplateId(1L);
    request.setSourceType("COURSE");
    request.setSourceId(20L);
    request.setConditionType("PASS");
    request.setIsAutoIssue(1);
    request.setStatus(1);
    return request;
  }

  private static CertificateIssueRuleCreateRequest createRequest() {
    CertificateIssueRuleCreateRequest request = new CertificateIssueRuleCreateRequest();
    request.setRuleName("课程规则");
    request.setCertificateTemplateId(1L);
    request.setSourceType("COURSE");
    request.setSourceId(20L);
    request.setConditionType("PASS");
    request.setIsAutoIssue(1);
    request.setStatus(1);
    return request;
  }
}
