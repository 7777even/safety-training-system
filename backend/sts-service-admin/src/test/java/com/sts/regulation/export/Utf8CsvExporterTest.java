package com.sts.regulation.export;

import com.sts.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Utf8CsvExporterTest {

  @Test
  @DisplayName("导出带 UTF-8 BOM、筛选摘要，并可转义逗号")
  void download_writesBomAndEscapes() {
    ResponseEntity<byte[]> response = Utf8CsvExporter.download(
      "demo.csv",
      "scope=test",
      List.of("a", "b"),
      List.of(List.of("1", "hello,world")));

    byte[] body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body[0]).isEqualTo((byte) 0xEF);
    assertThat(body[1]).isEqualTo((byte) 0xBB);
    assertThat(body[2]).isEqualTo((byte) 0xBF);
    String csv = new String(body, StandardCharsets.UTF_8);
    assertThat(csv).contains("scope=test");
    assertThat(csv).contains("a,b");
    assertThat(csv).contains("\"hello,world\"");
    assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("demo.csv");
  }

  @Test
  @DisplayName("超过安全上限拒绝导出")
  void download_rejectsTooManyRows() {
    List<List<String>> rows = new ArrayList<>();
    for (int i = 0; i <= Utf8CsvExporter.MAX_ROWS; i++) {
      rows.add(List.of(String.valueOf(i)));
    }
    assertThatThrownBy(() -> Utf8CsvExporter.download("x.csv", "", List.of("id"), rows))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo(Utf8CsvExporter.CODE_TOO_LARGE);
      });
  }
}
