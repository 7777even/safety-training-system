package com.sts.course.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CourseCreateRequest {
  @NotBlank(message = "课程编码不能为空")
  private String courseCode;

  @NotBlank(message = "课程名称不能为空")
  private String courseName;
  /** 本机构主讲教师用户ID，旧课程可空。 */
  private Long teacherUserId;

  private Long coverFileId;

  private String introduction;
  private String teachingObjective;
  private String syllabus;
  private String assessmentMethod;

  @NotBlank(message = "内容模式不能为空")
  @Pattern(regexp = "SINGLE|MULTI_CHAPTER", message = "内容模式仅支持 SINGLE 或 MULTI_CHAPTER")
  private String contentMode = "MULTI_CHAPTER";

  @NotNull(message = "学分不能为空")
  @DecimalMin(value = "0", message = "学分不能为负")
  private BigDecimal credit = BigDecimal.ZERO;

  @NotBlank(message = "积分奖励模式不能为空")
  @Pattern(regexp = "NONE|PER_ITEM|COURSE", message = "积分奖励模式仅支持 NONE、PER_ITEM 或 COURSE")
  private String pointMode = "NONE";

  @NotNull(message = "积分值不能为空")
  @DecimalMin(value = "0", message = "积分值不能为负")
  private BigDecimal pointValue = BigDecimal.ZERO;

  @NotBlank(message = "源文件下载模式不能为空")
  @Pattern(regexp = "NONE|ALL|PARTIAL", message = "源文件下载模式仅支持 NONE、ALL 或 PARTIAL")
  private String sourceDownloadMode = "NONE";

  @NotNull(message = "是否允许倍速播放不能为空")
  private Integer isSpeedPlayAllowed = 0;

  @NotNull(message = "是否启用学习防作弊不能为空")
  private Integer isStudyAnticheatEnabled = 0;

  @NotNull(message = "是否显示学时说明不能为空")
  private Integer isStudyDescriptionEnabled = 1;

  @NotNull(message = "是否启用水印不能为空")
  private Integer isWatermarkEnabled = 0;

  private Integer status = 1;

  /**
   * 创建时可选的初始分类关联（同机构分类 ID）。
   */
  private List<Long> categoryIds;
}
