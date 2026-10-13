package com.sts.question.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.question.dto.QuestionQuery;
import com.sts.question.entity.Question;
import com.sts.question.entity.QuestionBank;
import com.sts.question.entity.QuestionVersion;
import com.sts.question.service.QuestionServiceImpl;
import com.sts.question.support.QuestionMapperItConfig;
import com.sts.question.vo.QuestionVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证按发布状态筛选题目的 exists 子查询。
 *
 * <p>筛选依赖真实 SQL 与逻辑删除的交互，按 CLAUDE.md §5.2.8 不在零 DB 用 mock 硬凑。
 */
@SpringBootTest(classes = {QuestionMapperItConfig.class,
  QuestionPublishedStatusFilterIT.ServiceConfig.class},
  webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class QuestionPublishedStatusFilterIT extends AbstractMysqlIntegrationTest {

  @TestConfiguration
  @Import(QuestionServiceImpl.class)
  static class ServiceConfig {
  }

  @Autowired
  private QuestionServiceImpl questionService;
  @Autowired
  private QuestionMapper questionMapper;
  @Autowired
  private QuestionBankMapper questionBankMapper;
  @Autowired
  private QuestionVersionMapper questionVersionMapper;
  @Autowired
  private OrganizationMapper organizationMapper;

  @Test
  @DisplayName("publishedStatus 筛选：只返回有对应版本的题目，且分页总数一致")
  void filterByPublishedStatus() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Long bankId = insertBank(orgId);
    Long publishedId = insertQuestion(orgId, bankId, "Q-PUB", "PUBLISHED");
    Long draftId = insertQuestion(orgId, bankId, "Q-DRAFT", "DRAFT");

    PageResult<QuestionVO> published = page(orgId, bankId, "PUBLISHED");
    assertThat(published.list()).extracting(QuestionVO::getQuestionId).containsExactly(publishedId);
    assertThat(published.total()).isEqualTo(1);

    PageResult<QuestionVO> draft = page(orgId, bankId, "DRAFT");
    assertThat(draft.list()).extracting(QuestionVO::getQuestionId).containsExactly(draftId);
    assertThat(draft.total()).isEqualTo(1);

    PageResult<QuestionVO> all = page(orgId, bankId, null);
    assertThat(all.total()).isEqualTo(2);
  }

  @Test
  @DisplayName("publishedStatus 筛选：题目有多个版本时不重复返回")
  void filterDoesNotDuplicateMultiVersionQuestion() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Long bankId = insertBank(orgId);
    Long questionId = insertQuestion(orgId, bankId, "Q-MULTI", "PUBLISHED");
    // 同一题目再追加一个已发布版本：EXISTS 子查询必须仍只返回一行，JOIN 写法会重复
    insertVersion(questionId, 2, "PUBLISHED");

    PageResult<QuestionVO> result = page(orgId, bankId, "PUBLISHED");

    assertThat(result.list()).extracting(QuestionVO::getQuestionId).containsExactly(questionId);
    assertThat(result.total()).isEqualTo(1);
  }

  private PageResult<QuestionVO> page(Long orgId, Long bankId, String publishedStatus) {
    // 机构范围由租户插件在 SQL 层注入，查询条件不再携带 organizationId
    QuestionQuery query = new QuestionQuery();
    query.setQuestionBankId(bankId);
    query.setPublishedStatus(publishedStatus);
    PageRequest<QuestionQuery> request = new PageRequest<>();
    request.setPageNum(1);
    request.setPageSize(10);
    request.setQuery(query);
    return questionService.page(request);
  }

  private Long insertBank(Long orgId) {
    QuestionBank bank = new QuestionBank();
    bank.setOrganizationId(orgId);
    bank.setBankCode("BANK-FILTER-IT");
    bank.setBankName("筛选题库");
    bank.setDifficulty("NORMAL");
    bank.setQuestionOrderMode("IMPORT");
    bank.setArchiveStatus("ACTIVE");
    bank.setStatus(1);
    questionBankMapper.insert(bank);
    return bank.getQuestionBankId();
  }

  private Long insertQuestion(Long orgId, Long bankId, String code, String publishedStatus) {
    Question question = new Question();
    question.setOrganizationId(orgId);
    question.setQuestionBankId(bankId);
    question.setQuestionCode(code);
    question.setCurrentVersionNo(1);
    question.setArchiveStatus("ACTIVE");
    question.setStatus(1);
    questionMapper.insert(question);

    insertVersion(question.getQuestionId(), 1, publishedStatus);
    return question.getQuestionId();
  }

  private void insertVersion(Long questionId, int versionNo, String publishedStatus) {
    QuestionVersion version = new QuestionVersion();
    version.setQuestionId(questionId);
    version.setVersionNo(versionNo);
    version.setQuestionType("SINGLE");
    version.setStem("题干");
    version.setDifficulty("NORMAL");
    version.setGradingMode("AUTO");
    version.setIsUnorderedAnswerAllowed(0);
    version.setIsCaseIgnored(0);
    version.setIsFullHalfWidthIgnored(0);
    version.setPublishedStatus(publishedStatus);
    questionVersionMapper.insert(version);
  }
}
