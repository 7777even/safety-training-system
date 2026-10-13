package com.sts.admin.system.dto;

import java.util.List;

/**
 * 用户导出数据：表头与行。
 */
public record UserExportData(List<String> headers, List<List<String>> rows) {
}
