# QA：M3 培训执行后端（lims）

日期：2026-07-24  
Change：`openspec/changes/2026-07-24-implement-training-execution/`  
Owner：lims  
范围：仅后端；不含 `frontend/`

## 1. 交付范围

| 域 | 包 | migration |
|---|---|---|
| 培训类型/标准/模板/计划/执行 | `com.sts.training` | V20260724180000～184000 |
| 评价表单 | `com.sts.evaluation` | V20260724182000 |

- OpenAPI：`docs/api/training.openapi.json`、`docs/api/evaluation-form.openapi.json`
- Schema IT：`backend/sql/schema-training.sql`、`schema-evaluation.sql`
- 权限：`menus.json` moduleId 700

## 2. 自动化测试

### 零 DB（18 passed）

```bash
cd backend && mvn -pl sts-service-admin -am test \
  -Dtest=TrainingStandardServiceImplTest,TrainingTemplateServiceImplTest,EvaluationFormServiceImplTest,TrainingPlanServiceImplTest,TrainingAttendanceServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

| 测试类 | 用例数 | 结果 |
|--------|--------|------|
| TrainingStandardServiceImplTest | 4 | PASS |
| TrainingTemplateServiceImplTest | 3 | PASS |
| EvaluationFormServiceImplTest | 3 | PASS |
| TrainingPlanServiceImplTest | 5 | PASS |
| TrainingAttendanceServiceImplTest | 3 | PASS |

覆盖要点：标准 publish 护栏、模板 publish、评价表单不可变版本、计划发布快照哈希、generateExecutions 幂等、签到 upsert 唯一。

### Mapper IT（6 passed，需 Docker）

```bash
cd backend && mvn -pl sts-service-admin -am -Pintegration-test test \
  -Dtest=TrainingTypeMapperIT,EvaluationFormMapperIT,TrainingPlanMapperIT \
  -Dsurefire.failIfNoSpecifiedTests=false
```

| 测试类 | 用例数 | 结果 |
|--------|--------|------|
| TrainingTypeMapperIT | 2 | PASS |
| EvaluationFormMapperIT | 2 | PASS |
| TrainingPlanMapperIT | 2 | PASS |

覆盖要点：类型编码唯一与软删复用、评价表单版本/项目唯一、计划编码唯一与参训人 (plan,user) 唯一。

## 3. 编译

```bash
cd backend && mvn -pl sts-service-admin -am compile
```

结果：SUCCESS（700+ 源文件）

## 4. 已知缺口（lims 后端口径可接受）

1. **前端**：全部页面未实施；管理端/学员端以 OpenAPI 另开 Change。
2. **课程开课深链**：COURSE 执行 link 记录 `resource_id`，不强制调用课程域开课 API。
3. **评价作答 / 综合计算**：属 M4，本期仅表单定义侧。
4. **OpenAPI**：核心路径已登记；部分 CRUD 路径可按 Controller 增量补全 schema 细节。
5. **参训人 QR 加入**：表结构支持 `join_source=QR`，扫码流程未实施。

## 5. 结论

M3 后端纵向闭环（TRAIN-01～07）在 lims 口径下 **通过**：migration、Java API、零 DB 核心测试、Mapper IT、OpenAPI、menus、进度表均已同步。
