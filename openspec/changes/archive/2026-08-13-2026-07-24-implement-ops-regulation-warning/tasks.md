# Tasks：运营工作台 / 监管预警后端（M5）

前后端影响：仅后端；前端以 OpenAPI 为准另开。Owner：lims

## Task 1：OPS-01～03 工作台 / 统计 / 下钻导出

- 状态：已完成
- Owner：lims
- 输出：`com.sts.ops`；`sts_ops_export_job`；零 DB
- 验收标准：三类工作台摘要；四类统计；drilldown；export job
- [x] ops APIs + export table
- [x] 零 DB 测试

## Task 2：REG-01 监管汇总

- 状态：已完成
- Owner：lims
- 输出：overview API（读 regulatory_relation + plan/warning 计数）
- [x] RegulationOverviewService + 测试

## Task 3：REG-02 预警规则与检测

- 状态：已完成
- Owner：lims
- 输出：migration warning_*；rules/conditions；detect 幂等
- [x] migration + schema
- [x] Rule/Detect Service + IT/零 DB

## Task 4：REG-03 预警处置流水

- 状态：已完成
- Owner：lims
- 输出：assign/view/comment/close/ignore/reopen + action
- [x] Warning lifecycle APIs + 测试

## Task 5：REG-04 OpenAPI / 权限 / 进度 / QA

- 状态：已完成
- Owner：lims
- 输出：OpenAPI、menus、进度表 M5 关闭、QA
- [x] ops.openapi.json + regulation.openapi.json
- [x] menus + docs
- [x] 进度表 + QA
- [x] 目标测试通过
