package com.sts.training.service;

import com.sts.training.entity.TrainingParticipant;
import com.sts.training.entity.TrainingPlanContent;
import com.sts.training.port.TrainingResultRefresh;
import com.sts.training.vo.TrainingParticipantResultsVO;

import java.util.List;

/**
 * 培训逐内容结果投影的建立、同步、聚合与结束冻结。
 */
public interface TrainingResultSyncService extends TrainingResultRefresh {

  /**
   * 为每个参训人幂等建立 PENDING 投影行。
   */
  void ensurePending(Long trainingPlanId, TrainingPlanContent content, Long executionId,
                     List<TrainingParticipant> participants);

  /**
   * 从课程、考试、线下正式来源同步投影并重建参训人聚合字段。
   */
  List<TrainingParticipantResultsVO> synchronize(Long trainingPlanId);

  /**
   * 计算综合成绩前按单个参训人刷新全部内容投影和成绩聚合；已冻结结果保持不变。
   */
  void synchronizeParticipant(Long trainingParticipantId);

  /**
   * 按参训人+内容项幂等刷新投影并重建该人聚合；已冻结则忽略。
   */
  void synchronizeParticipantContent(Long trainingParticipantId, Long planContentId);

  @Override
  default void refreshParticipantContent(Long trainingParticipantId, Long planContentId) {
    synchronizeParticipantContent(trainingParticipantId, planContentId);
  }

  /**
   * 读取投影重建的结果；不触发同步。
   */
  TrainingParticipantResultsVO getResults(Long trainingPlanId, Long trainingParticipantId);

  /**
   * 结束时冻结全部投影与参训人最终快照。调用前须已同步且门禁通过。
   */
  void freezePlan(Long trainingPlanId);

  /** 统计读取：执行中从来源实时推导，已结束读取冻结结果，全程不写入。 */
  TrainingParticipantResultsVO getCurrentResults(Long trainingPlanId, Long trainingParticipantId);
}
