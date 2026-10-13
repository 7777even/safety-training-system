package com.sts.evaluation.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.evaluation.entity.EvaluationCalculationRun;
import com.sts.evaluation.support.EvaluationCalculationMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = EvaluationCalculationMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class EvaluationCalculationMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private EvaluationCalculationRunMapper calculationRunMapper;
  @Autowired
  private OrganizationMapper organizationMapper;

  @Test
  @DisplayName("uk_participant_official_deleted：正式结果唯一")
  void officialMarkerUnique() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    EvaluationCalculationRun first = run(orgId, 1L, 1);
    calculationRunMapper.insert(first);

    EvaluationCalculationRun dup = run(orgId, 1L, 1);
    dup.setRunNo(2);
    assertThatThrownBy(() -> calculationRunMapper.insert(dup)).isInstanceOf(DuplicateKeyException.class);
  }

  private EvaluationCalculationRun run(Long orgId, Long participantId, int official) {
    EvaluationCalculationRun run = new EvaluationCalculationRun();
    run.setOrganizationId(orgId);
    run.setTrainingPlanId(1L);
    run.setTrainingParticipantId(participantId);
    run.setFormVersionId(1L);
    run.setRunNo(1);
    run.setCalculationStatus("CONFIRMED");
    run.setOfficialMarker(official);
    run.setAdjustmentScore(BigDecimal.ZERO);
    run.setCompositeScore(new BigDecimal("80"));
    run.setRuleSnapshotJson("{}");
    run.setCalculationHash("hash");
    run.setCalculatedBy(0L);
    run.setCalculatedTime(LocalDateTime.now());
    return run;
  }
}
