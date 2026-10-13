package com.sts.training.vo;
/** 班级人员事实；mode 为线上或线下，空部门单列。 */
public record TrainingClassPersonVO(Long trainingPlanId,String planName,Long deptId,String deptName,Long userId,String learnerName,String learnerCode,String mode) {}
