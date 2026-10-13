package com.sts.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseAuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_exam_attempt_question")
public class ExamAttemptQuestion extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long attemptQuestionId;
  private Long organizationId;
  private Long examAttemptId;
  private Long questionId;
  private Long questionVersionId;
  private String questionType;
  private String stemSnapshot;
  private String analysisSnapshot;
  private String gradingMode;
  private BigDecimal score;
  private String correctAnswerSnapshotJson;
  private Integer sortOrder;
  private Long sourceBankId;
  /**
   * 抽题规则稳定序号；非规则抽题为空。
   */
  private Integer drawRuleIndex;
  /**
   * AUTO 自动判定，RECORD_ONLY 仅记录作答。
   */
  private String evaluationMode;
  /** 作答冻结时的知识点标识数组；历史空值表示未记录，不读取当前关联替代。 */
  private String knowledgePointIdsSnapshotJson;
}
