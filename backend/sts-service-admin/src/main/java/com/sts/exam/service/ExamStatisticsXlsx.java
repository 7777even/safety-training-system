package com.sts.exam.service;

import com.sts.exam.vo.ExamStatisticsPersonVO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** 仅导出考情明细的标准 OOXML 工作簿，文本单元格避免公式注入。 */
public final class ExamStatisticsXlsx {
  private ExamStatisticsXlsx() {}
  /** 同步生成工作簿，不引入额外依赖。 */
  public static byte[] write(List<ExamStatisticsPersonVO> people) {
    try {
      var out = new ByteArrayOutputStream();
      try (var zip = new ZipOutputStream(out,StandardCharsets.UTF_8)) {
        entry(zip,"[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
        entry(zip,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
        entry(zip,"xl/workbook.xml","<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"考试成绩\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
        entry(zip,"xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
        var sheet = new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        row(sheet,List.of("姓名","账号","最终成绩","排名","已参考","已完成","缺考","开始时间","提交时间","作答秒数","平台","操作系统","浏览器"));
        for (var p : people) row(sheet,Arrays.asList(p.learnerName(),p.learnerCode(),p.finalScore(),p.rank(),p.attended()?"是":"否",p.completed()?"是":"否",p.absent()?"是":"否",p.startedTime(),p.submittedTime(),p.usedSeconds(),p.platform(),p.operatingSystem(),p.browser()));
        entry(zip,"xl/worksheets/sheet1.xml",sheet.append("</sheetData></worksheet>").toString());
      }
      return out.toByteArray();
    } catch (IOException e) { throw new UncheckedIOException(e); }
  }
  private static void entry(ZipOutputStream zip,String name,String xml) throws IOException {
    zip.putNextEntry(new ZipEntry(name)); zip.write(xml.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
  }
  private static void row(StringBuilder xml,List<?> values) {
    xml.append("<row>");
    for (var value : values) {
      if (value instanceof Number) xml.append("<c><v>").append(value).append("</v></c>");
      else xml.append("<c t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(escape(value==null?"":value.toString())).append("</t></is></c>");
    }
    xml.append("</row>");
  }
  private static String escape(String value) {
    return value.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]","").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
  }
}
