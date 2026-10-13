# 独立考试延期策略列标注 QA（2026-09-20）

## 验收范围

- `sts_exam_policy` 中 11 个延期策略列保留类型、空值约束和历史数据语义，仅修改 COMMENT。
- 水印、防作弊总开关、端侧、补考、答案详情、解析和成绩可见性列不标注为暂时隐藏。
- 同步最新 init SQL、Admin Flyway migration 和 `sts.init.schema-version`。
- 不修改前端；目标字段在现有考试编辑页和详情摘要中已经隐藏，水印仍展示。

## 验证结果

| 验证项                   | 结果   | 证据                                                                                     |
|-----------------------|------|----------------------------------------------------------------------------------------|
| OpenSpec 严格校验         | 通过   | `openspec validate 2026-09-20-close-standalone-exam-anticheat-and-visibility --strict` |
| Flyway migration 资源测试 | 通过   | `FlywayMigrationResourcesTest` 1/1                                                     |
| MySQL 8.0.45 SQL 实执行  | 通过   | 临时库执行同形 `ALTER TABLE` 后，11 个目标列 COMMENT 均命中 `【暂时隐藏】原用于：%`；临时库已删除                       |
| 差异格式检查                | 通过   | `git diff --check`                                                                     |
| Testcontainers 迁移集成测试 | 环境阻塞 | 本机 Testcontainers 未找到有效 Docker environment，测试在 MySQL 容器启动前失败，未进入业务断言                   |

## 双轴 Review

### 规格符合性

- 11 个目标列与人工确认清单一致，无多标、漏标。
- `is_watermark_enabled`、`is_anticheat_enabled` 及当前补考、结果可见性字段未改。
- 列未删除，类型、NULL/NOT NULL 和默认值未改变。

### 代码质量

- migration 只执行 COMMENT 变更，不包含数据更新、列删除或索引变化。
- init SQL 与 migration 使用相同原用途和恢复约束文案。
- 版本号从当前工作树的 `20260920190000` 顺延为 `20260920200000`，未修改既有 migration。

## 结论

实现与人工确认范围一致。自动化 MySQL IT 因本机 Testcontainers 环境阻塞未执行成功；已有 MySQL 8.0.45 临时库实执行验证覆盖本次
SQL 语法和 11 列注释结果。
