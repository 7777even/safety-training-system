package com.sts.regulation.export;

import com.sts.common.exception.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 监管导出最小格式：UTF-8 BOM CSV，同步下载，禁止占位文件。
 */
public final class Utf8CsvExporter {

  public static final int MAX_ROWS = 10000;
  public static final int CODE_TOO_LARGE = 9820;

  private Utf8CsvExporter() {
  }

  public static ResponseEntity<byte[]> download(String filename, String filterSummary,
                                                List<String> headers, List<List<String>> rows) {
    if (rows.size() > MAX_ROWS) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, CODE_TOO_LARGE,
                                  "导出数据超过 " + MAX_ROWS + " 行，请缩小筛选范围");
    }
    StringBuilder csv = new StringBuilder();
    csv.append('\uFEFF');
    csv.append("# exportedAt=")
      .append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
      .append("; ")
      .append(filterSummary == null ? "" : filterSummary)
      .append('\n');
    csv.append(join(headers)).append('\n');
    for (List<String> row : rows) {
      csv.append(join(row)).append('\n');
    }
    byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
      .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
      .body(body);
  }

  private static String join(List<String> cells) {
    StringBuilder line = new StringBuilder();
    for (int i = 0; i < cells.size(); i++) {
      if (i > 0) {
        line.append(',');
      }
      line.append(escape(cells.get(i)));
    }
    return line.toString();
  }

  private static String escape(String raw) {
    String value = raw == null ? "" : raw;
    if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0) {
      return '"' + value.replace("\"", "\"\"") + '"';
    }
    return value;
  }
}
