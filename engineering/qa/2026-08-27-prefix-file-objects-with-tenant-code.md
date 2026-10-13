# QA：文件对象统一增加租户编码根目录

日期：2026-08-27  
OpenSpec：`openspec/changes/2026-08-27-prefix-file-objects-with-tenant-code/`  
Owner：lims

## 验收范围

- 新对象键统一为 `{ORG_CODE}/{既有对象键}`；机构编码来自当前租户主数据，不可由调用方覆盖。
- Local/MinIO 单文件、通用分片、课程封面、文档预览和视频派生写入均带租户根目录。
- 历史无前缀对象继续按已保存完整键读取、播放、删除和失败清理，不迁移、不双写。
- 课程封面接受历史无前缀键和当前机构前缀，拒绝其他机构前缀。
- OpenAPI 字段说明、路径示例与兼容语义同步。

## TDD 证据

1. Red：对象键、存储适配、multipart、封面、HLS 和派生测试要求新键以机构编码开头，旧实现仍写出 `source/`、`course-cover/`、
   `derivative/`。
2. Green：`TenantObjectKeyService` 解析不可变 `orgCode`；所有新写入入口经该组件加前缀；读取路径使用已保存键。
3. 真库：`TenantObjectKeyServiceIT` 从 DEMO 租户主数据解析编码，断言 `DEMO/source/2026/08/id.pdf`。

## 自动化结果

| 验证                                                                           | 结果                             |
|------------------------------------------------------------------------------|--------------------------------|
| 目标零 DB（对象键、Local/MinIO、multipart、封面、HLS、派生）                                  | 54 个通过，0 失败                    |
| `mvn -pl sts-service-admin -am test`                                         | 共运行 727 个，0 失败，1 个既有 qpdf 条件跳过 |
| `TenantObjectKeyServiceIT`                                                   | 1 个通过                          |
| `mvn -pl sts-service-admin -am -Pintegration-test test`                      | 193 个通过，0 失败                   |
| `jq empty docs/api/course.openapi.json`                                      | 通过                             |
| `jq empty docs/api/system-admin.openapi.json`                                | 通过                             |
| `openspec validate 2026-08-27-prefix-file-objects-with-tenant-code --strict` | 通过                             |
| `git diff --check`                                                           | 通过                             |

## Review

- 规格符合性：通过。同一私有 bucket 下以机构编码作为对象键首段；HTTP 路径、权限和错误码未新增；历史对象未迁移。
- 代码质量：通过。前缀生成集中，读取/删除不二次拼接；HLS 以已登记 manifest 为锚点兼容新旧布局；封面跨机构前缀被拒绝。

## 未执行项

- 未实施前端页面、Mock 或联调，前端以 OpenAPI 为准另开。
- 未迁移、重命名或改写任何历史对象及数据库引用。
- 未新增 bucket、日目录、Migration、权限码或生产依赖。
