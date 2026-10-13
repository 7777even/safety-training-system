package com.sts.asset.transcode;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfPageTreeNormalizerTest {
  @TempDir
  Path tempDir;

  @Test
  void rebuildsFlatSixtyFivePageTreeWithBoundedFanout() throws Exception {
    Path source = flatPdf("flat-65.pdf", 65);
    Path output = tempDir.resolve("normalized-65.pdf");

    new PdfPageTreeNormalizer().normalize(source, output);

    try (PDDocument document = Loader.loadPDF(output.toFile())) {
      assertThat(document.getNumberOfPages()).isEqualTo(65);
      assertBoundedTree(document.getDocumentCatalog().getPages().getCOSObject(), 32);
      assertThat(document.getDocumentCatalog().getPages().getCOSObject()
                   .getCOSArray(COSName.KIDS)).hasSize(3);
      for (int index = 0; index < document.getNumberOfPages(); index++) {
        assertThat(document.getPage(index).getCOSObject()
                     .getInt(COSName.getPDFName("TestPageIndex"))).isEqualTo(index);
      }
    }
  }

  @Test
  void createsAnotherLevelWhenPageCountExceedsFanoutSquared() throws Exception {
    Path source = flatPdf("flat-1025.pdf", 1025);
    Path output = tempDir.resolve("normalized-1025.pdf");

    new PdfPageTreeNormalizer().normalize(source, output);

    try (PDDocument document = Loader.loadPDF(output.toFile())) {
      COSDictionary root = document.getDocumentCatalog().getPages().getCOSObject();
      assertThat(document.getNumberOfPages()).isEqualTo(1025);
      assertBoundedTree(root, 32);
      COSDictionary branch = (COSDictionary) root.getCOSArray(COSName.KIDS).getObject(0);
      assertThat(branch.getCOSArray(COSName.KIDS).getObject(0))
        .isInstanceOf(COSDictionary.class);
      assertThat(((COSDictionary) branch.getCOSArray(COSName.KIDS).getObject(0))
                   .getCOSName(COSName.TYPE)).isEqualTo(COSName.PAGES);
    }
  }

  @Test
  void preservesInheritedPageAttributesOutlineAndAnnotations() throws Exception {
    Path source = tempDir.resolve("references.pdf");
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage();
      page.getCOSObject().removeItem(COSName.MEDIA_BOX);
      document.addPage(page);
      COSDictionary root = document.getDocumentCatalog().getPages().getCOSObject();
      root.setItem(COSName.MEDIA_BOX, new PDRectangle(420, 595).getCOSArray());
      root.setItem(COSName.CROP_BOX, new PDRectangle(400, 575).getCOSArray());
      root.setItem(COSName.RESOURCES, new PDResources());
      root.setInt(COSName.ROTATE, 90);
      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        content.moveTo(10, 10);
        content.lineTo(100, 100);
        content.stroke();
      }

      PDAnnotationText annotation = new PDAnnotationText();
      annotation.setContents("保留注释");
      page.setAnnotations(List.of(annotation));

      PDPageFitDestination destination = new PDPageFitDestination();
      destination.setPage(page);
      PDOutlineItem item = new PDOutlineItem();
      item.setTitle("第一页");
      item.setDestination(destination);
      PDDocumentOutline outline = new PDDocumentOutline();
      outline.addLast(item);
      document.getDocumentCatalog().setDocumentOutline(outline);
      document.save(source.toFile());
    }
    Path output = tempDir.resolve("references-normalized.pdf");

    new PdfPageTreeNormalizer().normalize(source, output);

    try (PDDocument document = Loader.loadPDF(output.toFile())) {
      PDPage page = document.getPage(0);
      assertThat(page.getMediaBox().getWidth()).isEqualTo(420);
      assertThat(page.getMediaBox().getHeight()).isEqualTo(595);
      assertThat(page.getCropBox().getWidth()).isEqualTo(400);
      assertThat(page.getResources()).isNotNull();
      assertThat(page.getRotation()).isEqualTo(90);
      assertThat(page.hasContents()).isTrue();
      assertThat(page.getAnnotations()).singleElement()
        .extracting(annotation -> ((PDAnnotationText) annotation).getContents())
        .isEqualTo("保留注释");
      PDOutlineItem item = document.getDocumentCatalog().getDocumentOutline().getFirstChild();
      assertThat(item.findDestinationPage(document)).isEqualTo(page);
    }
  }

  @Test
  void removesRestrictionsOnlyFromDerivedPreviewWhenPdfOpensWithoutPassword() throws Exception {
    Path source = protectedPdf("restricted-empty-password.pdf", "");
    Path output = tempDir.resolve("restricted-preview.pdf");

    new PdfPageTreeNormalizer().normalize(source, output);

    try (PDDocument original = Loader.loadPDF(source.toFile());
         PDDocument preview = Loader.loadPDF(output.toFile())) {
      assertThat(original.isEncrypted()).isTrue();
      assertThat(original.getCurrentAccessPermission().canModify()).isFalse();
      assertThat(original.getCurrentAccessPermission().canAssembleDocument()).isFalse();
      assertThat(preview.isEncrypted()).isFalse();
      assertThat(preview.getNumberOfPages()).isEqualTo(2);
      assertThat(preview.getPage(0).getCOSObject()
                   .getInt(COSName.getPDFName("TestPageIndex"))).isZero();
      assertThat(preview.getPage(1).getCOSObject()
                   .getInt(COSName.getPDFName("TestPageIndex"))).isEqualTo(1);
    }
  }

  @Test
  void rejectsPdfThatRequiresNonEmptyPasswordWithoutProducingPreview() throws Exception {
    Path source = protectedPdf("restricted-non-empty-password.pdf", "reader-secret");
    Path output = tempDir.resolve("password-preview.pdf");

    assertThatThrownBy(() -> new PdfPageTreeNormalizer().normalize(source, output))
      .isInstanceOf(TranscodeFailureException.class)
      .hasMessage("PDF 页面树规范化失败")
      .hasCauseInstanceOf(IOException.class);
    assertThat(output).doesNotExist();
  }

  @Test
  void normalizationFailureRetainsCauseForServerDiagnostics() throws Exception {
    Path source = flatPdf("diagnostic-source.pdf", 1);

    assertThatThrownBy(() -> new PdfPageTreeNormalizer().normalize(source, tempDir))
      .isInstanceOf(TranscodeFailureException.class)
      .hasMessage("PDF 页面树规范化失败")
      .hasCauseInstanceOf(IOException.class);
  }

  private Path flatPdf(String fileName, int pageCount) throws Exception {
    Path path = tempDir.resolve(fileName);
    try (PDDocument document = new PDDocument()) {
      COSDictionary root = document.getDocumentCatalog().getPages().getCOSObject();
      COSArray kids = new COSArray();
      for (int i = 0; i < pageCount; i++) {
        PDPage page = new PDPage();
        COSDictionary pageDictionary = page.getCOSObject();
        pageDictionary.setInt(COSName.getPDFName("TestPageIndex"), i);
        pageDictionary.setItem(COSName.PARENT, root);
        kids.add(pageDictionary);
      }
      root.setItem(COSName.KIDS, kids);
      root.setInt(COSName.COUNT, pageCount);
      document.save(path.toFile());
    }
    return path;
  }

  private Path protectedPdf(String fileName, String userPassword) throws Exception {
    Path path = tempDir.resolve(fileName);
    try (PDDocument document = new PDDocument()) {
      for (int index = 0; index < 2; index++) {
        PDPage page = new PDPage();
        page.getCOSObject().setInt(COSName.getPDFName("TestPageIndex"), index);
        document.addPage(page);
      }
      AccessPermission permission = new AccessPermission();
      permission.setCanModify(false);
      permission.setCanAssembleDocument(false);
      StandardProtectionPolicy policy = new StandardProtectionPolicy(
        "owner-secret", userPassword, permission);
      policy.setEncryptionKeyLength(128);
      document.protect(policy);
      document.save(path.toFile());
    }
    return path;
  }

  private static int assertBoundedTree(COSDictionary node, int maxFanout) {
    COSArray kids = node.getCOSArray(COSName.KIDS);
    assertThat(kids).isNotNull();
    assertThat(kids.size()).isLessThanOrEqualTo(maxFanout);
    int count = 0;
    for (int i = 0; i < kids.size(); i++) {
      COSDictionary child = (COSDictionary) kids.getObject(i);
      assertThat(child.getDictionaryObject(COSName.PARENT)).isSameAs(node);
      if (COSName.PAGES.equals(child.getCOSName(COSName.TYPE))) {
        count += assertBoundedTree(child, maxFanout);
      } else {
        assertThat(child.getCOSName(COSName.TYPE)).isEqualTo(COSName.PAGE);
        count++;
      }
    }
    assertThat(node.getInt(COSName.COUNT)).isEqualTo(count);
    return count;
  }
}
