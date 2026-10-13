package com.sts.training.service;
import com.sts.training.vo.*;
import java.util.*;
import java.util.stream.Collectors;
/** 按计划、部门、学员归并，混合模式分别统计。 */
public final class TrainingClassStatisticsCalculator {
  private TrainingClassStatisticsCalculator() {}
  /** 重复内容不能放大班级人数。 */
  public static TrainingClassStatisticsVO calculate(List<TrainingClassPersonVO> rows) {
    var groups=rows.stream().collect(Collectors.groupingBy(p->List.of(p.trainingPlanId(),p.deptId()==null?0L:p.deptId(),p.mode()),LinkedHashMap::new,Collectors.toList()));
    var classes=groups.values().stream().map(g->{var p=g.get(0);return new TrainingClassStatisticsVO.ClassGroup(p.trainingPlanId(),p.planName(),p.deptId(),p.deptName(),p.mode(),g.stream().map(TrainingClassPersonVO::userId).distinct().count());}).toList();
    return new TrainingClassStatisticsVO(count(rows,null),count(rows,"ONLINE"),count(rows,"OFFLINE"),classes);
  }
  /** 同一计划学员在两个模式中各计一次，总计仍为一次。 */
  private static long count(List<TrainingClassPersonVO> rows,String mode) {
    return rows.stream().filter(p->mode==null||mode.equals(p.mode())).map(p->List.of(p.trainingPlanId(),p.userId())).distinct().count();
  }
}
