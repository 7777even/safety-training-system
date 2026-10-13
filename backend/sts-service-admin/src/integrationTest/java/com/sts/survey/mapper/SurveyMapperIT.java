package com.sts.survey.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.survey.entity.Survey;
import com.sts.survey.entity.SurveyInstance;
import com.sts.survey.entity.SurveyVersion;
import com.sts.survey.support.SurveyMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = SurveyMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class SurveyMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private SurveyMapper surveyMapper;
  @Autowired
  private SurveyVersionMapper surveyVersionMapper;
  @Autowired
  private SurveyInstanceMapper instanceMapper;
  @Autowired
  private OrganizationMapper organizationMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("uk_org_code_deleted：逻辑删除后可复用问卷编码")
  void uniqueKey_allowsReuseAfterLogicDelete() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Survey first = survey(orgId, "IT-SURVEY-1");
    surveyMapper.insert(first);
    Long id = first.getSurveyId();
    jdbcTemplate.update("UPDATE sts_survey_survey SET deleted = ? WHERE survey_id = ?", id, id);

    Survey second = survey(orgId, "IT-SURVEY-1");
    surveyMapper.insert(second);
    assertThat(second.getSurveyId()).isNotNull();
  }

  @Test
  @DisplayName("uk_survey_version 唯一")
  void versionUnique() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Survey survey = survey(orgId, "IT-SURVEY-2");
    surveyMapper.insert(survey);

    SurveyVersion version = new SurveyVersion();
    version.setSurveyId(survey.getSurveyId());
    version.setVersionNo(1);
    version.setContentSnapshotJson("{}");
    version.setSnapshotHash("abc");
    version.setPublishedBy(0L);
    version.setPublishedTime(java.time.LocalDateTime.now());
    surveyVersionMapper.insert(version);

    SurveyVersion dup = new SurveyVersion();
    dup.setSurveyId(survey.getSurveyId());
    dup.setVersionNo(1);
    dup.setContentSnapshotJson("{}");
    dup.setSnapshotHash("def");
    dup.setPublishedBy(0L);
    dup.setPublishedTime(java.time.LocalDateTime.now());
    assertThatThrownBy(() -> surveyVersionMapper.insert(dup)).isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("问卷投放可持久化用途和结果可见方式")
  void instancePersistsPurposeAndVisibility() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Survey survey = survey(orgId, "IT-SURVEY-3");
    surveyMapper.insert(survey);
    SurveyVersion version = new SurveyVersion();
    version.setSurveyId(survey.getSurveyId());
    version.setVersionNo(1);
    version.setContentSnapshotJson("{}");
    version.setSnapshotHash("purpose");
    version.setPublishedBy(0L);
    version.setPublishedTime(java.time.LocalDateTime.now());
    surveyVersionMapper.insert(version);

    SurveyInstance instance = new SurveyInstance();
    instance.setOrganizationId(orgId);
    instance.setInstanceCode("IT-INST-1");
    instance.setInstanceName("投放IT");
    instance.setSurveyId(survey.getSurveyId());
    instance.setSurveyVersionId(version.getSurveyVersionId());
    instance.setPurpose("ORG_EVALUATION");
    instance.setResultVisibility("SUBMITTER_AFTER_SUBMIT");
    instance.setSourceType("STANDALONE");
    instance.setSourceId(0L);
    instance.setPublishMode("IMMEDIATE");
    instance.setIsAnonymousSnapshot(0);
    instance.setInstanceStatus("PUBLISHED");
    instance.setStatus(1);
    instanceMapper.insert(instance);

    SurveyInstance loaded = instanceMapper.selectById(instance.getSurveyInstanceId());
    assertThat(loaded.getPurpose()).isEqualTo("ORG_EVALUATION");
    assertThat(loaded.getResultVisibility()).isEqualTo("SUBMITTER_AFTER_SUBMIT");
  }

  private Survey survey(Long orgId, String code) {
    Survey s = new Survey();
    s.setOrganizationId(orgId);
    s.setSurveyCode(code);
    s.setSurveyName("IT问卷");
    s.setSurveyType("GENERAL");
    s.setIsAnonymous(0);
    s.setContentStatus("DRAFT");
    s.setStatus(1);
    return s;
  }
}
