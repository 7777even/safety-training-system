package com.sts.asset.transcode;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfPageCounterTest {
  @Test
  void readsRealPdfPageCount() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (PDDocument document = new PDDocument()) {
      document.addPage(new PDPage());
      document.addPage(new PDPage());
      document.save(output);
    }
    assertThat(new PdfPageCounter().count(new ByteArrayInputStream(output.toByteArray()))).isEqualTo(2);
  }

  @Test
  void rejectsInvalidPdf() {
    assertThatThrownBy(() -> new PdfPageCounter().count(
      new ByteArrayInputStream("not-pdf".getBytes())))
      .isInstanceOf(TranscodeFailureException.class)
      .extracting("failureCode").isEqualTo("SOURCE_INVALID");
  }
}
