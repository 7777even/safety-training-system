package com.sts.admin.system.service;

import com.sts.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetWordDocumentValidatorTest {

  private final AssetWordDocumentValidator validator = new AssetWordDocumentValidator();

  @Test
  void standardDocxIsAcceptedForAssetFolder() {
    assertThatCode(() -> validator.validate("standard.docx", "asset", new ByteArrayInputStream(docx(
      "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>"))))
      .doesNotThrowAnyException();
  }

  @Test
  void legacyDocAndVmlTextboxAreRejectedWithStableError() {
    assertRejected("legacy.doc", new byte[]{1, 2, 3});
    assertRejected("textbox.docx", docx("""
                                          <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                                                      xmlns:v="urn:schemas-microsoft-com:vml"><v:textbox/></w:document>
                                          """));
  }

  @Test
  void nonAssetFolderIsNotInspected() {
    assertThatCode(() -> validator.validate("legacy.doc", "general",
                                            new ByteArrayInputStream(new byte[]{1})))
      .doesNotThrowAnyException();
  }

  @Test
  void malformedDocxIsRejected() {
    assertRejected("broken.docx", "not-a-zip".getBytes());
    assertRejected("invalid-relationships.docx", docx(Map.of(
      "_rels/.rels", "<Relationships/>",
      "word/document.xml", validDocument())));
    assertRejected("invalid-document-root.docx", docx(Map.of(
      "word/document.xml", "<w:body xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>")));
  }

  @Test
  void vmlTextboxInHeaderIsRejected() {
    assertRejected("header-textbox.docx", docx(Map.of(
      "word/document.xml", validDocument(),
      "word/header1.xml", """
        <w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
               xmlns:v="urn:schemas-microsoft-com:vml"><v:textbox/></w:hdr>
        """)));
    assertRejected("vml-part-textbox.docx", docx(Map.of(
      "word/document.xml", validDocument(),
      "word/drawings/vmlDrawing1.vml", """
        <xml xmlns:v="urn:schemas-microsoft-com:vml"><v:textbox/></xml>
        """)));
  }

  @Test
  void wpsMetadataWithoutVmlIsAccepted() {
    assertThatCode(() -> validator.validate("wps-standard.docx", "asset", new ByteArrayInputStream(docx(Map.of(
      "word/document.xml", validDocument(),
      "docProps/app.xml", "<Properties><Application>WPS Office</Application></Properties>")))))
      .doesNotThrowAnyException();
  }

  @Test
  void oversizedNonXmlEntryIsRejected() {
    assertRejected("zip-bomb.docx", docxWithOversizedEntry("word/media/bomb.bin", 65 * 1024 * 1024));
    assertRejected("directory-zip-bomb.docx", docxWithOversizedEntry("word/media/bomb/", 65 * 1024 * 1024));
  }

  private void assertRejected(String fileName, byte[] content) {
    assertThatThrownBy(() -> validator.validate(fileName, "asset", new ByteArrayInputStream(content)))
      .isInstanceOfSatisfying(BusinessException.class, error -> {
        org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo(5006);
        org.assertj.core.api.Assertions.assertThat(error.getStatus().value()).isEqualTo(400);
      })
      .hasMessageContaining("标准 DOCX");
  }

  private static byte[] docx(String documentXml) {
    return docx(Map.of("word/document.xml", documentXml));
  }

  private static byte[] docx(Map<String, String> overrides) {
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      try (ZipOutputStream zip = new ZipOutputStream(output)) {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", """
          <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
            <Override PartName="/word/document.xml"
              ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
          </Types>
          """);
        entries.put("_rels/.rels", """
          <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1"
              Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
              Target="word/document.xml"/>
          </Relationships>
          """);
        entries.put("word/document.xml", validDocument());
        entries.putAll(overrides);
        for (var entry : entries.entrySet()) writeEntry(zip, entry.getKey(), entry.getValue());
      }
      return output.toByteArray();
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  private static byte[] docxWithOversizedEntry(String entryName, int size) {
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      try (ZipOutputStream zip = new ZipOutputStream(output)) {
        writeEntry(zip, "[Content_Types].xml", """
          <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
            <Override PartName="/word/document.xml"
              ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
          </Types>
          """);
        writeEntry(zip, "_rels/.rels", """
          <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1"
              Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
              Target="word/document.xml"/>
          </Relationships>
          """);
        zip.putNextEntry(new ZipEntry(entryName));
        byte[] block = new byte[8192];
        for (int written = 0; written < size; written += block.length) {
          zip.write(block, 0, Math.min(block.length, size - written));
        }
        zip.closeEntry();
        writeEntry(zip, "word/document.xml", validDocument());
      }
      return output.toByteArray();
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  private static void writeEntry(ZipOutputStream zip, String name, String value) throws java.io.IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(value.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  private static String validDocument() {
    return "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body/></w:document>";
  }
}
