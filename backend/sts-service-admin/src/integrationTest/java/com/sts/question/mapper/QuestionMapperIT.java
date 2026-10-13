package com.sts.question.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.question.entity.Question;
import com.sts.question.entity.QuestionBank;
import com.sts.question.entity.QuestionVersion;
import com.sts.question.support.QuestionMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = QuestionMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class QuestionMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private QuestionBankMapper questionBankMapper;
  @Autowired
  private QuestionMapper questionMapper;
  @Autowired
  private QuestionVersionMapper questionVersionMapper;
  @Autowired
  private OrganizationMapper organizationMapper;

  @Test
  @DisplayName("题库编码唯一；题目版本 uk_question_version")
  void bankAndVersionUnique() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    QuestionBank bank = new QuestionBank();
    bank.setOrganizationId(orgId);
    bank.setBankCode("B-IT-1");
    bank.setBankName("题库");
    bank.setDifficulty("EASY");
    bank.setQuestionOrderMode("TYPE");
    bank.setArchiveStatus("ACTIVE");
    bank.setStatus(1);
    questionBankMapper.insert(bank);

    QuestionBank dup = new QuestionBank();
    dup.setOrganizationId(orgId);
    dup.setBankCode("B-IT-1");
    dup.setBankName("题库2");
    dup.setDifficulty("EASY");
    dup.setQuestionOrderMode("TYPE");
    dup.setArchiveStatus("ACTIVE");
    dup.setStatus(1);
    assertThatThrownBy(() -> questionBankMapper.insert(dup)).isInstanceOf(DuplicateKeyException.class);

    Question q = new Question();
    q.setOrganizationId(orgId);
    q.setQuestionBankId(bank.getQuestionBankId());
    q.setQuestionCode("Q-IT-1");
    q.setCurrentVersionNo(1);
    q.setArchiveStatus("ACTIVE");
    q.setStatus(1);
    questionMapper.insert(q);

    QuestionVersion v1 = version(q.getQuestionId(), 1);
    questionVersionMapper.insert(v1);
    assertThat(v1.getQuestionVersionId()).isNotNull();

    QuestionVersion v1dup = version(q.getQuestionId(), 1);
    assertThatThrownBy(() -> questionVersionMapper.insert(v1dup)).isInstanceOf(DuplicateKeyException.class);

    QuestionVersion v2 = version(q.getQuestionId(), 2);
    questionVersionMapper.insert(v2);
    assertThat(questionVersionMapper.selectById(v2.getQuestionVersionId()).getVersionNo()).isEqualTo(2);
  }

  private static QuestionVersion version(Long questionId, int no) {
    QuestionVersion v = new QuestionVersion();
    v.setQuestionId(questionId);
    v.setVersionNo(no);
    v.setQuestionType("SINGLE");
    v.setStem("stem");
    v.setDifficulty("EASY");
    v.setGradingMode("AUTO");
    v.setIsUnorderedAnswerAllowed(0);
    v.setIsCaseIgnored(0);
    v.setIsFullHalfWidthIgnored(0);
    v.setPublishedStatus("DRAFT");
    return v;
  }
}
