package com.sts.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseAuditEntity;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_question_version")
public class QuestionVersion extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long questionVersionId;
  private Long questionId;
  private Integer versionNo;
  private String questionType;
  private String stem;
  private String analysis;
  private String standardAnswer;
  private String difficulty;
  private String gradingMode;
  private Integer isUnorderedAnswerAllowed;
  private Integer isCaseIgnored;
  private Integer isFullHalfWidthIgnored;
  private String publishedStatus;
  private Long publishedBy;
  private LocalDateTime publishedTime;
  private String versionNote;
}
