package com.sts.common.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用 CSV 解析（零依赖，Excel 兼容 UTF-8 BOM）。
 * 仅负责把字节解析为带行号的行记录；业务校验交由调用方。
 */
public final class CsvParser {

    private CsvParser() {
    }

    /** 一行：原始行号（含表头，从 1 开始）与单元格。 */
    public record Line(int number, List<String> cells) {
    }

    public static List<Line> parse(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<Line> lines = new ArrayList<>();
        String[] raw = text.split("\\r?\\n");
        int n = 0;
        for (String r : raw) {
            if (r.isEmpty()) {
                continue;
            }
            n++;
            lines.add(new Line(n, split(r)));
        }
        return lines;
    }

    /** 按 RFC4180 简化版拆分单行：支持双引号包裹与转义。 */
    static List<String> split(String line) {
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
