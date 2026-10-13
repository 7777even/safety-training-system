# 规格增量索引

唯一规范正文：`specs/gapfill-batchA-importexport/spec.md`。覆盖编号 11、16、38：补用户 CSV 导入导出、考试名单文件导入、培训人员批量导入/导出（扫码加入 QR 本期延期）。采用零依赖 CSV（Excel 兼容），复用现有用户/考试/培训链路与 `Utf8CsvExporter`；新增 5 个权限点并同步权限资产。
