## Why

课程域（含素材）业务能力已按多轮 Change 落地，但测试覆盖不均：早期分类/课程有 Mapper IT，后期开课/学习/发布/素材缺少带 DB
集成测试；部分 Service / Web 切片零 DB 测试也未补齐。需要用一次专项 Change 把「零 DB + 真 MySQL」两套测试补全，作为回归基线。

## What Changes

- 补齐缺失的零 DB 单测与 Web 切片测试（Mockito / MockMvc）。
- 补齐关键 Mapper / 链路的 Testcontainers MySQL 集成测试。
- 不新增业务功能、不改 API 行为（除非测出 bug 再单开 fix）。

## Capabilities

### New Capabilities

- `course-test-coverage`: 课程与素材域测试覆盖基线（零 DB + 带 DB）。

### Modified Capabilities

- （无需求变更）

## Impact

- 仅 `sts-service-admin` 的 `src/test` 与 `src/integrationTest`。
- 依赖现有 `AbstractMysqlIntegrationTest`、`CourseMapperItConfig`、schema-course/asset。
- 前端不在范围。
