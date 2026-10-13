package com.sts.regulation.service;

import com.sts.common.exception.BusinessException;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.org.service.TenantDirectory;
import com.sts.regulation.export.Utf8CsvExporter;
import com.sts.regulator.mapper.RegulatorAggregateMapper;
import com.sts.regulator.service.RegulatorIdentityGuard;
import com.sts.regulator.vo.LearningLearnerRowVO;
import com.sts.regulator.vo.LearningOfferingRowVO;
import com.sts.regulator.vo.LearningStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 监管学习情况统计，园区与机构共用 Design 5.2 口径。
 */
@Service
@RequiredArgsConstructor
public class RegulationLearningStatsService {

  private final RegulatorIdentityGuard regulatorIdentityGuard;
  private final RegulatorAggregateMapper aggregateMapper;
  private final OrganizationMapper organizationMapper;

  public LearningStatsVO stats(Long organizationId) {
    regulatorIdentityGuard.requireRegulator();
    assertRegulatedOrg(organizationId);
    if (organizationId == null) {
      LearningStatsVO park = normalize(aggregateMapper.selectLearningStats(null));
      park.setInstitutions(normalizeList(aggregateMapper.selectLearningStatsByInstitution()));
      return park;
    }
    return normalize(aggregateMapper.selectLearningStats(organizationId));
  }

  public List<LearningLearnerRowVO> learners(Long organizationId) {
    regulatorIdentityGuard.requireRegulator();
    assertRegulatedOrg(organizationId);
    return nullToEmpty(aggregateMapper.selectLearningLearners(organizationId));
  }

  public List<LearningOfferingRowVO> offerings(Long organizationId) {
    regulatorIdentityGuard.requireRegulator();
    assertRegulatedOrg(organizationId);
    return nullToEmpty(aggregateMapper.selectLearningOfferings(organizationId));
  }

  public ResponseEntity<byte[]> export(Long organizationId) {
    LearningStatsVO stats = stats(organizationId);
    List<List<String>> data = new ArrayList<>();
    if (organizationId == null) {
      for (LearningStatsVO row : stats.getInstitutions()) {
        data.add(toCsvRow(row));
      }
    } else {
      data.add(toCsvRow(stats));
    }
    return Utf8CsvExporter.download(
      "regulation-learning-stats.csv",
      "organizationId=" + (organizationId == null ? "ALL" : organizationId),
      List.of("organizationId", "orgCode", "orgName", "orgType", "employeeAccountCount",
              "classSessionCount", "classLearnerCount", "participatingOfferingCount",
              "avgSessionsPerLearner", "participationRate"),
      data);
  }

  private void assertRegulatedOrg(Long organizationId) {
    if (organizationId == null) {
      return;
    }
    Organization org = organizationMapper.selectById(organizationId);
    if (org == null || !TenantDirectory.isRegulatedOrgType(org.getOrgType())
      || !Integer.valueOf(1).equals(org.getStatus())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 4063, "目标企业租户不存在或不可访问");
    }
  }

  private LearningStatsVO normalize(LearningStatsVO vo) {
    if (vo == null) {
      vo = new LearningStatsVO();
    }
    vo.setAvgSessionsPerLearner(scale(vo.getAvgSessionsPerLearner()));
    vo.setParticipationRate(scale(vo.getParticipationRate()));
    return vo;
  }

  private List<LearningStatsVO> normalizeList(List<LearningStatsVO> list) {
    List<LearningStatsVO> result = new ArrayList<>();
    for (LearningStatsVO item : nullToEmpty(list)) {
      result.add(normalize(item));
    }
    return result;
  }

  private static BigDecimal scale(BigDecimal value) {
    if (value == null) {
      return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }
    return value.setScale(4, RoundingMode.HALF_UP);
  }

  private static List<String> toCsvRow(LearningStatsVO row) {
    return List.of(
      row.getOrganizationId() == null ? "" : String.valueOf(row.getOrganizationId()),
      nvl(row.getOrgCode()),
      nvl(row.getOrgName()),
      nvl(row.getOrgType()),
      String.valueOf(row.getEmployeeAccountCount()),
      String.valueOf(row.getClassSessionCount()),
      String.valueOf(row.getClassLearnerCount()),
      String.valueOf(row.getParticipatingOfferingCount()),
      String.valueOf(row.getAvgSessionsPerLearner()),
      String.valueOf(row.getParticipationRate())
    );
  }

  private static <T> List<T> nullToEmpty(List<T> list) {
    return list == null ? List.of() : list;
  }

  private static String nvl(String value) {
    return value == null ? "" : value;
  }
}
