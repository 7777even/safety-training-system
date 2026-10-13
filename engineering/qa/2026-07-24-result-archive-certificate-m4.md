# QA：M4 综合结果 / 档案 / 证书后端（lims）

日期：2026-07-24  
Change：`openspec/changes/2026-07-24-implement-result-archive-certificate/`  
Owner：lims  
范围：仅后端；不含 `frontend/`

## 1. 交付范围

| 域 | 包 | migration |
|---|---|---|
| 问卷 | `com.sts.survey` | V20260724190000 |
| 评价记录/综合计算 | `com.sts.evaluation`（扩展） | V20260724191000 |
| 培训档案 | `com.sts.archive` | V20260724192000 |
| 证书 | `com.sts.certificate` | V20260724193000 |

- OpenAPI：`survey.openapi.json`、`evaluation-result.openapi.json`、`archive.openapi.json`、`certificate.openapi.json`
- Schema IT：`schema-survey.sql`、`schema-evaluation.sql`（追加 record/calc）、`schema-archive.sql`、`schema-certificate.sql`
- 权限：`menus.json` moduleId 800

## 2. 自动化测试

### 零 DB（9 passed）

```bash
cd backend && mvn -pl sts-service-admin -am test \
  -Dtest=SurveyServiceImplTest,SurveyInstanceServiceImplTest,CalculationRunServiceImplTest,ArchiveServiceImplTest,CertificateServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

| 测试类 | 用例数 | 结果 |
|--------|--------|------|
| SurveyServiceImplTest | 2 | PASS |
| SurveyInstanceServiceImplTest | 1 | PASS |
| CalculationRunServiceImplTest | 2 | PASS |
| ArchiveServiceImplTest | 1 | PASS |
| CertificateServiceImplTest | 3 | PASS |

覆盖要点：问卷 publish 不可变版本、duplicate submit 拒绝、calculation confirm 正式唯一/supersede、archive 新版本递增、证书幂等签发/撤销/重发。

### Mapper IT（6 passed，需 Docker）

```bash
cd backend && mvn -pl sts-service-admin -am -Pintegration-test test \
  -Dtest=SurveyMapperIT,EvaluationCalculationMapperIT,ArchiveMapperIT,CertificateMapperIT \
  -Dsurefire.failIfNoSpecifiedTests=false
```

| 测试类 | 用例数 | 结果 |
|--------|--------|------|
| SurveyMapperIT | 2 | PASS |
| EvaluationCalculationMapperIT | 1 | PASS |
| ArchiveMapperIT | 1 | PASS |
| CertificateMapperIT | 2 | PASS |

覆盖要点：问卷编码软删复用、版本唯一、official_marker 唯一、档案版本唯一、证书 holder+source 唯一与证书号软删复用。

## 3. 编译

```bash
cd backend && mvn -pl sts-service-admin -am compile
```

结果：SUCCESS

## 4. 已知缺口（lims 后端口径可接受）

1. **前端**：全部 M4 页面未实施；以 OpenAPI 另开 Change。
2. **综合分计算**：MVP 按 score_rule 权重聚合 participant 分数字段；未深链 exam/offline 明细表逐条换算。
3. **档案导出**：export job 仅创建 PENDING 记录，无异步生成文件。
4. **证书文件**：`certificate_file_id` 使用占位 1L；内容以 snapshot JSON 为准。
5. **问卷 target_binding**：表已建，绑定 API 未单独暴露（instance 直建 MVP）。
6. **OpenAPI**：核心路径已登记；部分 CRUD schema 细节可按 Controller 增量补全。

## 5. 结论

M4 后端纵向闭环（RESULT-01～04、ARCHIVE-01、CERT-01～02）在 lims 口径下 **通过**：migration、Java API、零 DB 核心测试、Mapper IT、OpenAPI、menus、进度表均已同步。
