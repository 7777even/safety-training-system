package com.sts.question.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.question.entity.*;
import com.sts.question.support.QuestionMapperItConfig;
import com.sts.question.vo.QuestionDrawCandidateRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = QuestionMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class QuestionDrawCandidatePoolMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private OrganizationMapper organizationMapper;
  @Autowired
  private QuestionBankMapper bankMapper;
  @Autowired
  private QuestionMapper questionMapper;
  @Autowired
  private QuestionVersionMapper versionMapper;
  @Autowired
  private QuestionKnowledgeMapper knowledgeMapper;
  @Autowired
  private QuestionOptionMapper optionMapper;

  @Test
  @DisplayName("候选 SQL 只读取启用未归档题目的最新已发布版本并保留知识点")
  void selectsRealDrawCandidates() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Long bankId = insertBank(orgId);
    Long active = insertQuestion(orgId, bankId, "DRAW-A", "ACTIVE", 1);
    Long disabled = insertQuestion(orgId, bankId, "DRAW-D", "ACTIVE", 0);
    Long archived = insertQuestion(orgId, bankId, "DRAW-X", "ARCHIVED", 1);
    insertVersion(active, 1, "SINGLE", "PUBLISHED");
    Long latestVersionId = insertVersion(active, 2, "SINGLE", "PUBLISHED");
    insertVersion(active, 3, "SINGLE", "DRAFT");
    insertVersion(disabled, 1, "SINGLE", "PUBLISHED");
    insertVersion(archived, 1, "SINGLE", "PUBLISHED");
    insertKnowledge(active, 101L);
    insertKnowledge(active, 102L);
    insertCorrectOption(latestVersionId);

    List<QuestionDrawCandidateRow> rows = questionMapper.selectDrawCandidateRows(bankId);

    assertThat(rows).hasSize(2);
    assertThat(rows).extracting(QuestionDrawCandidateRow::getQuestionId).containsOnly(active);
    assertThat(rows).extracting(QuestionDrawCandidateRow::getQuestionVersionId).containsOnly(latestVersionId);
    assertThat(rows).extracting(QuestionDrawCandidateRow::getKnowledgePointId)
      .containsExactlyInAnyOrder(101L, 102L);
    assertThat(rows).extracting(QuestionDrawCandidateRow::getCorrectAnswerCount).containsOnly(1);
  }

  private Long insertBank(Long orgId) {
    QuestionBank bank = new QuestionBank();
    bank.setOrganizationId(orgId);
    bank.setBankCode("DRAW-POOL-IT");
    bank.setBankName("抽题候选池");
    bank.setDifficulty("NORMAL");
    bank.setQuestionOrderMode("IMPORT");
    bank.setArchiveStatus("ACTIVE");
    bank.setStatus(1);
    bankMapper.insert(bank);
    return bank.getQuestionBankId();
  }

  private Long insertQuestion(Long orgId, Long bankId, String code, String archiveStatus, int status) {
    Question question = new Question();
    question.setOrganizationId(orgId);
    question.setQuestionBankId(bankId);
    question.setQuestionCode(code);
    question.setCurrentVersionNo(1);
    question.setArchiveStatus(archiveStatus);
    question.setStatus(status);
    questionMapper.insert(question);
    return question.getQuestionId();
  }

  private Long insertVersion(Long questionId, int versionNo, String type, String publishedStatus) {
    QuestionVersion version = new QuestionVersion();
    version.setQuestionId(questionId);
    version.setVersionNo(versionNo);
    version.setQuestionType(type);
    version.setStem("题干");
    version.setDifficulty("NORMAL");
    version.setGradingMode("AUTO");
    version.setIsUnorderedAnswerAllowed(0);
    version.setIsCaseIgnored(0);
    version.setIsFullHalfWidthIgnored(0);
    version.setPublishedStatus(publishedStatus);
    versionMapper.insert(version);
    return version.getQuestionVersionId();
  }

  private void insertKnowledge(Long questionId, Long pointId) {
    QuestionKnowledge knowledge = new QuestionKnowledge();
    knowledge.setQuestionId(questionId);
    knowledge.setKnowledgePointId(pointId);
    knowledgeMapper.insert(knowledge);
  }

  private void insertCorrectOption(Long versionId) {
    QuestionOption option = new QuestionOption();
    option.setQuestionVersionId(versionId);
    option.setOptionKey("A");
    option.setOptionContent("正确项");
    option.setIsCorrect(1);
    option.setSortOrder(1);
    optionMapper.insert(option);
  }
}
