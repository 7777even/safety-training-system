package com.sts.learner.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

/** 原始记录与档案展示更正始终分开，不把更正冒充正式计算结果。 */
public record LearnerHistoryVO(String recordType, Long recordId, String title, Summary original,
  Summary current, int version, Reference reference, List<Correction> corrections) {
  /** 已有摘要字段；未公开成绩和不适用学时为空。 */
  public record Summary(String userName, String userCode, LocalDateTime occurredTime, String status,
                        BigDecimal score, BigDecimal hours, boolean scoreVisible) { }
  /** 只包含已有受控业务入口，不返回URL。 */
  public record Reference(String kind, Long resourceId, Long parentId, Long itemId, Long attemptId, String action) { }
  /** 每次追加的字段前后值、理由、操作人和版本；本人读取仍裁剪未公开成绩。 */
  public record Correction(Long correctionId, int version, Map<String, JsonNode> before,
    Map<String, JsonNode> after, String reason, Long operatorId, String operatorName, LocalDateTime operatedTime) { }
}
