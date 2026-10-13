package com.sts.course.vo;

import lombok.Data;

/** 本人直播参与返回已校验的地址及完成状态，不包含外部观看时长。 */
@Data
public class LiveParticipationVO {
  /** 合法HTTP(S)直播地址。 */
  private String liveUrl;
  /** 成功参与后的内容项状态。 */
  private String progressStatus;
}
