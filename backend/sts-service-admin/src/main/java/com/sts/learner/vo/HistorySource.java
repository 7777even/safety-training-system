package com.sts.learner.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 原系统只读摘要投影；不会回写任何来源记录。 */
@Data
public class HistorySource {
  /** COURSE/FORMAL_EXAM/COURSE_QUIZ/PRACTICE/SIMULATION/TRAINING。 */
  private String recordType;
  /** 来源记录主键，按类型区分。 */
  private Long recordId;
  /** 来源名称或冻结题源名称。 */
  private String title;
  /** 原记录发生时间。 */
  private LocalDateTime occurredTime;
  /** 原领域状态。 */
  private String status;
  /** 来源已有身份展示；考试使用分配时快照。 */
  private String userName;
  /** 来源已有账号展示。 */
  private String userCode;
  /** 原领域摘要成绩，不能自行重算。 */
  private BigDecimal score;
  /** 原摘要已累计的学时。 */
  private BigDecimal hours;
  /** 本人是否允许看到成绩。 */
  private boolean scoreVisible;
  /** 可更正成绩的领域上限；不评分记录为空。 */
  private BigDecimal maxScore;
  /** 原入口类型，培训考核仍从培训进入。 */
  private String referenceKind;
  /** 原入口资源主键。 */
  private Long resourceId;
  /** 原考试或计划主键。 */
  private Long parentId;
  /** 原随堂测试内容项。 */
  private Long itemId;
  /** 原答卷主键；不暴露答案。 */
  private Long attemptId;
  /** 原入口动作，仅用于路由映射。 */
  private String action;
}
