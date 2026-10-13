package com.sts.admin.system.dto;

import java.util.List;

/**
 * 用户导入结果：成功/跳过/失败统计与失败明细。
 */
public record UserImportResult(
        int successCount,
        int skippedCount,
        List<FailedRow> failedRows,
        List<String> fileErrors) {

    public record FailedRow(int line, String reason) {
    }

    public static UserImportResult of(int success, int skipped, List<FailedRow> failed, List<String> fileErrors) {
        return new UserImportResult(success, skipped, failed, fileErrors);
    }
}
