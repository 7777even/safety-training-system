package com.sts.exam.dto;

import lombok.Data;

/** 考情人员查询与导出共用筛选。 */
@Data
public class ExamStatisticsQuery {
  /** 姓名或账号关键字。 */
  private String learnerKeyword;
  /** 仅缺考；开放窗口内无缺考。 */
  private Boolean absentOnly;
}
