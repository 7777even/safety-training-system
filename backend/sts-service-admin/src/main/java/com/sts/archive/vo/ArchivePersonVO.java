package com.sts.archive.vo;

import lombok.Data;

import java.util.List;

/**
 * 个人档案：基本信息与所参加已结束培训列表。
 */
@Data
public class ArchivePersonVO {
  private Long userId;
  private String userName;
  private String userCode;
  private List<String> missingItems;
  private List<ArchiveViewVO> trainings;
  private Long fileInfoId;
  /** 当前操作人已有领域读取权限覆盖的统一历史摘要。 */
  private List<com.sts.learner.vo.LearnerHistoryVO> history;
}
