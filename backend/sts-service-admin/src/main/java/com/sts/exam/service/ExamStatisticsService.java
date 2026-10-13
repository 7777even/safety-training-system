package com.sts.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.common.api.*;
import com.sts.common.exception.BusinessException;
import com.sts.exam.entity.*;
import com.sts.exam.mapper.*;
import com.sts.exam.dto.ExamStatisticsQuery;
import com.sts.exam.vo.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 同一租户基础事实驱动汇总、分页及导出，不物化新统计表。 */
@Service
@RequiredArgsConstructor
public class ExamStatisticsService {
  private final ExamService examService;
  private final AssessmentExecutionMapper executionMapper;
  private final ExamCandidateMapper candidateMapper;
  private final ExamAttemptMapper attemptMapper;
  private final ExamAttemptQuestionMapper questionMapper;
  private final ExamAnswerMapper answerMapper;
  private final SysUserMapper userMapper;
  private final ObjectMapper objectMapper;

  /** 只读加载当前有权查看的考试，不调用 ensure 执行物化写入。 */
  private Facts facts(Long examId, boolean includeQuestions) {
    var exam = examService.get(examId);
    var executions = executionMapper.selectList(new LambdaQueryWrapper<AssessmentExecution>().eq(AssessmentExecution::getExamId,examId).eq(AssessmentExecution::getAssessmentKind,"FORMAL_EXAM"));
    if (executions.isEmpty()) return new Facts(List.of(),List.of(),List.of(),List.of(),exam.getOpenEndTime()!=null && LocalDateTime.now().isAfter(exam.getOpenEndTime()));
    var ids = executions.stream().map(AssessmentExecution::getAssessmentExecutionId).toList();
    var candidates = candidateMapper.selectList(new LambdaQueryWrapper<ExamCandidate>().in(ExamCandidate::getAssessmentExecutionId,ids));
    var attempts = attemptMapper.selectList(new LambdaQueryWrapper<ExamAttempt>().in(ExamAttempt::getAssessmentExecutionId,ids).eq(ExamAttempt::getAssessmentKind,"FORMAL_EXAM"));
    var attemptIds = includeQuestions ? candidates.stream().filter(ExamStatisticsCalculator::scored).map(ExamCandidate::getFinalAttemptId).distinct().toList() : List.<Long>of();
    var questions = attemptIds.isEmpty() ? List.<ExamAttemptQuestion>of() : questionMapper.selectList(new LambdaQueryWrapper<ExamAttemptQuestion>().in(ExamAttemptQuestion::getExamAttemptId,attemptIds));
    var answers = attemptIds.isEmpty() ? List.<ExamAnswer>of() : answerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>().in(ExamAnswer::getExamAttemptId,attemptIds));
    return new Facts(candidates,attempts,questions,answers,exam.getOpenEndTime()!=null && LocalDateTime.now().isAfter(exam.getOpenEndTime()));
  }
  /** 当次考试统计；所有 Mapper 受现行租户插件过滤。 */
  public ExamStatisticsVO statistics(Long examId) {
    var f = facts(examId,true);
    return ExamStatisticsCalculator.calculate(f.candidates,f.attempts,f.questions,f.answers,f.closed);
  }
  /** 分页仅裁剪已筛选明细，不改变汇总及排名口径。 */
  public PageResult<ExamStatisticsPersonVO> page(Long examId, PageRequest<ExamStatisticsQuery> request) {
    var rows = people(examId,request.getQuery());
    int start = (int)Math.min(rows.size(),(long)(request.getPageNum()-1)*request.getPageSize());
    return new PageResult<>(rows.subList(start,(int)Math.min(rows.size(),(long)start+request.getPageSize())),rows.size());
  }
  /** 导出及分页复用完全相同的筛选。 */
  public List<ExamStatisticsPersonVO> people(Long examId, ExamStatisticsQuery query) {
    var f = facts(examId,false);
    var userIds = f.candidates.stream().map(ExamCandidate::getUserId).distinct().toList();
    Map<Long,SysUser> users = userIds.isEmpty() ? Map.of() : userMapper.selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getUserId,userIds)).stream().collect(Collectors.toMap(SysUser::getUserId,Function.identity()));
    var ranks = ExamStatisticsCalculator.ranks(f.candidates);
    List<ExamStatisticsPersonVO> rows = new ArrayList<>();
    for (var c : f.candidates) {
      var attempts = f.attempts.stream().filter(a -> Objects.equals(a.getExamCandidateId(),c.getExamCandidateId())).toList();
      var latest = attempts.stream().max(Comparator.comparing(ExamAttempt::getExamAttemptId)).orElse(null);
      var user = users.get(c.getUserId());
      String name = user == null ? "历史学员" : user.getUserName(), code = user == null ? "" : user.getUserCode();
      if (c.getCandidateProfileSnapshotJson()!=null) {
        try {
          var snapshot = objectMapper.readTree(c.getCandidateProfileSnapshotJson());
          if (snapshot.hasNonNull("userName")) name = snapshot.get("userName").asText();
          if (snapshot.hasNonNull("userCode")) code = snapshot.get("userCode").asText();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
          throw new BusinessException(HttpStatus.CONFLICT,7007,"考试人员档案快照无法解析");
        }
      }
      boolean absent = f.closed && latest == null;
      if (query!=null && Boolean.TRUE.equals(query.getAbsentOnly()) && !absent) continue;
      if (query!=null && query.getLearnerKeyword()!=null && !query.getLearnerKeyword().isBlank() &&
          !(name+" "+code).toLowerCase(Locale.ROOT).contains(query.getLearnerKeyword().trim().toLowerCase(Locale.ROOT))) continue;
      rows.add(new ExamStatisticsPersonVO(c.getExamCandidateId(),c.getUserId(),name,code,c.getCandidateStatus(),
        ExamStatisticsCalculator.scored(c) ? c.getFinalScore() : null,ranks.get(c.getExamCandidateId()),latest!=null,
        attempts.stream().anyMatch(a -> a.getSubmittedTime()!=null),absent,
        latest==null?null:latest.getStartedTime(),latest==null?null:latest.getSubmittedTime(),latest==null?null:latest.getUsedSeconds(),
        latest==null?null:latest.getPlatform(),latest==null?null:latest.getOperatingSystem(),latest==null?null:latest.getBrowser()));
    }
    return rows.stream().sorted(Comparator.comparing(ExamStatisticsPersonVO::examCandidateId)).toList();
  }
  private record Facts(List<ExamCandidate> candidates,List<ExamAttempt> attempts,List<ExamAttemptQuestion> questions,List<ExamAnswer> answers,boolean closed) {}
}
