package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.training.dto.TrainingStandardRuleRequest;
import com.sts.training.dto.TrainingStandardRulesReplaceRequest;
import com.sts.training.entity.TrainingStandard;
import com.sts.training.entity.TrainingStandardRule;
import com.sts.training.mapper.TrainingStandardMapper;
import com.sts.training.mapper.TrainingStandardRuleMapper;
import com.sts.training.mapper.TrainingTemplateMapper;
import com.sts.training.mapper.TrainingTypeMapper;
import com.sts.training.support.TrainingMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {TrainingMapperItConfig.class, TrainingStandardServiceImplIT.ServiceConfig.class},
  webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TrainingStandardServiceImplIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private TrainingStandardServiceImpl service;
  @Autowired
  private TrainingStandardMapper standardMapper;
  @Autowired
  private TrainingStandardRuleMapper ruleMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("replaceRules：旧规则软删后仅保留本次提交的全量规则")
  void replaceRules_softDeletesExistingRules_andKeepsOnlySubmittedRules() {
    Long organizationId = demoTenantId();
    Long trainingTypeId = jdbcTemplate.queryForObject(
      "SELECT training_type_id FROM sts_training_type WHERE organization_id = ? AND deleted = 0 LIMIT 1",
      Long.class, organizationId);

    TrainingStandard standard = new TrainingStandard();
    standard.setOrganizationId(organizationId);
    standard.setStandardCode("IT-STD-" + UUID.randomUUID());
    standard.setStandardName("规则替换集成测试标准");
    standard.setTrainingTypeId(trainingTypeId);
    standard.setStandardVersion("v1");
    standard.setPublishStatus("DRAFT");
    standard.setStatus(1);
    standardMapper.insert(standard);

    TrainingStandardRule existing = new TrainingStandardRule();
    existing.setTrainingStandardId(standard.getTrainingStandardId());
    existing.setRuleType("TOTAL_HOURS");
    existing.setSortOrder(1);
    existing.setStatus(1);
    existing.setDescription("旧规则");
    ruleMapper.insert(existing);

    TrainingStandardRuleRequest submitted = new TrainingStandardRuleRequest();
    submitted.setRuleType("ONLINE_RATIO");
    submitted.setSortOrder(1);
    submitted.setStatus(1);
    submitted.setDescription("新规则");
    TrainingStandardRulesReplaceRequest request = new TrainingStandardRulesReplaceRequest();
    request.setVersion(0);
    request.setRules(List.of(submitted));

    service.replaceRules(standard.getTrainingStandardId(), request);

    List<TrainingStandardRule> activeRules = ruleMapper.selectList(
      new LambdaQueryWrapper<TrainingStandardRule>()
        .eq(TrainingStandardRule::getTrainingStandardId, standard.getTrainingStandardId()));
    assertThat(activeRules).singleElement()
      .satisfies(rule -> {
        assertThat(rule.getRuleType()).isEqualTo("ONLINE_RATIO");
        assertThat(rule.getDescription()).isEqualTo("新规则");
      });
    Long deleted = jdbcTemplate.queryForObject(
      "SELECT deleted FROM sts_training_standard_rule WHERE standard_rule_id = ?",
      Long.class, existing.getStandardRuleId());
    assertThat(deleted).isEqualTo(existing.getStandardRuleId());
  }

  @TestConfiguration
  static class ServiceConfig {

    @Bean
    TrainingResourceAccessHelper trainingResourceAccessHelper(TrainingTypeMapper typeMapper,
                                                              TrainingStandardMapper standardMapper,
                                                              TrainingTemplateMapper templateMapper) {
      return new TrainingResourceAccessHelper(typeMapper, standardMapper, templateMapper);
    }

    @Bean
    TrainingStandardServiceImpl trainingStandardService(TrainingStandardMapper standardMapper,
                                                        TrainingStandardRuleMapper ruleMapper,
                                                        TrainingResourceAccessHelper resourceAccessHelper) {
      return new TrainingStandardServiceImpl(standardMapper, ruleMapper, resourceAccessHelper);
    }
  }
}
