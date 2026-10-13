package com.sts.exam.service;

import com.sts.exam.vo.ExamStatisticsPersonVO;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.assertj.core.api.Assertions.assertThat;

/** 验证导出是真实工作簿，保留零分且用户文本不可成为公式。 */
class ExamStatisticsXlsxTest {
  @Test void exportsValidOoxmlAndEscapesUserText() throws Exception {
    var person = new ExamStatisticsPersonVO(1L,1L,"=SUM(1)<&","账号","PASSED",java.math.BigDecimal.ZERO,1,true,true,false,null,null,0,null,null,null);
    var bytes = ExamStatisticsXlsx.write(List.of(person));
    int entries=0; String sheet="";
    try(var zip = new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)) {
      java.util.zip.ZipEntry entry;
      while((entry=zip.getNextEntry())!=null) {
        byte[] xml=zip.readAllBytes();
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        if(entry.getName().endsWith("sheet1.xml")) sheet=new String(xml,StandardCharsets.UTF_8);
        entries++;
      }
    }
    assertThat(entries).isEqualTo(5);
    assertThat(sheet).contains("=SUM(1)&lt;&amp;","<v>0</v>").doesNotContain("<f>");
  }
}
