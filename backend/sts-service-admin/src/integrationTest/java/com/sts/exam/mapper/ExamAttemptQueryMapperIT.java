package com.sts.exam.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.exam.dto.ExamAttemptRecordQuery;
import com.sts.exam.dto.ExamReviewTaskQuery;
import com.sts.exam.entity.AssessmentExecution;
import com.sts.exam.service.AssessmentExecutionSnapshotCodec;
import com.sts.exam.support.ExamMapperItConfig;
import com.sts.exam.vo.ExamAttemptRecordVO;
import com.sts.exam.vo.ExamReviewTaskVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ExamMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ExamAttemptQueryMapperIT extends AbstractMysqlIntegrationTest {
  @Autowired
  private ExamAttemptQueryMapper mapper;
  @Autowired
  private AssessmentExecutionMapper executionMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  /**
   * 两条手写 XML 的多表查询在租户插件注入条件后仍能被 MySQL 正确解析并分页。
   * 手写 SQL 是租户注入最容易出语法/歧义错误的地方（子查询、JOIN 别名），
   * 因此这里必须用真库跑一次，零 DB 测试证明不了。
   */
  @Test
  void aggregateQueries_executeWithRealMysqlPaginationUnderTenantInjection() {
    Page<ExamReviewTaskVO> reviewPage = mapper.selectReviewTaskPage(
      new Page<>(1, 20), Long.MAX_VALUE, null, false, new ExamReviewTaskQuery());
    Page<ExamAttemptRecordVO> attemptPage = mapper.selectAttemptPage(
      new Page<>(1, 20), new ExamAttemptRecordQuery());

    assertThat(reviewPage.getRecords()).isEmpty();
    assertThat(attemptPage.getRecords()).isEmpty();
    assertThat(attemptPage.getTotal()).isZero();
  }

  @Test
  void courseReviewTasks_usePendingManualStatusAndNeverLeakIntoStandaloneQuery() {
    long executionId = insertCourseReviewFixture();

    Page<ExamReviewTaskVO> coursePage = mapper.selectReviewTaskPage(
      new Page<>(1, 20), 88001L, executionId, true, new ExamReviewTaskQuery());
    Page<ExamReviewTaskVO> standalonePage = mapper.selectReviewTaskPage(
      new Page<>(1, 20), 88001L, null, false, new ExamReviewTaskQuery());

    assertThat(coursePage.getRecords()).singleElement().satisfies(task -> {
      assertThat(task.getAssessmentExecutionId()).isEqualTo(executionId);
      assertThat(task.getSourceType()).isEqualTo("COURSE_QUIZ");
      assertThat(task.getReviewStatus()).isEqualTo("PENDING");
      assertThat(task.getSourceName()).isEqualTo("课程主观题测验");
    });
    assertThat(standalonePage.getRecords()).isEmpty();
  }

  private long insertCourseReviewFixture() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setOrganizationId(demoTenantId());
    execution.setSourceType("COURSE_QUIZ");
    execution.setSourceBusinessId(101L);
    execution.setCourseOfferingId(100L);
    execution.setCourseVersionId(200L);
    execution.setCourseItemId(101L);
    execution.setAssessmentKind("QUIZ");
    execution.setResourceType("PAPER_VERSION");
    execution.setResourceId(300L);
    execution.setResourceNameSnapshot("课程主观题测验");
    execution.setDurationSecondsSnapshot(3600);
    execution.setTotalScoreSnapshot(new BigDecimal("100"));
    execution.setPassScoreSnapshot(new BigDecimal("60"));
    execution.setQuestionOrderModeSnapshot("IMPORT");
    execution.setMultiSelectScoringSnapshot("ALL_OR_NOTHING");
    execution.setPolicySnapshotJson("{}");
    execution.setExecutionStatus("READY");
    new AssessmentExecutionSnapshotCodec(new ObjectMapper()).encode(execution);
    executionMapper.insert(execution);
    long executionId = execution.getAssessmentExecutionId();
    jdbcTemplate.update("INSERT INTO sts_exam_reviewer (organization_id,assessment_execution_id,reviewer_user_id,"
                          + "assignment_source) VALUES (?,?,88001,'USER')", demoTenantId(), executionId);
    jdbcTemplate.update("INSERT INTO sts_exam_candidate (organization_id,assessment_execution_id,user_id,"
                          + "course_offering_id,course_item_id,course_enrollment_id,assignment_source,source_business_id,"
                          + "candidate_profile_snapshot_json,profile_snapshot_time,candidate_status,attempt_count) "
                          + "VALUES (?,?,88002,100,101,1000,'COURSE',101,'{\"userName\":\"课程学员\",\"userCode\":\"L88002\"}',"
                          + "NOW(),'PENDING_REVIEW',1)", demoTenantId(), executionId);
    long candidateId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbcTemplate.update("INSERT INTO sts_exam_attempt (organization_id,assessment_execution_id,source_type,"
                          + "assessment_kind,exam_candidate_id,user_id,paper_version_id,paper_source_type,paper_source_id,"
                          + "total_score_snapshot,pass_score_snapshot,duration_seconds_snapshot,question_order_mode_snapshot,"
                          + "multi_select_scoring_snapshot,attempt_no,attempt_type,attempt_status,submitted_time,used_seconds,"
                          + "objective_score,subjective_score,final_score,correct_count,wrong_count,client_attempt_token) "
                          + "VALUES (?,?,'COURSE_QUIZ','QUIZ',?,88002,300,'PAPER_VERSION',300,100,60,3600,'IMPORT',"
                          + "'ALL_OR_NOTHING',1,'FIRST','PENDING_REVIEW',NOW(),600,80,0,80,8,1,'course-review-fixture')",
                        demoTenantId(), executionId, candidateId);
    long attemptId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbcTemplate.update("INSERT INTO sts_exam_attempt_question (organization_id,exam_attempt_id,question_id,"
                          + "question_version_id,question_type,stem_snapshot,grading_mode,score,sort_order,source_bank_id) "
                          + "VALUES (?,?,501,601,'ESSAY','说明安全措施','MANUAL',20,1,701)", demoTenantId(), attemptId);
    long questionId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbcTemplate.update("INSERT INTO sts_exam_answer (organization_id,exam_attempt_id,attempt_question_id,"
                          + "answer_text,is_answered,final_score,grading_status,answered_time,last_saved_time) "
                          + "VALUES (?,?,?,'先通风再检测',1,0,'PENDING_MANUAL',NOW(),NOW())",
                        demoTenantId(), attemptId, questionId);
    return executionId;
  }
}
