package com.sts.training.port;

import java.util.List;

/**
 * 培训域调用课程开课与报名的内部端口。培训不得直接写课程 Mapper。
 */
public interface TrainingCourseExecutionPort {

  /**
   * 按 planContentId 创建或复用冻结版本的指派开课草稿，返回 courseOfferingId。
   * 只落下开课和测验执行，不发布、不报名。
   */
  Long ensureOffering(TrainingCourseOfferingCommand command);

  /**
   * 调用开课发布：已有版本则用已有，否则取课程当前版本；缺阅卷人抛 5068，开课保持草稿。
   */
  void publishOffering(Long courseOfferingId);

  /**
   * 按计划内容查找已落下的开课 ID；尚未生成时返回 null。
   */
  Long findOfferingIdByPlanContentId(Long planContentId);

  /**
   * 为参训人创建或复用 enrollmentSource=TRAINING 的报名。
   */
  void ensureEnrollments(Long courseOfferingId, Long planContentId, List<TrainingParticipantBinding> participants);

  /**
   * 读取指定参训人在该开课上的正式学习结果；尚无报名时返回 null。
   */
  TrainingCourseResult readResult(Long courseOfferingId, Long trainingParticipantId);

  /**
   * 为指定培训报名补齐缺失的课程正式成绩；独立课程不处理。
   */
  void reconcileTrainingFinalScore(Long courseOfferingId, Long trainingParticipantId);

  /**
   * 参训人是否已有课程报名事实，供草稿移除护栏使用。
   */
  boolean existsEnrollmentForParticipant(Long trainingParticipantId);

  /**
   * 返回该开课中仍在学习中的参训人 ID，供计划结束门禁使用。
   */
  List<Long> listInProgressLearningParticipantIds(Long courseOfferingId);
}
