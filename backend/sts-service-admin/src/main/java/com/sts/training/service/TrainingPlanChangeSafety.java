package com.sts.training.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.common.exception.BusinessException;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.port.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
/** 完整变更保护已存在的安排、学习及成绩，不以计划状态代替事实检查。 */
@Service @RequiredArgsConstructor public class TrainingPlanChangeSafety {
  /** 内容所属阶段。 */ private final TrainingPlanStageMapper stages;
  /** 当前编排。 */ private final TrainingPlanContentMapper contents;
  /** 已生成执行。 */ private final TrainingExecutionLinkMapper links;
  /** 线下安排。 */ private final TrainingOfflineSessionMapper sessions;
  /** 具体参训人。 */ private final TrainingParticipantMapper participants;
  /** 已有逐内容结果。 */ private final TrainingParticipantContentResultMapper results;
  /** 课程事实端口。 */ private final TrainingCourseExecutionPort course;
  /** 作答事实端口。 */ private final TrainingAssessmentExecutionPort exams;
  /** 现有跨域端口无法原子撤销重建安排时明确拒绝，零安排才可完整替换。 */
  public void requireFullChangeSafe(TrainingPlan plan) {
    if(!"PUBLISHED".equals(plan.getPublishStatus())) throw blocked();
    Long id=plan.getTrainingPlanId();
    if(links.selectCount(new LambdaQueryWrapper<TrainingExecutionLink>().eq(TrainingExecutionLink::getTrainingPlanId,id))>0
      || sessions.selectCount(new LambdaQueryWrapper<TrainingOfflineSession>().eq(TrainingOfflineSession::getTrainingPlanId,id))>0) throw blocked();
    var stageIds=stages.selectList(new LambdaQueryWrapper<TrainingPlanStage>().eq(TrainingPlanStage::getTrainingPlanId,id)).stream().map(TrainingPlanStage::getPlanStageId).toList();
    if(!stageIds.isEmpty()) for(var c:contents.selectList(new LambdaQueryWrapper<TrainingPlanContent>().in(TrainingPlanContent::getPlanStageId,stageIds))) {
      // 开课发布失败可能已留下草稿开课，不能仅凭没有执行Link就覆盖。
      if("COURSE".equals(c.getContentType()) && course.findOfferingIdByPlanContentId(c.getPlanContentId())!=null) throw blocked();
    }
    for(var p:participants.selectList(new LambdaQueryWrapper<TrainingParticipant>().eq(TrainingParticipant::getTrainingPlanId,id))) {
      if(course.existsEnrollmentForParticipant(p.getTrainingParticipantId()) || exams.existsCandidateForParticipant(p.getTrainingParticipantId())
        || results.selectCount(new LambdaQueryWrapper<TrainingParticipantContentResult>().eq(TrainingParticipantContentResult::getTrainingParticipantId,p.getTrainingParticipantId()))>0) throw blocked();
    }
  }
  private BusinessException blocked() {return new BusinessException(HttpStatus.CONFLICT,8351,"已有执行安排或学习/作答/结果事实，无法安全同步完整变更；请仅申请名称、负责人、地点和说明变更");}
}
