package com.sts.course.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.common.api.*;
import com.sts.common.exception.BusinessException;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.course.entity.*;
import com.sts.course.mapper.*;
import com.sts.course.vo.*;
import com.sts.exam.entity.*;
import com.sts.exam.mapper.*;
import com.sts.exam.service.ExamStatisticsCalculator;
import com.sts.question.entity.KnowledgePoint;
import com.sts.question.mapper.KnowledgePointMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
/** 课程管理统计，基础事实受当前租户插件过滤。 */
@Service
@RequiredArgsConstructor
public class CourseStatisticsService {
  private final CourseMapper courseMapper;
  private final CourseOfferingMapper offeringMapper;
  private final CourseEnrollmentMapper enrollmentMapper;
  private final CourseStudySessionMapper sessionMapper;
  private final CourseCourseCategoryMapper categoryRelMapper;
  private final CourseCategoryMapper categoryMapper;
  private final ExamCandidateMapper candidateMapper;
  private final ExamAttemptMapper attemptMapper;
  private final ExamAttemptQuestionMapper questionMapper;
  private final ExamAnswerMapper answerMapper;
  /** 冻结执行题池提供覆盖事实，独立于是否有人交卷。 */
  private final AssessmentExecutionMapper executionMapper;
  private final AssessmentExecutionQuestionPoolMapper poolMapper;
  private final KnowledgePointMapper knowledgeMapper;
  private final SysUserMapper userMapper;
  private final ObjectMapper objectMapper;

  /** 课程为空表示当前租户所有课程，用于分类分析。 */
  private Facts facts(Long courseId,Long offeringId,boolean includeWithdrawn) {
    if(courseId!=null && courseMapper.selectById(courseId)==null) throw new BusinessException(HttpStatus.NOT_FOUND,5001,"课程不存在");
    var offerings=offeringMapper.selectList(new LambdaQueryWrapper<CourseOffering>().eq(courseId!=null,CourseOffering::getCourseId,courseId).eq(offeringId!=null,CourseOffering::getCourseOfferingId,offeringId));
    var ids=offerings.stream().map(CourseOffering::getCourseOfferingId).toList();
    var rows=ids.isEmpty()?List.<CourseEnrollment>of():enrollmentMapper.selectList(new LambdaQueryWrapper<CourseEnrollment>().in(CourseEnrollment::getCourseOfferingId,ids).ne(!includeWithdrawn,CourseEnrollment::getLearningStatus,"WITHDRAWN"));
    var enrollmentIds=rows.stream().map(CourseEnrollment::getCourseEnrollmentId).toList();
    var sessions=enrollmentIds.isEmpty()?List.<CourseStudySession>of():sessionMapper.selectList(new LambdaQueryWrapper<CourseStudySession>().in(CourseStudySession::getCourseEnrollmentId,enrollmentIds));
    var candidates=enrollmentIds.isEmpty()?List.<ExamCandidate>of():candidateMapper.selectList(new LambdaQueryWrapper<ExamCandidate>().in(ExamCandidate::getCourseEnrollmentId,enrollmentIds));
    var candidateIds=candidates.stream().map(ExamCandidate::getExamCandidateId).toList();
    var attempts=candidateIds.isEmpty()?List.<ExamAttempt>of():attemptMapper.selectList(new LambdaQueryWrapper<ExamAttempt>().in(ExamAttempt::getExamCandidateId,candidateIds));
    Set<Long> started=new HashSet<>(); attempts.forEach(a->started.add(a.getExamCandidateId()));
    Set<Long> assessed=new HashSet<>(); candidates.stream().filter(c->started.contains(c.getExamCandidateId())).forEach(c->assessed.add(c.getCourseEnrollmentId()));
    return new Facts(offerings,rows,sessions,candidates,attempts,assessed);
  }
  /** 单课程、开班及分类参与统计。 */
  public CourseStatisticsVO statistics(Long courseId,Long offeringId) {
    var f=facts(courseId,offeringId,false);
    var groups=f.offerings.stream().map(o->new CourseStatisticsVO.Group(o.getCourseOfferingId(),o.getOfferingName(),summary(f,Set.of(o.getCourseOfferingId())))).toList();
    var courseIds=f.offerings.stream().map(CourseOffering::getCourseId).distinct().toList();
    var relations=courseIds.isEmpty()?List.<CourseCourseCategory>of():categoryRelMapper.selectList(new LambdaQueryWrapper<CourseCourseCategory>().in(CourseCourseCategory::getCourseId,courseIds));
    var categoryIds=relations.stream().map(CourseCourseCategory::getCourseCategoryId).distinct().toList();
    var categories=categoryIds.isEmpty()?List.<CourseCategory>of():categoryMapper.selectList(new LambdaQueryWrapper<CourseCategory>().in(CourseCategory::getCourseCategoryId,categoryIds));
    var categoryGroups=categories.stream().map(c->{
      var related=relations.stream().filter(r->Objects.equals(r.getCourseCategoryId(),c.getCourseCategoryId())).map(CourseCourseCategory::getCourseId).collect(Collectors.toSet());
      var ids=f.offerings.stream().filter(o->related.contains(o.getCourseId())).map(CourseOffering::getCourseOfferingId).collect(Collectors.toSet());
      return new CourseStatisticsVO.Group(c.getCourseCategoryId(),c.getCategoryName(),summary(f,ids));
    }).toList();
    var selected=f.candidates.stream().filter(c->c.getFinalAttemptId()!=null).map(ExamCandidate::getFinalAttemptId).distinct().toList();
    var attemptedIds=f.attempts.stream().map(ExamAttempt::getExamAttemptId).toList();
    var questions=new ArrayList<>(attemptedIds.isEmpty()?List.<ExamAttemptQuestion>of():questionMapper.selectList(new LambdaQueryWrapper<ExamAttemptQuestion>().in(ExamAttemptQuestion::getExamAttemptId,attemptedIds)));
    var offeringIds=f.offerings.stream().map(CourseOffering::getCourseOfferingId).toList();
    var executions=offeringIds.isEmpty()?List.<AssessmentExecution>of():executionMapper.selectList(new LambdaQueryWrapper<AssessmentExecution>().in(AssessmentExecution::getCourseOfferingId,offeringIds));
    var executionIds=executions.stream().map(AssessmentExecution::getAssessmentExecutionId).toList();
    var frozenPool=executionIds.isEmpty()?List.<AssessmentExecutionQuestionPool>of():poolMapper.selectList(new LambdaQueryWrapper<AssessmentExecutionQuestionPool>().in(AssessmentExecutionQuestionPool::getAssessmentExecutionId,executionIds));
    for(var pooled:frozenPool) {
      var coverage=new ExamAttemptQuestion();coverage.setQuestionVersionId(pooled.getQuestionVersionId());coverage.setKnowledgePointIdsSnapshotJson(pooled.getKnowledgePointIdsJson());
      questions.add(coverage);
    }
    for(var execution:executions)for(var frozen:com.sts.exam.service.CourseKnowledgeSnapshotService.read(execution,objectMapper)) {
      var coverage=new ExamAttemptQuestion();coverage.setQuestionVersionId(frozen.questionVersionId());coverage.setKnowledgePointIdsSnapshotJson(objectMapper.valueToTree(frozen.knowledgePointIds()).toString());questions.add(coverage);
    }
    var answers=selected.isEmpty()?List.<ExamAnswer>of():answerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>().in(ExamAnswer::getExamAttemptId,selected));
    var answerMap=answers.stream().collect(Collectors.toMap(ExamAnswer::getAttemptQuestionId,Function.identity(),(a,b)->b));
    Map<Long,List<ExamAttemptQuestion>> knowledge=new TreeMap<>();
    long missing=0;
    for(var q:questions) {
      if(q.getKnowledgePointIdsSnapshotJson()==null) { missing++;continue; }
      try {
        var ids=objectMapper.readValue(q.getKnowledgePointIdsSnapshotJson(),new TypeReference<List<Long>>(){});
        for(var id:new HashSet<>(ids)) knowledge.computeIfAbsent(id,k->new ArrayList<>()).add(q);
      } catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new BusinessException(HttpStatus.CONFLICT,8167,"答卷知识点快照无法解析"); }
    }
    var names=knowledge.isEmpty()?Map.<Long,KnowledgePoint>of():knowledgeMapper.selectList(new LambdaQueryWrapper<KnowledgePoint>().in(KnowledgePoint::getKnowledgePointId,knowledge.keySet())).stream().collect(Collectors.toMap(KnowledgePoint::getKnowledgePointId,Function.identity()));
    var knowledgeRows=knowledge.entrySet().stream().map(e->{
      var qs=e.getValue(); var evaluated=qs.stream().map(q->answerMap.get(q.getAttemptQuestionId())).filter(Objects::nonNull).filter(a->a.getIsCorrect()!=null).toList();
      long correct=evaluated.stream().filter(a->a.getIsCorrect()==1).count();
      long answered=qs.stream().map(q->answerMap.get(q.getAttemptQuestionId())).filter(Objects::nonNull).filter(a->Integer.valueOf(1).equals(a.getIsAnswered())).count();
      return new CourseStatisticsVO.Knowledge(e.getKey(),names.containsKey(e.getKey())?names.get(e.getKey()).getKnowledgeName():"历史知识点",qs.stream().map(ExamAttemptQuestion::getQuestionVersionId).distinct().count(),answered,evaluated.size(),correct,evaluated.isEmpty()?null:ExamStatisticsCalculator.percent(correct,evaluated.size()));
    }).toList();
    return new CourseStatisticsVO(CourseStatisticsCalculator.summary(f.rows,f.sessions,f.assessed),groups,categoryGroups,knowledgeRows,missing);
  }
  private CourseStatisticsVO.Summary summary(Facts f,Set<Long> offeringIds) {
    return CourseStatisticsCalculator.summary(f.rows.stream().filter(r->offeringIds.contains(r.getCourseOfferingId())).toList(),f.sessions,f.assessed);
  }
  /** 可跨课程查询全部记录，分页保留每条开班报名。 */
  public PageResult<CourseStatisticsPersonVO> people(Long courseId,Long offeringId,String keyword,int pageNum,int pageSize) {
    var f=facts(courseId,offeringId,true);
    var ids=f.rows.stream().map(CourseEnrollment::getUserId).distinct().toList();
    var users=ids.isEmpty()?Map.<Long,SysUser>of():userMapper.selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getUserId,ids)).stream().collect(Collectors.toMap(SysUser::getUserId,Function.identity()));
    var courseIds=f.offerings.stream().map(CourseOffering::getCourseId).distinct().toList();
    var courses=courseIds.isEmpty()?Map.<Long,Course>of():courseMapper.selectList(new LambdaQueryWrapper<Course>().in(Course::getCourseId,courseIds)).stream().collect(Collectors.toMap(Course::getCourseId,Function.identity()));
    var offerings=f.offerings.stream().collect(Collectors.toMap(CourseOffering::getCourseOfferingId,Function.identity()));
    var rows=f.rows.stream().map(r->{
      var user=users.get(r.getUserId());var o=offerings.get(r.getCourseOfferingId());var c=courses.get(o.getCourseId());
      return new CourseStatisticsPersonVO(r.getCourseEnrollmentId(),r.getUserId(),user==null?"历史学员":user.getUserName(),user==null?"":user.getUserCode(),
        o.getCourseId(),c==null?"历史课程":c.getCourseName(),o.getCourseOfferingId(),o.getOfferingName(),r.getLearningStatus(),
        r.getTotalStudySeconds()==null?0:r.getTotalStudySeconds(),f.sessions.stream().filter(s->Objects.equals(s.getCourseEnrollmentId(),r.getCourseEnrollmentId()) && s.getValidStudySeconds()!=null && s.getValidStudySeconds()>0).count(),r.getProgressPercent());
    }).filter(r->keyword==null || keyword.isBlank() || (r.learnerName()+" "+r.learnerCode()).contains(keyword.trim())).sorted(Comparator.comparing(CourseStatisticsPersonVO::courseEnrollmentId)).toList();
    int start=(int)Math.min(rows.size(),(long)(pageNum-1)*pageSize);
    return new PageResult<>(rows.subList(start,(int)Math.min(rows.size(),(long)start+pageSize)),rows.size());
  }
  private record Facts(List<CourseOffering> offerings,List<CourseEnrollment> rows,List<CourseStudySession> sessions,List<ExamCandidate> candidates,List<ExamAttempt> attempts,Set<Long> assessed) {}
}
