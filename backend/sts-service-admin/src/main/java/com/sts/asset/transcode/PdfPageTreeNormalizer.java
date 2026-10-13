package com.sts.asset.transcode;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 将 PREVIEW 的页面树重建为固定最大扇出的层级结构。
 */
public class PdfPageTreeNormalizer {
  static final int MAX_FANOUT = 32;
  private static final List<COSName> INHERITABLE_PAGE_KEYS = List.of(
    COSName.RESOURCES, COSName.MEDIA_BOX, COSName.CROP_BOX, COSName.ROTATE);

  public void normalize(Path source, Path output) {
    int expectedPageCount;
    try (PDDocument document = Loader.loadPDF(source.toFile())) {
      if (document.isEncrypted()) {
        // SOURCE 始终只读；仅允许随后保存的受控 PREVIEW 派生文件移除 PDF 内部保护。
        document.setAllSecurityToBeRemoved(true);
      }
      List<COSDictionary> pages = snapshotPages(document);
      if (pages.isEmpty()) {
        throw new TranscodeFailureException("SOURCE_INVALID", "PDF 页面树不包含页面");
      }
      expectedPageCount = pages.size();
      pages.forEach(PdfPageTreeNormalizer::materializeInheritedAttributes);
      rebuild(document.getDocumentCatalog().getPages().getCOSObject(), pages);
      // qpdf 负责最终压缩和线性化；传统 xref 可避免 PDFBox 压缩保存时产生的自引用 xref 警告。
      document.save(output.toFile(), CompressParameters.NO_COMPRESSION);
    } catch (TranscodeFailureException e) {
      deletePartialOutput(output);
      throw e;
    } catch (IOException | RuntimeException e) {
      deletePartialOutput(output);
      throw new TranscodeFailureException("SOURCE_INVALID", "PDF 页面树规范化失败", e);
    }
    try {
      validate(output, expectedPageCount);
    } catch (TranscodeFailureException e) {
      deletePartialOutput(output);
      throw e;
    }
  }

  public int validate(Path pdf, int expectedPageCount) {
    try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
      int pageCount = validateNode(
        document.getDocumentCatalog().getPages().getCOSObject(), null,
        Collections.newSetFromMap(new IdentityHashMap<>()));
      if (pageCount != document.getNumberOfPages() || pageCount != expectedPageCount) {
        throw new TranscodeFailureException("TRANSCODE_ERROR", "PDF 页面树页数不一致");
      }
      return pageCount;
    } catch (TranscodeFailureException e) {
      throw e;
    } catch (IOException | RuntimeException e) {
      throw new TranscodeFailureException("TRANSCODE_ERROR", "PDF 页面树结构校验失败", e);
    }
  }

  private static List<COSDictionary> snapshotPages(PDDocument document) {
    List<COSDictionary> pages = new ArrayList<>(document.getNumberOfPages());
    for (PDPage page : document.getPages()) {
      pages.add(page.getCOSObject());
    }
    return pages;
  }

  private static void materializeInheritedAttributes(COSDictionary page) {
    for (COSName key : INHERITABLE_PAGE_KEYS) {
      if (!page.containsKey(key)) {
        COSBase inherited = PDPageTree.getInheritableAttribute(page, key);
        if (inherited != null) page.setItem(key, inherited);
      }
    }
  }

  private static void rebuild(COSDictionary root, List<COSDictionary> pages) {
    List<COSDictionary> level = pages;
    while (level.size() > MAX_FANOUT) {
      level = group(level);
    }
    setChildren(root, level);
    root.setName(COSName.TYPE, COSName.PAGES.getName());
    root.setInt(COSName.COUNT, pages.size());
    root.removeItem(COSName.PARENT);
  }

  private static List<COSDictionary> group(List<COSDictionary> children) {
    List<COSDictionary> parents = new ArrayList<>((children.size() + MAX_FANOUT - 1) / MAX_FANOUT);
    for (int start = 0; start < children.size(); start += MAX_FANOUT) {
      int end = Math.min(start + MAX_FANOUT, children.size());
      COSDictionary parent = new COSDictionary();
      parent.setName(COSName.TYPE, COSName.PAGES.getName());
      setChildren(parent, children.subList(start, end));
      parent.setInt(COSName.COUNT, countPages(children.subList(start, end)));
      parents.add(parent);
    }
    return parents;
  }

  private static void setChildren(COSDictionary parent, List<COSDictionary> children) {
    COSArray kids = new COSArray();
    for (COSDictionary child : children) {
      child.setItem(COSName.PARENT, parent);
      kids.add(child);
    }
    parent.setItem(COSName.KIDS, kids);
  }

  private static int countPages(List<COSDictionary> children) {
    int count = 0;
    for (COSDictionary child : children) {
      count += COSName.PAGES.equals(child.getCOSName(COSName.TYPE))
        ? child.getInt(COSName.COUNT) : 1;
    }
    return count;
  }

  private static int validateNode(COSDictionary node, COSDictionary expectedParent,
                                  Set<COSDictionary> visited) {
    if (!visited.add(node)) {
      throw new TranscodeFailureException("TRANSCODE_ERROR", "PDF 页面树存在循环引用");
    }
    if (!COSName.PAGES.equals(node.getCOSName(COSName.TYPE))) {
      throw new TranscodeFailureException("TRANSCODE_ERROR", "PDF 页面树节点类型无效");
    }
    if (expectedParent != null && node.getDictionaryObject(COSName.PARENT) != expectedParent) {
      throw new TranscodeFailureException("TRANSCODE_ERROR", "PDF 页面树父节点无效");
    }
    COSArray kids = node.getCOSArray(COSName.KIDS);
    if (kids == null || kids.size() == 0 || kids.size() > MAX_FANOUT) {
      throw new TranscodeFailureException("TRANSCODE_ERROR", "PDF 页面树扇出无效");
    }
    int count = 0;
    for (int index = 0; index < kids.size(); index++) {
      COSBase kid = kids.getObject(index);
      if (!(kid instanceof COSDictionary child)) {
        throw new TranscodeFailureException("TRANSCODE_ERROR", "PDF 页面树子节点无效");
      }
      if (child.getDictionaryObject(COSName.PARENT) != node) {
        throw new TranscodeFailureException("TRANSCODE_ERROR", "PDF 页面树父子关系无效");
      }
      if (COSName.PAGES.equals(child.getCOSName(COSName.TYPE))) {
        count += validateNode(child, node, visited);
      } else if (COSName.PAGE.equals(child.getCOSName(COSName.TYPE))) {
        count++;
      } else {
        throw new TranscodeFailureException("TRANSCODE_ERROR", "PDF 页面节点类型无效");
      }
    }
    if (node.getInt(COSName.COUNT) != count) {
      throw new TranscodeFailureException("TRANSCODE_ERROR", "PDF 页面树 Count 无效");
    }
    return count;
  }

  private static void deletePartialOutput(Path output) {
    try {
      Files.deleteIfExists(output);
    } catch (IOException ignored) {
    }
  }
}
