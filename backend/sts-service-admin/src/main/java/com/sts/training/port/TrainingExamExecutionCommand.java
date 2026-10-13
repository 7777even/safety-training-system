package com.sts.training.port;

import com.sts.training.dto.TrainingExamPolicySnapshot;
import com.sts.training.dto.TrainingExamReviewerSnapshot;
import com.sts.training.dto.TrainingQuestionBankDrawRuleRequest;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 培训域请求考试域创建计划专属 Exam 与 AssessmentExecution 的命令。
 */
@Data
public class TrainingExamExecutionCommand {
  /**
   * 计划所属机构 ID。
   */
  private Long organizationId;
  /**
   * 培训计划内容 ID，作为计划专属考试幂等键。
   */
  private Long planContentId;
  /**
   * 内容类型：EXAM 或 QUESTION_BANK。
   */
  private String contentType;
  /**
   * 考核类型：QUIZ、FORMAL_EXAM 或 BANK_PRACTICE。
   */
  private String assessmentKind;
  /**
   * 执行名称。
   */
  private String examName;
  /**
   * EXAM：来源考试 ID，只作配置来源，不共享其 Candidate。
   */
  private Long sourceExamId;
  /**
   * EXAM：冻结的试卷版本 ID。
   */
  private Long paperVersionId;
  /**
   * QUESTION_BANK：题库 ID。
   */
  private Long questionBankId;
  /**
   * 开放开始时间（阶段时间，缺失时为计划时间）。
   */
  private LocalDateTime openStartTime;
  /**
   * 开放结束时间（阶段时间，缺失时为计划时间）。
   */
  private LocalDateTime openEndTime;
  /**
   * QUESTION_BANK：练习时长秒数。
   */
  private Integer durationSeconds;
  /**
   * QUESTION_BANK：及格分数。EXAM：发布快照中的及格分。
   */
  private BigDecimal passScore;
  /**
   * EXAM：发布快照中的及格比例。
   */
  private BigDecimal passRatio;
  /**
   * EXAM：发布快照中的题目顺序。
   */
  private String questionOrderMode;
  /**
   * EXAM：发布快照中的多选计分。
   */
  private String multiSelectScoring;
  /**
   * QUESTION_BANK：最大作答次数。EXAM：由快照补考次数换算。
   */
  private Integer maxAttempts;
  /**
   * EXAM：成绩可见性策略。
   */
  private String answerDetailMode;
  private Integer isAnalysisVisible;
  private Integer isScoreVisible;
  /**
   * QUESTION_BANK：抽题规则，写入执行策略快照。
   */
  private List<TrainingQuestionBankDrawRuleRequest> drawRules;
  /**
   * QUESTION_BANK：发布时冻结题池的指纹与候选版本。
   */
  private String questionPoolHash;
  private List<TrainingQuestionPoolItem> questionPool;
  /**
   * EXAM：来源考试编码，仅作审计，不回读来源。
   */
  private String sourceExamCode;
  /**
   * EXAM：分类、封面、负责人、通知和学分，来自 v2 快照。
   */
  private Long examCategoryId;
  private Long coverFileId;
  private Long responsibleUserId;
  private Integer isNoticeEnabled;
  private String noticeContent;
  private BigDecimal credit;
  /**
   * EXAM：发布快照规范化 hash，写入 AssessmentExecution 策略指纹。
   */
  private String resourceSnapshotHash;
  /**
   * EXAM：完整策略快照。
   */
  private TrainingExamPolicySnapshot policy;
  /**
   * EXAM：冻结的计划专属批阅人。
   */
  private List<TrainingExamReviewerSnapshot> reviewers;
}
