package com.sts.paper.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 试卷版本摘要。用于从 paperId 找到具体 paperVersionId（含草稿版本）。
 */
@Data
public class PaperVersionSummaryVO {
  private Long paperVersionId;
  private Long paperId;
  private Integer versionNo;
  private String publishedStatus;
  private Long publishedBy;
  private LocalDateTime publishedTime;
  private String versionNote;
}
