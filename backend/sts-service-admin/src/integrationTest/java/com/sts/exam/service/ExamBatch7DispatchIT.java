package com.sts.exam.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.support.*;
import com.sts.admin.mapper.SysMenuMapper;
import com.sts.admin.system.service.SysDeptService;
import com.sts.admin.tenant.TenantContext;
import com.sts.exam.mapper.*;
import com.sts.exam.entity.*;
import com.sts.exam.dto.*;
import com.sts.person.dto.PersonSelectionRequest;
import com.sts.person.service.*;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 真实组查询、考生/评卷人持久化与替换事务；权限矩阵由独立资格单测覆盖。 */
@SpringBootTest(classes=ExamBatch7DispatchIT.Config.class)
@Transactional
class ExamBatch7DispatchIT extends AbstractMysqlIntegrationTest {
  @Configuration @EnableTransactionManagement
  @Import({MapperItConfig.class,PersonClassificationService.class,PersonSelectionServiceImpl.class,ExamReviewerEligibility.class,ExamCandidateProfileSnapshotAssembler.class})
  @MapperScan({"com.sts.person.mapper","com.sts.org.mapper","com.sts.exam.mapper","com.sts.training.mapper"})
  static class Config {
    @Bean ObjectMapper json(){return new ObjectMapper().findAndRegisterModules();}
    @Bean SysDeptService departments(){return mock(SysDeptService.class);}
    @Bean @Primary SysMenuMapper permissions(){var m=mock(SysMenuMapper.class);when(m.selectPermsByUserId(anyLong())).thenReturn(List.of("exam:review"));return m;}
    @Bean ExamServiceImpl exams(ExamMapper exams,ExamCandidateMapper candidates,ExamReviewerMapper reviewers,PersonSelectionService people,ExamReviewerEligibility eligibility,ObjectMapper json,ExamCandidateProfileSnapshotAssembler assembler){
      var executions=mock(AssessmentExecutionService.class);var execution=new AssessmentExecution();execution.setAssessmentExecutionId(987654L);execution.setSourceType("STANDALONE");when(executions.findStandaloneByExamId(anyLong())).thenAnswer(call->{execution.setExamId(call.getArgument(0));return execution;});
      // 候选人回读复用同一个已创建执行，不能让端口返回空记录。
      when(executions.requireById(987654L)).thenReturn(execution);
      return new ExamServiceImpl(exams,mock(ExamPolicyMapper.class),candidates,reviewers,mock(com.sts.paper.mapper.PaperVersionMapper.class),mock(com.sts.paper.mapper.PaperMapper.class),mock(com.sts.question.mapper.QuestionBankMapper.class),people,mock(ExamPolicyDeleteHelper.class),assembler,mock(ExamReviewerQueryMapper.class),mock(ExamCandidateQueryMapper.class),executions,json,eligibility);
    }
  }
  @Autowired JdbcTemplate jdbc;
  /** JDBC旁路更新后读取真实人员状态和组成员。 */
  @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
  @Autowired ExamService exams;
  @Autowired ExamReviewerMapper reviewers;
  @Autowired ExamCandidateMapper candidates;
  Long user;
  Long exam;
  PersonSelectionRequest seed() {
    Long org=TenantContext.requireTenantId();
    jdbc.update("INSERT INTO us_sys_user(organization_id,user_code,user_name,status) VALUES (?,'b7-exam','组考试人员',1)",org);
    user=jdbc.queryForObject("SELECT user_id FROM us_sys_user WHERE organization_id=? AND user_code='b7-exam'",Long.class,org);
    jdbc.update("INSERT INTO sts_person_profile(organization_id,user_id,occupation) VALUES (?,?,'评卷组')",org,user);
    jdbc.update("INSERT INTO sts_person_group(organization_id,group_name,status,conditions_json) VALUES (?,'b7-exam',1,?)",org,"{\"occupation\":\"评卷组\"}");
    Long group=jdbc.queryForObject("SELECT group_id FROM sts_person_group WHERE organization_id=? AND group_name='b7-exam'",Long.class,org);
    jdbc.update("INSERT INTO sts_exam_exam(organization_id,exam_code,exam_name,exam_type,duration_seconds,pass_ratio,pass_score,credit,question_order_mode,multi_select_scoring,is_notice_enabled,publish_status,status) VALUES (?,'b7-exam','考试','ONLINE',3600,0.6,60,1,'IMPORT','ALL_OR_NOTHING',0,'DRAFT',1)",org);
    exam=jdbc.queryForObject("SELECT exam_id FROM sts_exam_exam WHERE organization_id=? AND exam_code='b7-exam'",Long.class,org);
    var selection=new PersonSelectionRequest();selection.setSelectionType("GROUP");selection.setSubjectIds(List.of(group));return selection;
  }
  @Test void groupCreatesConcreteCandidateAndReviewerSnapshotsWithoutDynamicMembership() {
    var selection=seed();var req=new ExamReviewersReplaceRequest();req.setSelection(selection);exams.replaceReviewers(exam,req);var bind=new BindSelectionRequest();bind.setSelection(selection);exams.bindSelection(exam,bind);
    assertThat(reviewers.selectList(null)).extracting(ExamReviewer::getReviewerUserId).contains(user);assertThat(candidates.selectList(null)).extracting(ExamCandidate::getUserId).contains(user);
    jdbc.update("UPDATE sts_person_profile SET occupation='其它' WHERE user_id=?",user);
    sqlSession.clearCache();
    assertThatThrownBy(()->exams.replaceReviewers(exam,req)).hasMessageContaining("没有符合条件");assertThat(reviewers.selectList(null)).extracting(ExamReviewer::getReviewerUserId).contains(user);
    assertThat(candidates.selectList(null)).extracting(ExamCandidate::getUserId).contains(user);
  }
  @Test void disabledReviewerDoesNotDeleteExistingSnapshotAndExplicitEmptyClears() {
    var selection=seed();var req=new ExamReviewersReplaceRequest();req.setSelection(selection);exams.replaceReviewers(exam,req);
    jdbc.update("UPDATE us_sys_user SET status=0 WHERE user_id=?",user);sqlSession.clearCache();var fixed=new ExamReviewersReplaceRequest();fixed.setReviewerUserIds(List.of(user));assertThatThrownBy(()->exams.replaceReviewers(exam,fixed)).hasMessageContaining("停用");assertThat(reviewers.selectList(null)).hasSize(1);
    fixed.setReviewerUserIds(List.of());exams.replaceReviewers(exam,fixed);assertThat(reviewers.selectList(null)).isEmpty();
  }
}
