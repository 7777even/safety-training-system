package com.sts.course.snapshot;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({
  "courseItemId", "itemType", "itemTitle", "assetId", "questionBankId", "questionBankName",
  "paperVersionId", "paperId", "paperName", "paperVersionNo", "paperPublishedStatus",
  "isRequired", "minimumStudySeconds",
  "sortOrder", "status", "liveSession"
})
public class ItemSnapshot {
  private Long courseItemId;
  private String itemType;
  private String itemTitle;
  private Long assetId;
  /** 课件关联的同课程测验ID，可空；多个课件可复用同一测验。 */
  private Long linkedQuizItemId;
  /** 结业测评标记，仅QUIZ可启用，旧版本空值沿用普通测验语义。 */
  private Integer isFinalAssessment;
  /** 结业及格线覆盖值，空值沿用已发布试卷。 */
  private java.math.BigDecimal passScore;
  private Long questionBankId;
  private String questionBankName;
  private Long paperVersionId;
  private Long paperId;
  private String paperName;
  private Integer paperVersionNo;
  private String paperPublishedStatus;
  private Integer isRequired;
  private Integer minimumStudySeconds;
  private Integer sortOrder;
  private Integer status;
  /** 仅用于反序列化旧课程版本，新快照不再写入材料规则。 */
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private MaterialRuleSnapshot materialRule;
  private LiveSessionSnapshot liveSession;
}
