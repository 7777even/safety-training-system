package com.sts.course.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ItemCreateRequest {
  @NotNull(message = "章节ID不能为空")
  private Long chapterId;

  @NotBlank(message = "内容项类型不能为空")
  private String itemType;

  @NotBlank(message = "内容项标题不能为空")
  private String itemTitle;

  private Long assetId;
  /** 课件关联的同课程测验ID，可空；多个课件可复用同一测验。 */
  private Long linkedQuizItemId;
  /** 结业测评标记，仅QUIZ可启用，旧版本空值沿用普通测验语义。 */
  @jakarta.validation.constraints.Min(0)
  @jakarta.validation.constraints.Max(1)
  private Integer isFinalAssessment;
  /** 结业及格线覆盖值，空值沿用已发布试卷。 */
  @jakarta.validation.constraints.DecimalMin("0")
  private java.math.BigDecimal passScore;

  /**
   * QUESTION_BANK 类型直接引用的题库ID。
   */
  private Long questionBankId;

  /**
   * QUIZ 类型直接引用的已发布试卷版本ID。
   */
  private Long paperVersionId;

  @NotNull(message = "是否必修不能为空")
  private Integer isRequired;

  /** ASSET 内容项累计有效学习达到该秒数后完成，其他类型固定为 0。 */
  @NotNull(message = "要求学习时长不能为空")
  private Integer minimumStudySeconds;

  @NotNull(message = "排序不能为空")
  private Integer sortOrder;

  @NotNull(message = "启用状态不能为空")
  private Integer status;

  private Integer isLockedAfterParticipation = 0;

  @Valid
  private LiveSessionCreatePayload liveSession;
}
