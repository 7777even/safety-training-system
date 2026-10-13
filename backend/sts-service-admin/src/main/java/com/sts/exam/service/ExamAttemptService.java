package com.sts.exam.service;

import com.sts.exam.dto.*;
import com.sts.exam.vo.ExamAttemptVO;

public interface ExamAttemptService {
  ExamAttemptVO start(StartAttemptRequest request);

  ExamAttemptVO get(Long examAttemptId);

  void saveAnswers(Long examAttemptId, SaveAnswersRequest request);

  ExamAttemptVO submit(Long examAttemptId, SubmitAttemptRequest request);

  ExamAttemptVO timeoutSubmit(Long examAttemptId);

  void reviewAnswer(Long examAnswerId, ReviewAnswerRequest request);

  void reviewCourseAnswer(Long courseOfferingId, Long courseItemId,
                          Long examAnswerId, ReviewAnswerRequest request);

  ExamAttemptVO publishScore(Long examAttemptId, Long publishedBy);

  ExamAttemptVO retake(Long examCandidateId, String clientAttemptToken);

  void practiceReset(Long examCandidateId, PracticeResetRequest request);

  void recordAnticheat(Long examAttemptId, AnticheatEventRequest request);

  /** 课程回接：考生是否已有已发布合格成绩 */
  boolean hasPublishedPass(Long examId, Long userId);
}
