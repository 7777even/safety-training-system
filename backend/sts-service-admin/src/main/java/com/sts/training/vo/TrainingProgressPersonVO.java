package com.sts.training.vo;
import java.math.BigDecimal;
import java.util.List;
/** 学员必修进度与正式结果，不以进度百分比替代通过结论。 */
public record TrainingProgressPersonVO(Long trainingParticipantId,Long userId,String learnerName,String learnerCode,String resultStatus,long requiredCount,long completedContentCount,BigDecimal progressPercent,BigDecimal onlineHours,BigDecimal offlineHours,boolean finalized,List<TrainingParticipantResultItemVO> items) {}
