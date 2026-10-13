package com.sts.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseEntity;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_question_user_state")
public class QuestionUserState extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long userQuestionStateId;
  private Long organizationId;
  private Long userId;
  private Long questionId;
  private Integer isFavorite;
  private Integer wrongCount;
  private Integer correctCount;
  private LocalDateTime lastAnswerTime;
  private LocalDateTime lastWrongTime;
  private Long lastExamAttemptId;
  /** 最近一次已处理错题事实标识；新错答标识变化后恢复。 */
  private String handledWrongKey;
  /** DELETED或MASTERED，不影响原答卷。 */
  private String wrongDisposition;
}
