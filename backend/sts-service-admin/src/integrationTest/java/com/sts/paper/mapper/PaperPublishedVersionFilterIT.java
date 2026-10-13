package com.sts.paper.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.paper.dto.PaperQuery;
import com.sts.paper.entity.Paper;
import com.sts.paper.entity.PaperVersion;
import com.sts.paper.service.PaperServiceImpl;
import com.sts.paper.support.PaperMapperItConfig;
import com.sts.paper.vo.PaperVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证「是否存在可用发布版本」的 exists / notExists 子查询。
 *
 * <p>考试新建页要问的是「这份试卷能不能直接用」，草稿覆盖发布版后该语义仍须正确。
 */
@SpringBootTest(classes = {PaperMapperItConfig.class,
  PaperPublishedVersionFilterIT.ServiceConfig.class},
  webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class PaperPublishedVersionFilterIT extends AbstractMysqlIntegrationTest {

  @TestConfiguration
  @Import(PaperServiceImpl.class)
  static class ServiceConfig {
  }

  @Autowired
  private PaperServiceImpl paperService;
  @Autowired
  private PaperMapper paperMapper;
  @Autowired
  private PaperVersionMapper paperVersionMapper;
  @Autowired
  private OrganizationMapper organizationMapper;

  @Test
  @DisplayName("hasPublishedVersion：区分有无可用发布版本，且草稿覆盖后语义不变")
  void filterByHasPublishedVersion() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Long publishedPaperId = insertPaper(orgId, "P-PUB");
    insertVersion(publishedPaperId, 1, "PUBLISHED");
    // 发布后又存了草稿：试卷仍然「有可用发布版本」，不能因当前版本是草稿就被排除
    insertVersion(publishedPaperId, 2, "DRAFT");

    Long draftOnlyPaperId = insertPaper(orgId, "P-DRAFT");
    insertVersion(draftOnlyPaperId, 1, "DRAFT");

    PageResult<PaperVO> usable = page(orgId, true);
    assertThat(usable.list()).extracting(PaperVO::getPaperId).containsExactly(publishedPaperId);
    assertThat(usable.total()).isEqualTo(1);

    PageResult<PaperVO> notUsable = page(orgId, false);
    assertThat(notUsable.list()).extracting(PaperVO::getPaperId).contains(draftOnlyPaperId);
    assertThat(notUsable.list()).extracting(PaperVO::getPaperId).doesNotContain(publishedPaperId);
  }

  @Test
  @DisplayName("hasPublishedVersion 为 null 时不施加筛选")
  void nullFilterReturnsAll() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Long a = insertPaper(orgId, "P-ALL-1");
    insertVersion(a, 1, "PUBLISHED");
    Long b = insertPaper(orgId, "P-ALL-2");
    insertVersion(b, 1, "DRAFT");

    assertThat(page(orgId, null).list()).extracting(PaperVO::getPaperId).contains(a, b);
  }

  private PageResult<PaperVO> page(Long orgId, Boolean hasPublishedVersion) {
    // 机构范围由租户插件在 SQL 层注入，查询条件不再携带 organizationId
    PaperQuery query = new PaperQuery();
    query.setHasPublishedVersion(hasPublishedVersion);
    PageRequest<PaperQuery> request = new PageRequest<>();
    request.setPageNum(1);
    request.setPageSize(50);
    request.setQuery(query);
    return paperService.page(request);
  }

  private Long insertPaper(Long orgId, String code) {
    Paper paper = new Paper();
    paper.setOrganizationId(orgId);
    paper.setPaperCode(code);
    paper.setPaperName(code + "-名称");
    paper.setPaperType("FIXED");
    paper.setCurrentVersionNo(0);
    paper.setArchiveStatus("ACTIVE");
    paper.setStatus(1);
    paperMapper.insert(paper);
    return paper.getPaperId();
  }

  private void insertVersion(Long paperId, int versionNo, String publishedStatus) {
    PaperVersion v = new PaperVersion();
    v.setPaperId(paperId);
    v.setVersionNo(versionNo);
    v.setDurationSeconds(3600);
    v.setTotalScore(new BigDecimal("100"));
    v.setPassRatio(new BigDecimal("0.6"));
    v.setPassScore(new BigDecimal("60"));
    v.setQuestionCount(1);
    v.setQuestionOrderMode("IMPORT");
    v.setMultiSelectScoring("ALL_OR_NOTHING");
    v.setDrawSettingMode("UNIFIED");
    v.setPublishedStatus(publishedStatus);
    paperVersionMapper.insert(v);
  }
}
