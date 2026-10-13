package com.sts.training.exception;

import com.sts.common.exception.BusinessException;
import com.sts.training.vo.TrainingPlanEndIssueVO;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 计划结束门禁不满足。HTTP 409，业务码 8160。
 */
public class TrainingPlanEndBlockedException extends BusinessException {

  private final List<TrainingPlanEndIssueVO> issues;

  public TrainingPlanEndBlockedException(List<TrainingPlanEndIssueVO> issues) {
    super(HttpStatus.CONFLICT, 8160, format(issues));
    this.issues = List.copyOf(issues);
  }

  public List<TrainingPlanEndIssueVO> getIssues() {
    return issues;
  }

  private static String format(List<TrainingPlanEndIssueVO> issues) {
    return "结束被拒绝：" + issues.stream()
      .map(issue -> "participantId=" + issue.getTrainingParticipantId()
        + ",planContentId=" + issue.getPlanContentId()
        + ",reason=" + issue.getReasonCode())
      .collect(Collectors.joining("; "));
  }
}
