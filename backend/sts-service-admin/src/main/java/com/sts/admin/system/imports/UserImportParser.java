package com.sts.admin.system.imports;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户导入 CSV 纯解析（零依赖，Excel 兼容 UTF-8 BOM）。
 * 仅负责把字节解析为带行号的行记录，业务校验交由 Service。
 */
public final class UserImportParser {

    /** 导入表头顺序：登录名,姓名,手机号,部门编码,状态(可选)。 */
    public static final List<String> EXPECTED_HEADERS = List.of("登录名", "姓名", "手机号", "部门编码", "状态");

    private UserImportParser() {
    }

    public record Row(int line, String userCode, String userName, String phone, String deptCode, Integer status) {
    }

    public record ParseResult(List<String> headers, List<Row> rows, List<String> errors) {
    }

    public static ParseResult parse(byte[] content) {
        List<String> errors = new ArrayList<>();
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        String[] rawLines = text.split("\\r?\\n");
        List<String> lines = new ArrayList<>();
        for (String l : rawLines) {
            if (l.isEmpty()) {
                continue;
            }
            lines.add(l);
        }
        if (lines.isEmpty()) {
            return new ParseResult(List.of(), List.of(), List.of("文件内容为空"));
        }
        List<String> headers = splitCsvLine(lines.get(0));
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> cells = splitCsvLine(lines.get(i));
            int lineNo = i + 1;
            if (cells.size() < 4) {
                errors.add("第" + lineNo + "行：列数不足，至少需要 登录名,姓名,手机号,部门编码");
                continue;
            }
            String userCode = blankToNull(cells.get(0));
            String userName = blankToNull(cells.get(1));
            String phone = blankToNull(cells.get(2));
            String deptCode = blankToNull(cells.get(3));
            Integer status = 1;
            if (cells.size() >= 5 && cells.get(4) != null && !cells.get(4).isBlank()) {
                try {
                    status = Integer.parseInt(cells.get(4).trim());
                } catch (NumberFormatException e) {
                    errors.add("第" + lineNo + "行：状态必须是整数");
                    continue;
                }
            }
            if (userCode == null || userName == null || phone == null || deptCode == null) {
                errors.add("第" + lineNo + "行：登录名/姓名/手机号/部门编码均不能为空");
                continue;
            }
            rows.add(new Row(lineNo, userCode.trim(), userName.trim(), phone.trim(), deptCode.trim(), status));
        }
        return new ParseResult(headers, rows, errors);
    }

    private static String blankToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    /** 按 RFC4180 简化版拆分单行：支持双引号包裹与转义。 */
    static List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuote = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuote = true;
                } else if (c == ',') {
                    cells.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        cells.add(cur.toString());
        return cells;
    }
}
