package com.sts.admin.system.service;

import com.sts.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 校验素材 Word 文件是否适合进入 OnlyOffice 预览转换。
 */
@Component
public class AssetWordDocumentValidator {
  private static final String ASSET_FOLDER = "asset";
  private static final String VML_NAMESPACE = "urn:schemas-microsoft-com:vml";
  private static final String WORD_NAMESPACE =
    "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
  private static final String CONTENT_TYPES_NAMESPACE =
    "http://schemas.openxmlformats.org/package/2006/content-types";
  private static final String RELATIONSHIPS_NAMESPACE =
    "http://schemas.openxmlformats.org/package/2006/relationships";
  private static final String OFFICE_DOCUMENT_RELATIONSHIP =
    "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument";
  private static final String DOCUMENT_CONTENT_TYPE =
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
  private static final int MAX_ZIP_ENTRIES = 512;
  private static final int MAX_XML_ENTRY_SIZE = 16 * 1024 * 1024;
  private static final int MAX_OTHER_ENTRY_SIZE = 64 * 1024 * 1024;
  private static final long MAX_TOTAL_UNCOMPRESSED_SIZE = 256L * 1024 * 1024;

  /**
   * 判断文件是否需要执行 Word 兼容性预检。
   */
  public boolean requiresValidation(String fileName, String folder) {
    if (!ASSET_FOLDER.equals(folder) || fileName == null) return false;
    String lowerName = fileName.toLowerCase(Locale.ROOT);
    return lowerName.endsWith(".doc") || lowerName.endsWith(".docx");
  }

  /**
   * 拒绝旧 DOC、损坏 DOCX 及包含旧式 VML 文本框的素材文档。
   */
  public void validate(String fileName, String folder, InputStream input) {
    if (!requiresValidation(fileName, folder)) return;
    if (fileName.toLowerCase(Locale.ROOT).endsWith(".doc")) throw incompatible();

    try (ZipInputStream zip = new ZipInputStream(input)) {
      int entries = 0;
      boolean contentTypesFound = false;
      boolean relationshipsFound = false;
      boolean documentFound = false;
      long totalUncompressedSize = 0;
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (++entries > MAX_ZIP_ENTRIES) throw incompatible();
        String entryName = entry.getName();
        boolean xmlEntry = isXmlEntry(entryName);
        EntryContent content = readEntry(zip, xmlEntry ? MAX_XML_ENTRY_SIZE : MAX_OTHER_ENTRY_SIZE,
                                         MAX_TOTAL_UNCOMPRESSED_SIZE - totalUncompressedSize, xmlEntry);
        totalUncompressedSize += content.size();
        if (entry.isDirectory()) continue;
        if (!xmlEntry) continue;

        if ("[Content_Types].xml".equals(entryName)) {
          if (contentTypesFound) throw incompatible();
          validateContentTypes(content.bytes());
          contentTypesFound = true;
        } else if ("_rels/.rels".equals(entryName)) {
          if (relationshipsFound) throw incompatible();
          validateRelationships(content.bytes());
          relationshipsFound = true;
        } else {
          boolean mainDocument = "word/document.xml".equals(entryName);
          if (mainDocument && documentFound) throw incompatible();
          validateXmlPart(content.bytes(), mainDocument);
          if (mainDocument) documentFound = true;
        }
      }
      if (!contentTypesFound || !relationshipsFound || !documentFound) throw incompatible();
    } catch (IOException | XMLStreamException e) {
      throw incompatible();
    }
  }

  private void validateContentTypes(byte[] xml) throws XMLStreamException {
    var reader = xmlReader(xml);
    try {
      boolean validRoot = false;
      boolean documentTypeFound = false;
      while (reader.hasNext()) {
        if (reader.next() != XMLStreamConstants.START_ELEMENT) continue;
        if (!validRoot) {
          validRoot = CONTENT_TYPES_NAMESPACE.equals(reader.getNamespaceURI())
            && "Types".equals(reader.getLocalName());
          if (!validRoot) throw incompatible();
        } else if (CONTENT_TYPES_NAMESPACE.equals(reader.getNamespaceURI())
          && "Override".equals(reader.getLocalName())
          && "/word/document.xml".equals(reader.getAttributeValue(null, "PartName"))
          && DOCUMENT_CONTENT_TYPE.equals(reader.getAttributeValue(null, "ContentType"))) {
          documentTypeFound = true;
        }
      }
      if (!validRoot || !documentTypeFound) throw incompatible();
    } finally {
      reader.close();
    }
  }

  private void validateRelationships(byte[] xml) throws XMLStreamException {
    var reader = xmlReader(xml);
    try {
      boolean validRoot = false;
      boolean documentRelationshipFound = false;
      while (reader.hasNext()) {
        if (reader.next() != XMLStreamConstants.START_ELEMENT) continue;
        if (!validRoot) {
          validRoot = RELATIONSHIPS_NAMESPACE.equals(reader.getNamespaceURI())
            && "Relationships".equals(reader.getLocalName());
          if (!validRoot) throw incompatible();
        } else if (RELATIONSHIPS_NAMESPACE.equals(reader.getNamespaceURI())
          && "Relationship".equals(reader.getLocalName())
          && OFFICE_DOCUMENT_RELATIONSHIP.equals(reader.getAttributeValue(null, "Type"))
          && isDocumentTarget(reader.getAttributeValue(null, "Target"))
          && reader.getAttributeValue(null, "TargetMode") == null) {
          documentRelationshipFound = true;
        }
      }
      if (!validRoot || !documentRelationshipFound) throw incompatible();
    } finally {
      reader.close();
    }
  }

  private void validateXmlPart(byte[] xml, boolean mainDocument) throws XMLStreamException {
    var reader = xmlReader(xml);
    try {
      boolean rootFound = false;
      while (reader.hasNext()) {
        if (reader.next() != XMLStreamConstants.START_ELEMENT) continue;
        if (!rootFound) {
          rootFound = true;
          if (mainDocument && (!WORD_NAMESPACE.equals(reader.getNamespaceURI())
            || !"document".equals(reader.getLocalName()))) {
            throw incompatible();
          }
        }
        if (VML_NAMESPACE.equals(reader.getNamespaceURI()) && "textbox".equals(reader.getLocalName())) {
          throw incompatible();
        }
      }
      if (!rootFound) throw incompatible();
    } finally {
      reader.close();
    }
  }

  private javax.xml.stream.XMLStreamReader xmlReader(byte[] xml) throws XMLStreamException {
    XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
    return factory.createXMLStreamReader(new ByteArrayInputStream(xml));
  }

  private EntryContent readEntry(ZipInputStream zip, int entryLimit, long totalRemaining,
                                 boolean capture) throws IOException {
    if (totalRemaining < 0) throw incompatible();
    ByteArrayOutputStream output = capture ? new ByteArrayOutputStream() : null;
    byte[] buffer = new byte[8192];
    long size = 0;
    int read;
    while ((read = zip.read(buffer)) != -1) {
      size += read;
      if (size > entryLimit || size > totalRemaining) throw incompatible();
      if (capture) output.write(buffer, 0, read);
    }
    return new EntryContent(capture ? output.toByteArray() : null, size);
  }

  private boolean isXmlEntry(String name) {
    return "[Content_Types].xml".equals(name)
      || name.endsWith(".xml")
      || name.endsWith(".vml")
      || name.endsWith(".rels");
  }

  private boolean isDocumentTarget(String target) {
    return "word/document.xml".equals(target) || "/word/document.xml".equals(target);
  }

  private record EntryContent(byte[] bytes, long size) {
  }

  private static BusinessException incompatible() {
    return new BusinessException(HttpStatus.BAD_REQUEST, 5006,
                                 "文档包含不兼容格式，请在 WPS/Word 中整理浮动文本框后另存为标准 DOCX，或上传 PDF");
  }
}
