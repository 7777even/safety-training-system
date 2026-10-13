package com.sts.common.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvParserTest {

    @Test
    void parsesRowsWithLineNumbersAndQuotes() {
        String csv = "登录名,姓名,手机号\nzhangsan,张三,13800000001\nlisi,\"李,四\",13800000002\n";
        List<CsvParser.Line> lines = CsvParser.parse(csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(3, lines.size());
        assertEquals(1, lines.get(0).number());
        assertEquals("登录名", lines.get(0).cells().get(0));
        assertEquals("李,四", lines.get(2).cells().get(1));
    }

    @Test
    void toleratesUtf8Bom() {
        String csv = "登录名,姓名,手机号\nabc,张三,13800000001\n";
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = new byte[bom.length + csv.getBytes(StandardCharsets.UTF_8).length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(csv.getBytes(StandardCharsets.UTF_8), 0, content, bom.length, csv.getBytes(StandardCharsets.UTF_8).length);
        List<CsvParser.Line> lines = CsvParser.parse(content);
        assertEquals(2, lines.size());
        assertEquals("abc", lines.get(1).cells().get(0));
    }
}
