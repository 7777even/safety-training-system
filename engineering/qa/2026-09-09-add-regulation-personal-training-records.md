# QA：监管个人培训记录下钻

## 范围

OpenSpec Change：`add-regulation-personal-training-records` Task 1.1。

四个监管只读 GET：计划分页/详情、参训人、参训人结果。须 REGULATOR 且已切换 `X-Target-Tenant-Id`。无 DDL。未改前端。

## 自动化验证

在 `backend/` 执行：

```text
mvn -pl sts-service-admin -am test -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=RegulationTrainingRecordServiceImplTest,RegulationControllerTrainingRecordTest
```

→ 7 项通过。

- `docs/api/regulation.openapi.json` JSON 解析通过。

未跑 MySQL IT（委托现有查询，无新表）。

## Review

规格符合性：通过。
代码质量：通过。未改 `frontend/`。
