package com.sts.regulation.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.regulation.entity.RegulationWarning;
import com.sts.regulation.entity.RegulationWarningRule;
import com.sts.regulation.support.RegulationMapperItConfig;
import com.sts.regulation.util.WarningDedupUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = RegulationMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class RegulationWarningMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private RegulationWarningRuleMapper ruleMapper;
  @Autowired
  private RegulationWarningMapper warningMapper;
  @Autowired
  private OrganizationMapper organizationMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("uk_org_dedup_deleted：逻辑删除后可复用 dedup_key")
  void dedupKey_allowsReuseAfterLogicDelete() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    RegulationWarningRule rule = rule(orgId, "IT-RULE-1");
    ruleMapper.insert(rule);

    String dedup = WarningDedupUtil.dedupKey(rule.getWarningRuleId(), orgId, 100L, "2026-Q1");
    RegulationWarning first = warning(orgId, rule.getWarningRuleId(), orgId, dedup);
    warningMapper.insert(first);
    Long id = first.getWarningId();
    jdbcTemplate.update("UPDATE sts_regulation_warning SET deleted = ? WHERE warning_id = ?", id, id);

    RegulationWarning second = warning(orgId, rule.getWarningRuleId(), orgId, dedup);
    warningMapper.insert(second);
    assertThat(second.getWarningId()).isNotNull();
  }

  @Test
  @DisplayName("uk_org_dedup_deleted：同 dedup 重复插入失败")
  void dedupKey_rejectsDuplicate() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    RegulationWarningRule rule = rule(orgId, "IT-RULE-2");
    ruleMapper.insert(rule);

    String dedup = WarningDedupUtil.dedupKey(rule.getWarningRuleId(), orgId, null, "2026-Q2");
    warningMapper.insert(warning(orgId, rule.getWarningRuleId(), orgId, dedup));
    assertThatThrownBy(() -> warningMapper.insert(warning(orgId, rule.getWarningRuleId(), orgId, dedup)))
      .isInstanceOf(DuplicateKeyException.class);
  }

  private static RegulationWarningRule rule(Long orgId, String code) {
    RegulationWarningRule rule = new RegulationWarningRule();
    rule.setOrganizationId(orgId);
    rule.setRuleCode(code);
    rule.setRuleName("IT规则");
    rule.setWarningType("PLAN_HOURS");
    rule.setWarningLevel("WARN");
    rule.setStatus(1);
    rule.setDescription("IT");
    return rule;
  }

  private static RegulationWarning warning(Long orgId, Long ruleId, Long regulatedOrgId, String dedup) {
    RegulationWarning w = new RegulationWarning();
    w.setOrganizationId(orgId);
    w.setWarningRuleId(ruleId);
    w.setRegulatedOrgId(regulatedOrgId);
    w.setDetectionCycle("2026-Q1");
    w.setDedupKey(dedup);
    w.setWarningLevel("WARN");
    w.setWarningTitle("IT预警");
    w.setWarningStatus("OPEN");
    w.setDetectedTime(LocalDateTime.now());
    return w;
  }
}
