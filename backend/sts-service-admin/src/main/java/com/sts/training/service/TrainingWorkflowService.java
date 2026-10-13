package com.sts.training.service;

import com.sts.exam.vo.ExamAttemptVO;
import com.sts.training.dto.TrainingExamAttemptStartRequest;
import com.sts.training.dto.TrainingLearnerEnterRequest;
import com.sts.training.vo.*;

import java.util.List;

public interface TrainingWorkflowService {
  void removeParticipant(Long trainingPlanId, Long trainingParticipantId);

  TrainingTeacherTaskDetailVO getTeacherTask(Long offlineSessionId, Long teacherUserId);

  List<TrainingExecutionLinkVO> listExecutions(Long trainingPlanId);

  TrainingExecutionLinkVO provision(Long trainingPlanId, Long planContentId);

  ExamAttemptVO startExam(Long trainingParticipantId, Long planContentId,
                          TrainingExamAttemptStartRequest request, Long currentUserId);

  TrainingLearnerEnterVO enter(Long trainingParticipantId, Long planContentId,
                               TrainingLearnerEnterRequest request, Long currentUserId);

  /** 读取本人培训理论考试须知，不开始作答。 */
  TrainingLearnerExamNoticeVO getLearnerExamNotice(Long trainingParticipantId, Long planContentId,
                                                    Long currentUserId);

  List<TrainingLearnerTaskVO> listLearnerTasks(Long currentUserId);

  /**
   * 按计划 ID 打开当前学员可见培训的类型封面。
   */
  TrainingCoverFile openLearnerPlanCover(Long trainingPlanId, Long currentUserId);

  TrainingLearnerParticipantDetailVO getLearnerParticipant(Long trainingParticipantId, Long currentUserId);

  /**
   * 学员只读本人线下场次详情。按登录用户派生参训人。
   */
  TrainingLearnerOfflineSessionVO getLearnerOfflineSession(Long offlineSessionId, Long currentUserId);

  /**
   * 学员只读本人参训计划下启用的一般资料。主体强制为当前登录学员，不接受覆盖主体的参数。
   */
  List<TrainingLearnerMaterialVO> listLearnerMaterials(Long trainingParticipantId, Long currentUserId);

  /**
   * 学员受控读取本人参训计划下一般资料的原件；只按资料记录读取，不接受调用方传入的文件 ID。
   */
  TrainingAttachmentFile openLearnerMaterialFile(Long trainingParticipantId, Long trainingAttachmentId,
                                                 Long currentUserId);

  List<TrainingParticipantResultsVO> synchronizeResults(Long trainingPlanId);

  TrainingParticipantResultsVO getResults(Long trainingPlanId, Long trainingParticipantId);

  /**
   * 仅已结束培训返回正式结果汇总、学员详情、奖惩和基础统计。
   */
  TrainingEndedResultVO getEndedResult(Long trainingPlanId);
}
