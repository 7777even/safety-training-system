package com.sts.training.port;

import com.sts.exam.vo.ExamAttemptVO;
import com.sts.training.vo.TrainingLearnerExamNoticeVO;

import java.util.List;

/**
 * 培训域调用考试执行、Candidate 与开考的内部端口。培训不得直接写考试 Mapper。
 */
public interface TrainingAssessmentExecutionPort {

  /**
   * 按 planContentId 创建或复用计划专属 Exam 与 AssessmentExecution，返回 assessmentExecutionId。
   */
  Long ensureExamExecution(TrainingExamExecutionCommand command);

  /**
   * 为参训人创建或复用 assignmentSource=TRAINING 的 Candidate。
   */
  void ensureCandidates(Long assessmentExecutionId, Long planContentId,
                        List<TrainingParticipantBinding> participants);

  /**
   * 为当前登录学员创建或恢复 Attempt。
   */
  ExamAttemptVO startAttempt(Long assessmentExecutionId, Long trainingParticipantId,
                             Long currentUserId, String clientAttemptToken,
                             String platform, String operatingSystem,
                             String browser, String clientIp);

  /** 读取计划专属理论考试的冻结须知；不得创建 Candidate 或 Attempt。 */
  TrainingLearnerExamNoticeVO getLearnerExamNotice(Long assessmentExecutionId);

  /**
   * 读取指定参训人在该考核执行上的正式结果；尚未发布成绩时 score 为空。
   */
  TrainingAssessmentResult readResult(Long assessmentExecutionId, Long trainingParticipantId);

  /**
   * 参训人是否已有考试 Candidate 事实，供草稿移除护栏使用。
   */
  boolean existsCandidateForParticipant(Long trainingParticipantId);

  /**
   * 返回该考核执行中仍有进行中 Attempt 的参训人 ID，供计划结束门禁使用。
   */
  List<Long> listInProgressAttemptParticipantIds(Long assessmentExecutionId);
}
