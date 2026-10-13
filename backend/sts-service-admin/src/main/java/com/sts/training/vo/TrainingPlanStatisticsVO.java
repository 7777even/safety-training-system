package com.sts.training.vo;
import java.math.BigDecimal;
/** 一个计划的实时或冻结结果汇总，未通过与未完成分列。 */
public record TrainingPlanStatisticsVO(Long trainingPlanId,String planName,String publishStatus,long expectedCount,long completedCount,long failedCount,long incompleteCount,BigDecimal completionRate) {}
