package com.sts.paper.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.paper.entity.Paper;
import com.sts.paper.entity.PaperVersion;
import com.sts.paper.support.PaperMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = PaperMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class PaperMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private PaperMapper paperMapper;
  @Autowired
  private PaperVersionMapper paperVersionMapper;
  @Autowired
  private OrganizationMapper organizationMapper;

  @Test
  @DisplayName("试卷编码唯一；试卷版本号唯一")
  void paperAndVersionUnique() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Paper paper = new Paper();
    paper.setOrganizationId(orgId);
    paper.setPaperCode("PAPER-IT-1");
    paper.setPaperName("卷");
    paper.setPaperType("FIXED");
    paper.setCurrentVersionNo(0);
    paper.setArchiveStatus("ACTIVE");
    paper.setStatus(1);
    paperMapper.insert(paper);

    Paper dup = new Paper();
    dup.setOrganizationId(orgId);
    dup.setPaperCode("PAPER-IT-1");
    dup.setPaperName("卷2");
    dup.setPaperType("FIXED");
    dup.setCurrentVersionNo(0);
    dup.setArchiveStatus("ACTIVE");
    dup.setStatus(1);
    assertThatThrownBy(() -> paperMapper.insert(dup)).isInstanceOf(DuplicateKeyException.class);

    PaperVersion v = new PaperVersion();
    v.setPaperId(paper.getPaperId());
    v.setVersionNo(1);
    v.setDurationSeconds(60);
    v.setTotalScore(new BigDecimal("10"));
    v.setPassRatio(new BigDecimal("0.6"));
    v.setPassScore(new BigDecimal("6"));
    v.setQuestionCount(1);
    v.setQuestionOrderMode("IMPORT");
    v.setMultiSelectScoring("ALL_OR_NOTHING");
    v.setDrawSettingMode("UNIFIED");
    v.setPublishedStatus("PUBLISHED");
    paperVersionMapper.insert(v);
    assertThat(v.getPaperVersionId()).isNotNull();

    PaperVersion vdup = new PaperVersion();
    vdup.setPaperId(paper.getPaperId());
    vdup.setVersionNo(1);
    vdup.setDurationSeconds(60);
    vdup.setTotalScore(new BigDecimal("10"));
    vdup.setPassRatio(new BigDecimal("0.6"));
    vdup.setPassScore(new BigDecimal("6"));
    vdup.setQuestionCount(1);
    vdup.setQuestionOrderMode("IMPORT");
    vdup.setMultiSelectScoring("ALL_OR_NOTHING");
    vdup.setDrawSettingMode("UNIFIED");
    vdup.setPublishedStatus("DRAFT");
    assertThatThrownBy(() -> paperVersionMapper.insert(vdup)).isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("random_seed 真实读写：随机卷落库种子，固定卷为 null")
  void randomSeedPersistsAndReadsBack() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Paper paper = new Paper();
    paper.setOrganizationId(orgId);
    paper.setPaperCode("PAPER-IT-SEED");
    paper.setPaperName("随机卷");
    paper.setPaperType("RANDOM");
    paper.setCurrentVersionNo(0);
    paper.setArchiveStatus("ACTIVE");
    paper.setStatus(1);
    paperMapper.insert(paper);

    PaperVersion seeded = paperVersion(paper.getPaperId(), 1);
    seeded.setRandomSeed(20260731L);
    paperVersionMapper.insert(seeded);

    PaperVersion unseeded = paperVersion(paper.getPaperId(), 2);
    unseeded.setRandomSeed(null);
    paperVersionMapper.insert(unseeded);

    assertThat(paperVersionMapper.selectById(seeded.getPaperVersionId()).getRandomSeed())
      .isEqualTo(20260731L);
    assertThat(paperVersionMapper.selectById(unseeded.getPaperVersionId()).getRandomSeed())
      .isNull();
  }

  private PaperVersion paperVersion(Long paperId, int versionNo) {
    PaperVersion v = new PaperVersion();
    v.setPaperId(paperId);
    v.setVersionNo(versionNo);
    v.setDurationSeconds(60);
    v.setTotalScore(new BigDecimal("10"));
    v.setPassRatio(new BigDecimal("0.6"));
    v.setPassScore(new BigDecimal("6"));
    v.setQuestionCount(1);
    v.setQuestionOrderMode("RANDOM");
    v.setMultiSelectScoring("ALL_OR_NOTHING");
    v.setDrawSettingMode("UNIFIED");
    v.setPublishedStatus("DRAFT");
    return v;
  }
}
