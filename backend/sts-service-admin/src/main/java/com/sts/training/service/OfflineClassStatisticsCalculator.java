package com.sts.training.service;
import com.sts.training.entity.TrainingOfflineScore;
import com.sts.training.vo.OfflineClassStatisticsVO.*;
import com.sts.exam.service.ExamStatisticsCalculator;
import java.math.*;
import java.util.*;
/** 线下成绩按满分归一化，缺少成绩不计零分。 */
public final class OfflineClassStatisticsCalculator {
  private OfflineClassStatisticsCalculator() {}
  /** 不同考核分开计算，人员数去重。 */
  public static Assessment assessment(String name,long expected,List<TrainingOfflineScore> rows) {
    var values=rows.stream().filter(s->s.getScore()!=null && s.getMaxScore()!=null && s.getMaxScore().signum()>0)
      .map(s->s.getScore().multiply(BigDecimal.valueOf(100)).divide(s.getMaxScore(),4,RoundingMode.HALF_UP)).toList();
    long count=rows.stream().filter(s->s.getScore()!=null).map(TrainingOfflineScore::getTrainingParticipantId).distinct().count();
    long[] bands=new long[5];String[] labels={"0–<60","60–<70","70–<80","80–<90","90–100"};
    for(var v:values) {int i=v.compareTo(BigDecimal.valueOf(60))<0?0:v.compareTo(BigDecimal.valueOf(70))<0?1:v.compareTo(BigDecimal.valueOf(80))<0?2:v.compareTo(BigDecimal.valueOf(90))<0?3:4;bands[i]++;}
    List<Bucket> buckets=new ArrayList<>();for(int i=0;i<5;i++)buckets.add(new Bucket(labels[i],bands[i],ExamStatisticsCalculator.percent(bands[i],values.size())));
    return new Assessment(name,count,Math.max(0,expected-count),values.stream().max(BigDecimal::compareTo).orElse(null),
      values.isEmpty()?null:values.stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(values.size()),2,RoundingMode.HALF_UP),
      values.stream().min(BigDecimal::compareTo).orElse(null),buckets);
  }
}
