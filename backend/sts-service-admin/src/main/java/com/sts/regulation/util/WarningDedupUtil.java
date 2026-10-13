package com.sts.regulation.util;

import com.sts.survey.util.SnapshotHashUtil;

public final class WarningDedupUtil {
  private WarningDedupUtil() {}

  public static String hoursKey(Long regulatedOrgId, Long planId) {
    return SnapshotHashUtil.sha256Hex("PLAN_TOTAL_HOURS|" + regulatedOrgId + "|" + planId);
  }

  public static String dedupKey(Long ruleId, Long regulatedOrgId, Long planId, String detectionCycle) {
    String raw = ruleId + "|" + regulatedOrgId + "|" + (planId == null ? 0 : planId) + "|" + detectionCycle;
    return SnapshotHashUtil.sha256Hex(raw);
  }

  public static String evidenceKey(String metricCode, String sourceType, Long sourceId) {
    return SnapshotHashUtil.sha256Hex(metricCode + "|" + sourceType + "|" + sourceId);
  }
}
