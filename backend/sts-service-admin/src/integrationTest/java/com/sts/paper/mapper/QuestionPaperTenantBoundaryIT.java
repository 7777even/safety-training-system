package com.sts.paper.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.paper.entity.Paper;
import com.sts.paper.support.PaperMapperItConfig;
import com.sts.question.entity.Question;
import com.sts.question.entity.QuestionBank;
import com.sts.question.mapper.QuestionBankMapper;
import com.sts.question.mapper.QuestionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 题库、题目与试卷的租户边界（Testcontainers 真 MySQL）。
 * <p>
 * 客户端已不能再传 organizationId，剩下的越权面是「猜 ID 直查」和「组卷时引用别家题库」。
 * 这两条都只能靠 SQL 层的租户条件挡住，零 DB 测试证明不了，必须在真库上验证。
 */
@SpringBootTest(classes = PaperMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class QuestionPaperTenantBoundaryIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private QuestionBankMapper questionBankMapper;
  @Autowired
  private QuestionMapper questionMapper;
  @Autowired
  private PaperMapper paperMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Long otherTenantId() {
    jdbcTemplate.update(
      "INSERT INTO sts_org_organization (org_code, org_name, org_type, status, description)"
        + " VALUES ('QP-OTHER', '另一家企业', 'ENTERPRISE', 1, '')");
    return jdbcTemplate.queryForObject(
      "SELECT organization_id FROM sts_org_organization WHERE org_code = 'QP-OTHER'", Long.class);
  }

  private Long insertBank(String code) {
    QuestionBank bank = new QuestionBank();
    bank.setBankCode(code);
    bank.setBankName("题库-" + code);
    bank.setDifficulty("EASY");
    bank.setQuestionOrderMode("TYPE");
    bank.setArchiveStatus("ACTIVE");
    bank.setStatus(1);
    questionBankMapper.insert(bank);
    return bank.getQuestionBankId();
  }

  private Long insertQuestion(Long bankId, String code) {
    Question question = new Question();
    question.setQuestionBankId(bankId);
    question.setQuestionCode(code);
    question.setCurrentVersionNo(1);
    question.setArchiveStatus("ACTIVE");
    question.setStatus(1);
    questionMapper.insert(question);
    return question.getQuestionId();
  }

  private Long insertPaper(String code) {
    Paper paper = new Paper();
    paper.setPaperCode(code);
    paper.setPaperName("试卷-" + code);
    paper.setPaperType("RANDOM");
    paper.setCurrentVersionNo(0);
    paper.setArchiveStatus("ACTIVE");
    paper.setStatus(1);
    paperMapper.insert(paper);
    return paper.getPaperId();
  }

  @Test
  @DisplayName("跨租户按 ID 直查题库、题目、试卷一律返回空")
  void selectById_isRejectedAcrossTenants() {
    Long other = otherTenantId();
    Long bankId = TenantContext.callWithTenant(other, () -> insertBank("QP-BANK"));
    Long questionId = TenantContext.callWithTenant(other, () -> insertQuestion(bankId, "QP-Q"));
    Long paperId = TenantContext.callWithTenant(other, () -> insertPaper("QP-PAPER"));

    assertThat(questionBankMapper.selectById(bankId)).isNull();
    assertThat(questionMapper.selectById(questionId)).isNull();
    assertThat(paperMapper.selectById(paperId)).isNull();
  }

  @Test
  @DisplayName("组卷抽题池按别家题库 ID 抽题时取不到任何题目")
  void drawPool_cannotReachOtherTenantQuestionBank() {
    Long other = otherTenantId();
    Long bankId = TenantContext.callWithTenant(other, () -> insertBank("QP-POOL"));
    TenantContext.callWithTenant(other, () -> insertQuestion(bankId, "QP-POOL-Q1"));
    TenantContext.callWithTenant(other, () -> insertQuestion(bankId, "QP-POOL-Q2"));

    // 与 PaperServiceImpl.resolveQuestions 中的抽题池查询同构：只按题库 ID 过滤，租户条件靠插件注入
    List<Question> pool = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                                                      .eq(Question::getQuestionBankId, bankId)
                                                      .eq(Question::getStatus, 1)
                                                      .eq(Question::getArchiveStatus, "ACTIVE"));

    assertThat(pool)
      .describedAs("引用别家题库 ID 组卷必须抽不到题，随后由 6507 抽题不足拦下").isEmpty();
  }

  @Test
  @DisplayName("本租户抽题池正常命中，隔离没有误伤正常组卷")
  void drawPool_stillReachesOwnTenantQuestionBank() {
    Long bankId = insertBank("QP-OWN");
    insertQuestion(bankId, "QP-OWN-Q1");
    insertQuestion(bankId, "QP-OWN-Q2");

    List<Question> pool = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                                                      .eq(Question::getQuestionBankId, bankId)
                                                      .eq(Question::getStatus, 1)
                                                      .eq(Question::getArchiveStatus, "ACTIVE"));

    assertThat(pool).hasSize(2);
    assertThat(pool).allMatch(q -> demoTenantId().equals(q.getOrganizationId()));
  }

  @Test
  @DisplayName("题库编码在不同租户之间可以重名")
  void bankCode_isUniqueOnlyWithinTenant() {
    Long other = otherTenantId();
    insertBank("QP-SAME-CODE");

    Long otherBankId = TenantContext.callWithTenant(other, () -> insertBank("QP-SAME-CODE"));

    assertThat(otherBankId).isNotNull();
    assertThat(questionBankMapper.selectList(new LambdaQueryWrapper<QuestionBank>()
                                               .eq(QuestionBank::getBankCode, "QP-SAME-CODE"))).hasSize(1);
  }
}
