package com.sts.course.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CourseUpdateRequest {
  @NotBlank(message = "课程名称不能为空")
  private String courseName;
  /** 本机构主讲教师用户ID，旧课程可空。 */
  private Long teacherUserId;

  private Long coverFileId;

  private String introduction;
  private String teachingObjective;
  private String syllabus;
  private String assessmentMethod;

  @NotNull(message = "学分不能为空")
  @DecimalMin(value = "0", message = "学分不能为负")
  private BigDecimal credit;

  @NotBlank(message = "积分奖励模式不能为空")
  @Pattern(regexp = "NONE|PER_ITEM|COURSE", message = "积分奖励模式仅支持 NONE、PER_ITEM 或 COURSE")
  private String pointMode;

  @NotNull(message = "积分值不能为空")
  @DecimalMin(value = "0", message = "积分值不能为负")
  private BigDecimal pointValue;

  @NotBlank(message = "源文件下载模式不能为空")
  @Pattern(regexp = "NONE|ALL|PARTIAL", message = "源文件下载模式仅支持 NONE、ALL 或 PARTIAL")
  private String sourceDownloadMode;

  @NotNull(message = "是否允许倍速播放不能为空")
  private Integer isSpeedPlayAllowed;

  @NotNull(message = "是否启用学习防作弊不能为空")
  private Integer isStudyAnticheatEnabled;

  @NotNull(message = "是否显示学时说明不能为空")
  private Integer isStudyDescriptionEnabled;

  @NotNull(message = "是否启用水印不能为空")
  private Integer isWatermarkEnabled;

  @NotNull(message = "状态不能为空")
  private Integer status;

  @NotNull(message = "版本号不能为空")
  private Integer version;
}
