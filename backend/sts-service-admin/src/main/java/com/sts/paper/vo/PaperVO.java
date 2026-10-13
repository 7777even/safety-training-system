package com.sts.paper.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PaperVO {
  private Long paperId;
  private Long organizationId;
  private String paperCode;
  private String paperName;
  private String paperType;
  private Long paperCategoryId;
  private Long coverFileId;
  private Integer currentVersionNo;
  private String archiveStatus;
  private Integer status;
  private Integer version;
  private LocalDateTime createTime;
}
