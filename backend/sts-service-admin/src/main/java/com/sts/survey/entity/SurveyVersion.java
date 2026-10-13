package com.sts.survey.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sts_survey_version")
public class SurveyVersion {
  @TableId(type = IdType.AUTO)
  private Long surveyVersionId;
  private Long surveyId;
  private Integer versionNo;
  private String contentSnapshotJson;
  private String snapshotHash;
  private String versionNote;
  private Long publishedBy;
  private LocalDateTime publishedTime;
  private Long createBy;
  private LocalDateTime createTime;
}
