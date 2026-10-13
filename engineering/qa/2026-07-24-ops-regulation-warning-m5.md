# QA：运营工作台 / 监管预警后端（M5）

日期：2026-07-24  
Owner：lims  
Change：`openspec/changes/2026-07-24-implement-ops-regulation-warning/`

## 范围

- 包：`com.sts.ops`、`com.sts.regulation`
- Migration：`V20260724200000`、`V20260724201000`
- OpenAPI：`docs/api/ops.openapi.json`、`docs/api/regulation.openapi.json`
- 未实施前端

## 自动化测试

### 零 DB（`backend/`）

```bash
mvn -pl sts-service-admin -am test \
  -Dtest=OpsWorkbenchServiceImplTest,OpsStatsServiceImplTest,WarningRuleServiceImplTest,WarningDetectServiceImplTest,WarningActionServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：**6 passed**（2026-07-24）

### 带 DB IT（需 Docker）

```bash
mvn -pl sts-service-admin -am -Pintegration-test test \
  -Dtest=RegulationWarningMapperIT,OpsExportJobMapperIT \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：**3 passed**（RegulationWarningMapperIT 2 + OpsExportJobMapperIT 1）

## 验收核对

| 能力 | 证据 |
|------|------|
| OPS-01 三类工作台 | `/ops/workbench/enterprise|learner|teacher`；Mapper 真实计数 |
| OPS-02 四类统计 | `/ops/stats/courses|exams|trainings|results` |
| OPS-03 下钻/导出 | `/ops/stats/drilldown`；`/ops/exports` PENDING→SUCCEEDED |
| REG-01 监管汇总 | `/regulation/overview` + regulatory_relation |
| REG-02 规则/检测 | warning_* 五表；detect dedup_key + evidence_key 幂等 |
| REG-03 处置流水 | view/assign/comment/close/ignore/reopen + action 表 |
| 权限菜单 | `menus.json` moduleId 900/950；`ops:*` / `regulation:*` |

## 已知简化（暂定）

- 检测指标仅 PLAN_TOTAL_HOURS、PLAN_ONLINE_RATIO；同步执行。
- 导出任务同步成功，result_file_id 占位为 1。
- 教师工作台按 teacherUserId 过滤线下场次，未做跨机构数据范围深链。

## 结论

M5 后端口径 **通过**；前端 E2E 另开。
