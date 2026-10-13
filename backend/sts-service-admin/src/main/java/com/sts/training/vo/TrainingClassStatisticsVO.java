package com.sts.training.vo;
import java.util.List;
/** 班级统计，总人数按计划和学员去重，模式之间允许重叠。 */
public record TrainingClassStatisticsVO(long totalParticipantCount,long onlineParticipantCount,long offlineParticipantCount,List<ClassGroup> classes) {
  /** 一个计划下一个部门及一种培训模式的班级。 */
  public record ClassGroup(Long trainingPlanId,String planName,Long deptId,String deptName,String mode,long participantCount) {}
}
