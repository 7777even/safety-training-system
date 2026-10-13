package com.sts.exam.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.exam.dto.ExamCandidateQuery;
import com.sts.exam.entity.AssessmentExecution;
import com.sts.exam.service.AssessmentExecutionSnapshotCodec;
import com.sts.exam.support.ExamMapperItConfig;
import com.sts.exam.vo.ExamCandidateVO;
import com.sts.exam.vo.ExamReviewerVO;
import com.sts.org.mapper.OrganizationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 批阅人与考生列表的 join 与分页，只有真 SQL 能证明，故放 IT。
 */
@SpringBootTest(classes = ExamMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ExamReviewerAndCandidateQueryIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private ExamReviewerQueryMapper reviewerQueryMapper;
  @Autowired
  private ExamCandidateQueryMapper candidateQueryMapper;
  @Autowired
  private AssessmentExecutionMapper executionMapper;
  @Autowired
  private OrganizationMapper organizationMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("批阅人读取：返回姓名、账号与机构")
  void reviewersCarryDisplayFields() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    long deptId = insertDept(orgId, "RV-DEPT");
    long userId = insertUser("rv-user", "批阅王五", deptId);
    long examId = insertExam(orgId, "EXAM-RV-1");
    long executionId = insertExecution(orgId, examId);
    jdbcTemplate.update("INSERT INTO sts_exam_reviewer (organization_id, assessment_execution_id, reviewer_user_id,"
                          + " assignment_source) VALUES (?,?,?,'USER')", demoTenantId(), executionId, userId);

    List<ExamReviewerVO> reviewers = reviewerQueryMapper.selectReviewers(executionId);

    assertThat(reviewers).hasSize(1);
    assertThat(reviewers.getFirst().getReviewerUserId()).isEqualTo(userId);
    assertThat(reviewers.getFirst().getReviewerName()).isEqualTo("批阅王五");
    assertThat(reviewers.getFirst().getReviewerCode()).isEqualTo("rv-user");
    assertThat(reviewers.getFirst().getReviewerOrganizationName()).isNotBlank();
    assertThat(reviewers.getFirst().getAssignmentSource()).isEqualTo("USER");
  }

  @Test
  @DisplayName("考生分页：快照优先于实时用户名，改名后历史记录不变")
  void candidatePagePrefersSnapshotOverLiveUser() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    long deptId = insertDept(orgId, "CD-DEPT");
    long userId = insertUser("cd-user", "改名后的新名字", deptId);
    long examId = insertExam(orgId, "EXAM-CD-1");
    insertCandidate(orgId, examId, userId,
                    "{\"userId\":" + userId + ",\"userName\":\"考试当时的姓名\",\"userCode\":\"cd-user\"}");

    var page = candidateQueryMapper.selectCandidatePage(new Page<>(1, 10), examId, null);

    assertThat(page.getTotal()).isEqualTo(1);
    ExamCandidateVO vo = page.getRecords().getFirst();
    assertThat(vo.getLearnerName()).isEqualTo("考试当时的姓名");
    assertThat(vo.getDeptName()).isEqualTo("CD-DEPT-名称");
  }

  @Test
  @DisplayName("考生分页：快照缺姓名时回退实时用户数据")
  void candidatePageFallsBackWhenSnapshotIncomplete() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    long deptId = insertDept(orgId, "FB-DEPT");
    long userId = insertUser("fb-user", "实时姓名", deptId);
    long examId = insertExam(orgId, "EXAM-CD-2");
    insertCandidate(orgId, examId, userId, "{\"userId\":" + userId + "}");

    var page = candidateQueryMapper.selectCandidatePage(new Page<>(1, 10), examId, null);

    assertThat(page.getRecords().getFirst().getLearnerName()).isEqualTo("实时姓名");
  }

  @Test
  @DisplayName("考生分页：关键字与状态筛选，分页总数与条件一致")
  void candidatePageFiltersAndCountsConsistently() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    long deptId = insertDept(orgId, "FL-DEPT");
    long examId = insertExam(orgId, "EXAM-CD-3");
    long alice = insertUser("fl-alice", "张三", deptId);
    long bob = insertUser("fl-bob", "李四", deptId);
    insertCandidate(orgId, examId, alice,
                    "{\"userId\":" + alice + ",\"userName\":\"张三\",\"userCode\":\"fl-alice\"}");
    insertCandidate(orgId, examId, bob,
                    "{\"userId\":" + bob + ",\"userName\":\"李四\",\"userCode\":\"fl-bob\"}");

    ExamCandidateQuery byName = new ExamCandidateQuery();
    byName.setLearnerKeyword("张");
    var named = candidateQueryMapper.selectCandidatePage(new Page<>(1, 10), examId, byName);
    assertThat(named.getTotal()).isEqualTo(1);
    assertThat(named.getRecords().getFirst().getLearnerName()).isEqualTo("张三");

    ExamCandidateQuery byStatus = new ExamCandidateQuery();
    byStatus.setCandidateStatus("NOT_STARTED");
    assertThat(candidateQueryMapper.selectCandidatePage(new Page<>(1, 10), examId, byStatus).getTotal())
      .isEqualTo(2);

    ExamCandidateQuery none = new ExamCandidateQuery();
    none.setCandidateStatus("PASSED");
    assertThat(candidateQueryMapper.selectCandidatePage(new Page<>(1, 10), examId, none).getTotal())
      .isZero();
  }

  private long insertDept(Long orgId, String code) {
    jdbcTemplate.update("INSERT INTO us_sys_dept (organization_id, parent_id, ancestors, dept_code,"
                          + " dept_name, dept_type, description, status) VALUES (?,0,'0',?,?,'DEPARTMENT','',1)",
                        orgId, code, code + "-名称");
    return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  private long insertUser(String code, String name, long deptId) {
    // 裸 SQL 不经过租户插件，须显式写入租户列
    jdbcTemplate.update("INSERT INTO us_sys_user (organization_id, user_code, user_name, status, dept_id)"
                          + " VALUES (?,?,?,1,?)",
                        demoTenantId(), code, name, deptId);
    return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  private long insertExam(Long orgId, String examCode) {
    jdbcTemplate.update("INSERT INTO sts_exam_exam (organization_id, exam_code, exam_name, exam_type,"
                          + " duration_seconds, pass_ratio, pass_score, credit, question_order_mode,"
                          + " multi_select_scoring, is_notice_enabled, publish_status, status)"
                          + " VALUES (?,?,?,'ONLINE',3600,0.6,60,1,'IMPORT','ALL_OR_NOTHING',0,'DRAFT',1)",
                        orgId, examCode, examCode + "-名称");
    return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  private void insertCandidate(Long orgId, long examId, long userId, String snapshotJson) {
    long executionId = insertExecution(orgId, examId);
    jdbcTemplate.update("INSERT INTO sts_exam_candidate (organization_id, assessment_execution_id, user_id,"
                          + " assignment_source, source_business_id,"
                          + " candidate_profile_snapshot_json, profile_snapshot_time,"
                          + " candidate_status, attempt_count)"
                          + " VALUES (?,?,?,'ASSIGN',0,?,NOW(),'NOT_STARTED',0)",
                        orgId, executionId, userId, snapshotJson);
  }

  private long insertExecution(Long orgId, long examId) {
    AssessmentExecution existing = executionMapper.selectOne(new LambdaQueryWrapper<AssessmentExecution>()
                                                               .eq(AssessmentExecution::getExamId, examId));
    if (existing != null) return existing.getAssessmentExecutionId();
    AssessmentExecution execution = new AssessmentExecution();
    execution.setOrganizationId(orgId);
    execution.setSourceType("STANDALONE_EXAM");
    execution.setSourceBusinessId(examId);
    execution.setExamId(examId);
    execution.setAssessmentKind("FORMAL_EXAM");
    execution.setResourceType("PAPER_VERSION");
    execution.setResourceId(examId);
    execution.setResourceNameSnapshot("试卷");
    execution.setDurationSecondsSnapshot(3600);
    execution.setTotalScoreSnapshot(new BigDecimal("100"));
    execution.setPassScoreSnapshot(new BigDecimal("60"));
    execution.setQuestionOrderModeSnapshot("IMPORT");
    execution.setMultiSelectScoringSnapshot("ALL_OR_NOTHING");
    execution.setPolicySnapshotJson("{}");
    execution.setExecutionStatus("READY");
    new AssessmentExecutionSnapshotCodec(new ObjectMapper()).encode(execution);
    executionMapper.insert(execution);
    return execution.getAssessmentExecutionId();
  }
}
