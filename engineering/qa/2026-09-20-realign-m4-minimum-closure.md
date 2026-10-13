# QA：2026-09-20-realign-m4-minimum-closure（后端）

日期：2026-09-20  
负责人：lims  
范围：仅后端；前端以 OpenAPI 为准另开。

## 结论

按 lims 人工确认「后端可交互即可」归档本 Change。前端页面验收不在本协作范围。Task 1～4、6 后端已交付；Task 5 三级查询与护栏可交互，缺少
ATTENDANCE 时拒绝正式归档且不写空快照。正式可下载档案文件仍需独立签到 Change，不伪装完成。

## Task 证据

| Task          | 后端状态         | 证据                                                                                                     |
|---------------|--------------|--------------------------------------------------------------------------------------------------------|
| 1 收口超范围入口     | 完成           | 计算批次/通用评价创建提交/档案版本导出/证书签发撤销重发公开入口已删                                                                    |
| 2 问卷最小闭环      | 完成           | `docs/api/survey.openapi.json`；零 DB 18                                                                 |
| 3 自动综合分与已结束结果 | 完成           | `CompositeScoreServiceImpl`；奖惩 CUD 触发 `synchronizeParticipant`；`GET /training/plans/{id}/ended-result` |
| 4 专家查课        | 完成           | 表单类型仅 COMPOSITE/EXPERT_INSPECTION；`/evaluation/expert-inspections/submit` 与计划内查询                       |
| 5 三级档案        | 后端可交互；正式文件残留 | 仅 ENDED；缺 ATTENDANCE → 409/9204；查询返回 `missingItems`；不写空签到快照                                            |
| 6 证书真实文件      | 完成           | 自动签发 PNG 写入 `us_sys_file_info`；预览；管理员与本人下载                                                             |
| 7 关闭          | 后端口径已归档      | 2026-09-20 人工确认归档；主规格已同步 `openspec/specs/m4-minimum-closure/spec.md`                                   |

## 未运行项

- 无：Docker 可用。已对齐 IT 基线 `20260920180000`，并跑通本次相关 MySQL IT。

## MySQL IT

命令：
`mvn -pl sts-service-admin -am -Pintegration-test test -Dtest=ArchiveServiceIT,CompositeScoreServiceIT,SurveyMapperIT,ArchiveMapperIT,CertificateMapperIT,EvaluationFormMapperIT,FlywayMigrationIT,TrainingMixedPlanLifecycleIT -Dsurefire.failIfNoSpecifiedTests=false`

- `ArchiveServiceIT` 3：非 ENDED 9202、缺 ATTENDANCE 9204、查询 missingItems
- `CompositeScoreServiceIT` 2：加权综合分落库、缺考试分不补零
- `SurveyMapperIT` 含 purpose/result_visibility 落库
- `FlywayMigrationIT` 5：init 基线与增量 V 对齐当前 `20260920180000`
- 同批还通过：ArchiveMapperIT、CertificateMapperIT、EvaluationFormMapperIT、TrainingMixedPlanLifecycleIT
