package com.sts.exam.dto;

import lombok.Data;

@Data
public class ExamCandidateQuery {
  /**
   * 按学员姓名或账号模糊匹配。
   */
  private String learnerKeyword;
  private String candidateStatus;
}
