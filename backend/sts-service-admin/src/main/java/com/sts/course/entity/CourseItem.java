package com.sts.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_course_item")
public class CourseItem extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long courseItemId;
  private Long courseId;
  private Long chapterId;
  private String itemType;
  private String itemTitle;
  private Long assetId;
  /** 课件关联的同课程测验ID，可空；多个课件可复用同一测验。 */
  @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
  private Long linkedQuizItemId;
  /** 结业测评标记，仅QUIZ可启用，旧版本空值沿用普通测验语义。 */
  private Integer isFinalAssessment;
  /** 结业及格线覆盖值，空值沿用已发布试卷。 */
  @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
  private java.math.BigDecimal passScore;
  private Long paperVersionId;
  private Integer isRequired;
  private Integer minimumStudySeconds;
  private Integer sortOrder;
  private Integer isLockedAfterParticipation;
  private Integer status;
}
