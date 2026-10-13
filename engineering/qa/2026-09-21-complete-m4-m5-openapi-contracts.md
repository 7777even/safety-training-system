# QA：补齐 M4/M5 OpenAPI 机器可读契约

日期：2026-09-21  
负责人：lims  
OpenSpec：`2026-09-21-complete-m4-m5-openapi-contracts` Task 1  
范围：后端公开模型与契约；不修改数据库、权限模型或前端。

## 结论

通过。2026-09-20 M4/M5 最小化重构后的五份契约已与当前 Controller、DTO、VO
和鉴权事实对齐；实施中发现并按人工确认修复了证书公开模型继续读写已隐藏字段的问题。数据库兼容列和历史值继续保留。

## 交付内容

| 契约/模型                            | 结果                                                                                  |
|----------------------------------|-------------------------------------------------------------------------------------|
| `certificate.openapi.json`       | 14 个 operation 与 Controller 一致；补齐模板详情/更新/删除、规则删除；请求、分页、VO、ID、void、binary 均具体建模      |
| 证书公开 DTO/VO                      | 规则创建请求删除 `conditionValue/collectionInstruction`；证书响应删除 `revokedTime`；Entity 与数据库列保留 |
| `evaluation-result.openapi.json` | 3 个当前 operation 保留；人工评价写入与综合计算批次残留组件清零；评价记录和专家查课字段补齐                                |
| `survey.openapi.json`            | fill、submit、stats 均增加 `x-permission: authenticated`；fill/submit 补标准 403 响应          |
| `ops.openapi.json`               | 企业工作台 data 改为 `OpsWorkbenchSummaryVO` 具体模型并补标准错误响应                                  |
| `regulation.openapi.json`        | 15 个 operation 与 Controller 一致；工作台、学习统计、预警及培训下钻全部引用具体 VO、列表或分页 Schema               |

## TDD 证据

- Red：新增 `CertificateControllerTest.publicModels_excludeHiddenFields` 后，目标测试 4 例中 1 例失败；断言准确发现
  `CertificateIssueRuleCreateRequest` 仍声明两个隐藏字段。
- Green：删除三个公开字段并同步 OpenAPI 后：

```text
mvn -pl sts-service-admin -am test \
  -Dtest=CertificateControllerTest,CertificateServiceImplTest,CertificateTemplateServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：7 tests，0 failures，0 errors；Admin 生产源码 1021 个文件编译通过。

## 契约验证

1. 全量 `docs/api/*.openapi.json` 逐文件执行 JSON 解析：通过。
2. Controller-operation 双向集合比对：certificate 14、evaluation-result 3、survey 14、ops 1、regulation 15；差异均为 0。
3. Certificate、Ops、Regulation、Evaluation 共 18 个目标 DTO/VO 与 Schema property 集合比对：差异 0。
4. 五份契约本地 `$ref` 解析检查：悬空引用 0。
5. operation 的 Controller tag、summary、description、`x-permission`、参数/请求/成功 example、401/403 检查通过；新增或修改
   property 均有中文 description。
6. `evaluation-result.openapi.json` 中已删除的 CalculationRun、人工 EvaluationRecord 写入组件：残留 0。
7. 证书公开 DTO/VO 与 OpenAPI 中 `conditionValue`、`collectionInstruction`、`revokedTime`：残留 0；Entity 兼容字段仍存在。
8. `openspec validate 2026-09-21-complete-m4-m5-openapi-contracts --strict`：通过。
9. `git diff --check`：通过。

## 未运行项

- 未运行 MySQL IT：数据库结构、Entity、Mapper、SQL 和 migration 均未修改。
- 未执行前端代码生成或页面联调：lims 协作边界下由前端依据修复后的 OpenAPI 另开。

## 双轴 Review

- 规格符合性：通过。证书隐藏字段按已确认 M4 规格收口，未恢复已删除能力；其他修改只校正当前运行时契约。
- 代码与契约质量：通过。最小生产代码改动有定向回归；路径、字段、权限、媒体类型、错误响应、示例和引用均完成复核。

## 阻塞与遗留

无本次交付阻塞。前端如已基于旧的无类型 data 或隐藏证书字段手写类型，需要在独立前端任务中改为消费当前 Schema。

