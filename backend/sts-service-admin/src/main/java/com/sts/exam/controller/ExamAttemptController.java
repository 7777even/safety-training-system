package com.sts.exam.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.exam.dto.*;
import com.sts.exam.service.ExamAttemptAuditQueryService;
import com.sts.exam.service.ExamAttemptQueryService;
import com.sts.exam.service.ExamAttemptService;
import com.sts.exam.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 考试作答、交卷、阅卷、成绩发布及补考管理接口。
 */
@RestController
@RequiredArgsConstructor
public class ExamAttemptController {

  private final ExamAttemptService examAttemptService;
  private final ExamAttemptQueryService examAttemptQueryService;
  private final ExamAttemptAuditQueryService examAttemptAuditQueryService;

  /**
   * 分页查询阅卷任务。
   */
  @GetMapping("/exam/review-tasks")
  @SaCheckPermission("exam:review")
  public PageResult<ExamReviewTaskVO> pageReviewTasks(PageRequest<ExamReviewTaskQuery> request) {
    return examAttemptQueryService.pageReviewTasks(request);
  }

  /**
   * 分页查询作答记录。
   */
  @GetMapping("/exam/attempts")
  @SaCheckPermission("exam:attempt:list")
  public PageResult<ExamAttemptRecordVO> pageAttempts(PageRequest<ExamAttemptRecordQuery> request) {
    return examAttemptQueryService.pageAttempts(request);
  }

  /**
   * 查询防作弊事件列表。
   */
  @GetMapping("/exam/attempts/{examAttemptId}/anticheat-events")
  @SaCheckPermission("exam:attempt:list")
  public java.util.List<ExamAnticheatEventVO> listAnticheatEvents(@PathVariable Long examAttemptId) {
    return examAttemptAuditQueryService.listAnticheatEvents(examAttemptId);
  }

  /**
   * 查询成绩发布日志列表。
   */
  @GetMapping("/exam/attempts/{examAttemptId}/score-publish-logs")
  @SaCheckPermission("exam:score:publish")
  public java.util.List<ExamScorePublishLogVO> listScorePublishLogs(@PathVariable Long examAttemptId) {
    return examAttemptAuditQueryService.listScorePublishLogs(examAttemptId);
  }

  /**
   * 开始一次考试作答。
   */
  @PostMapping("/exam/attempts/start")
  @SaCheckPermission("exam:attempt:start")
  public ExamAttemptVO start(@RequestBody @Valid StartAttemptRequest request) {
    return examAttemptService.start(request);
  }

  /**
   * 查询考试作答。
   */
  @GetMapping("/exam/attempts/{examAttemptId}")
  @SaCheckPermission("exam:attempt:list")
  public ExamAttemptVO get(@PathVariable Long examAttemptId) {
    return examAttemptService.get(examAttemptId);
  }

  /**
   * 保存作答答案。
   */
  @PutMapping("/exam/attempts/{examAttemptId}/answers")
  @SaCheckPermission("exam:attempt:answer")
  public void saveAnswers(@PathVariable Long examAttemptId,
                          @RequestBody @Valid SaveAnswersRequest request) {
    examAttemptService.saveAnswers(examAttemptId, request);
  }

  /**
   * 提交考试作答。
   */
  @PostMapping("/exam/attempts/{examAttemptId}/submit")
  @SaCheckPermission("exam:attempt:submit")
  public ExamAttemptVO submit(@PathVariable Long examAttemptId,
                              @RequestBody(required = false) SubmitAttemptRequest request) {
    return examAttemptService.submit(examAttemptId,
      request == null ? new SubmitAttemptRequest() : request);
  }

  /**
   * 超时自动交卷。
   */
  @PostMapping("/exam/attempts/{examAttemptId}/timeout-submit")
  @SaCheckPermission("exam:attempt:submit")
  public ExamAttemptVO timeoutSubmit(@PathVariable Long examAttemptId) {
    return examAttemptService.timeoutSubmit(examAttemptId);
  }

  /**
   * 提交题目人工阅卷结果。
   */
  @PostMapping("/exam/answers/{examAnswerId}/review")
  @SaCheckPermission("exam:review")
  public void review(@PathVariable Long examAnswerId,
                     @RequestBody @Valid ReviewAnswerRequest request) {
    examAttemptService.reviewAnswer(examAnswerId, request);
  }

  /**
   * 发布成绩。
   */
  @PostMapping("/exam/attempts/{examAttemptId}/publish-score")
  @SaCheckPermission("exam:score:publish")
  public ExamAttemptVO publishScore(@PathVariable Long examAttemptId,
                                    @RequestParam(required = false) Long publishedBy) {
    return examAttemptService.publishScore(examAttemptId, publishedBy);
  }

  /**
   * 为符合策略的考生开补考或恢复进行中补考。
   */
  @PostMapping("/exam/candidates/{examCandidateId}/retake")
  @SaCheckPermission("exam:attempt:start")
  public ExamAttemptVO retake(@PathVariable Long examCandidateId,
                              @RequestParam String clientAttemptToken) {
    return examAttemptService.retake(examCandidateId, clientAttemptToken);
  }

  /**
   * 重置练习作答。
   */
  @PostMapping("/exam/candidates/{examCandidateId}/practice-reset")
  @SaCheckPermission("exam:exam:edit")
  public void practiceReset(@PathVariable Long examCandidateId,
                            @RequestBody @Valid PracticeResetRequest request) {
    examAttemptService.practiceReset(examCandidateId, request);
  }

  /**
   * 记录考试防作弊事件。
   */
  @PostMapping("/exam/attempts/{examAttemptId}/anticheat-events")
  @SaCheckPermission("exam:attempt:answer")
  public void anticheat(@PathVariable Long examAttemptId,
                        @RequestBody @Valid AnticheatEventRequest request) {
    examAttemptService.recordAnticheat(examAttemptId, request);
  }
}
