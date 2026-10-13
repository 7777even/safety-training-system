package com.sts.common.util;

import java.util.List;

/**
 * CSV 导出内容：表头与数据行。
 */
public record CsvContent(List<String> headers, List<List<String>> rows) {
}
