package com.sts.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExamVO {
  private Long examId;
  private Long organizationId;
  private String examCode;
  private String examName;
  private String examType;
  private Long examCategoryId;
  private Long coverFileId;
  private Long paperVersionId;
  /** 试卷版本所属试卷名称；未绑定或资源已不存在时为空。 */
  private String paperName;
  /** 试卷版本号；未绑定或资源已不存在时为空。 */
  private Integer paperVersionNo;
  private Long questionBankId;
  /** 题库名称；未绑定或资源已不存在时为空。 */
  private String questionBankName;
  /**
   * 题库练习抽题规则；仅 BANK_PRACTICE 且已配置时返回，其他类型为空。
   */
  private List<ExamDrawRuleVO> drawRules;
  /**
   * 来源培训计划内容 ID；独立考试为空，培训生成的计划专属考试非空。
   */
  private Long planContentId;
  private Long responsibleUserId;
  private LocalDateTime openStartTime;
  private LocalDateTime openEndTime;
  private Integer durationSeconds;
  private BigDecimal passRatio;
  private BigDecimal passScore;
  private BigDecimal credit;
  private String questionOrderMode;
  private String multiSelectScoring;
  private Integer isNoticeEnabled;
  private String noticeContent;
  private String publishStatus;
  private LocalDateTime publishedTime;
  private Integer status;
  private Integer version;
  private LocalDateTime createTime;
  /**
   * 考试策略；仅详情接口返回，分页列表为 null。
   */
  private ExamPolicyVO policy;
}
