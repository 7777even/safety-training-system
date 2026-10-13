package com.sts.training.vo;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
/** 线下课堂内容和已录成绩，未评分保持独立。 */
public record OfflineClassStatisticsVO(Long offlineSessionId,String sessionName,String contentName,String location,
  List<ExamContent> examContents,long expectedCount,long scoredCount,long unscoredCount,List<Assessment> assessments,List<Person> people) {
  /** 同阶段既有考试及其试卷关联；不宣称属于该场次独立答卷。 */
  public record ExamContent(String examName,Long paperVersionId,String paperName,Integer versionNo) {}
  /** 每种课堂考核名称分别统计，不混合不同考核成绩。 */
  public record Assessment(String scoreName,long scoredCount,long unscoredCount,BigDecimal highestPercent,BigDecimal averagePercent,BigDecimal lowestPercent,List<Bucket> scoreBands) {}
  /** 百分制成绩分布。 */
  public record Bucket(String label,long count,BigDecimal percent) {}
  /** 学员录分记录；未评分者的分数字段为空。 */
  public record Person(Long trainingParticipantId,Long userId,String learnerName,String scoreName,BigDecimal score,BigDecimal maxScore,LocalDateTime scoredTime) {}
}
