package com.sts.archive.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 一期/企业/个人档案详情；缺失项不为空时不得当作正式档案完成。
 * <p>三视角共用计划、人员、成绩等基础事实；专家查课仅在一期档案返回。
 */
@Data
public class ArchiveViewVO {
  private Long trainingPlanId;
  private String planCode;
  private String planName;
  private String publishStatus;
  private Long organizationId;
  private String organizationName;
  private Long participantCount;
  private List<String> missingItems;
  private Object plan;
  private Object organization;
  /** 培训内容清单（内容名称、类型、是否必修、权重与要求学时）。 */
  private Object contents;
  private Object participants;
  /** 签到情况：由已录入的线下成绩映射，计划无线下内容时标记为不适用。 */
  private Object attendance;
  private Object scores;
  /** 综合评价：综合分与结果状态。 */
  private Object evaluations;
  /** 仅一期档案返回当前专家检查版本的已提交课程记录和冻结项目答案。 */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Object expertInspections;
  private Object rewardPenalties;
  /** 影像记录（attachmentType 为 IMAGE/VIDEO 的培训资料）。 */
  private Object materials;
  private Object attachments;
  /**
   * 证书清单；调用方缺少 {@code certificate:cert:list} 时不返回该字段，
   * 不能以“返回清单但禁用下载”代替。
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Object certificates;
  /** 数据统计：人数、完成情况、比率、学时与资料数量。 */
  private Object statistics;
  private Long fileInfoId;
  /** 当前归档文件是否为 PDF 正本；历史 JSON 档案为 false，页面据此提示重新更新档案。 */
  private Boolean archiveFileIsPdf;
}
