package com.sts.course.vo;
import java.math.BigDecimal;
import java.util.List;
/** 课程及分类统计，时长单位为秒，人数按学员去重。 */
public record CourseStatisticsVO(Summary summary, List<Group> offerings, List<Group> categories, List<Knowledge> knowledgePoints, long historicalKnowledgeMissingCount) {
  /** 当前范围学习参与汇总。 */
  public record Summary(long enrolledCount,long participatedCount,long completedCount,long totalStudySeconds,long studyCount,BigDecimal averageProgress) {}
  /** 开班或分类汇总，分类之间允许重叠。 */
  public record Group(Long id,String name,Summary summary) {}
  /** 冻结知识点覆盖、已答及已判定正确率。 */
  public record Knowledge(Long knowledgePointId,String knowledgeName,long questionCount,long answeredCount,long evaluatedCount,long correctCount,BigDecimal accuracyRate) {}
}
