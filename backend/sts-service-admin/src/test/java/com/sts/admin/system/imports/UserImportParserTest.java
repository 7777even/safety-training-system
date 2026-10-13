package com.sts.admin.system.imports;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserImportParserTest {

    @Test
    void parsesBomAndQuotedCells() {
        String csv = "登录名,姓名,手机号,部门编码,状态\n"
                + "zhangsan,张三,13800000001,D_RD,1\n"
                + "lisi,\"李,四\",13800000002,D_QA,0\n";
        UserImportParser.ParseResult r = UserImportParser.parse(csv.getBytes(StandardCharsets.UTF_8));
        assertTrue(r.errors().isEmpty(), "不应有文件级错误: " + r.errors());
        assertEquals(2, r.rows().size());
        assertEquals("zhangsan", r.rows().get(0).userCode());
        assertEquals("李,四", r.rows().get(1).userName());
        assertEquals(0, r.rows().get(1).status());
    }

    @Test
    void toleratesUtf8Bom() {
        String csv = "登录名,姓名,手机号,部门编码\nabc,张三,13800000001,D_RD\n";
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = new byte[bom.length + csv.getBytes(StandardCharsets.UTF_8).length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(csv.getBytes(StandardCharsets.UTF_8), 0, content, bom.length, csv.getBytes(StandardCharsets.UTF_8).length);
        UserImportParser.ParseResult r = UserImportParser.parse(content);
        assertEquals(1, r.rows().size());
        assertEquals("abc", r.rows().get(0).userCode());
    }

    @Test
    void rejectsTooFewColumns() {
        String csv = "登录名,姓名,手机号,部门编码\nonlythree,张三,13800000001\n";
        UserImportParser.ParseResult r = UserImportParser.parse(csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, r.errors().size());
    }

    @Test
    void rejectsBadStatusAndBlankRequired() {
        String csv = "登录名,姓名,手机号,部门编码,状态\n"
                + "ok,张三,13800000001,D_RD,1\n"
                + "bad,张三,13800000001,D_RD,xx\n"
                + ",张三,13800000001,D_RD,1\n";
        UserImportParser.ParseResult r = UserImportParser.parse(csv.getBytes(StandardCharsets.UTF_8));
        // 第 2 行状态非法 + 第 3 行登录名为空
        assertEquals(2, r.errors().size());
        assertEquals(1, r.rows().size());
        assertFalse(r.errors().isEmpty());
    }

    @Test
    void emptyFileHasFileError() {
        UserImportParser.ParseResult r = UserImportParser.parse("".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("文件内容为空"), r.errors());
    }
}
