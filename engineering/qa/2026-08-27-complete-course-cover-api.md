# QA：课程封面上传与受控读取

日期：2026-08-27  
OpenSpec：`openspec/changes/2026-08-27-complete-course-cover-api/`  
Owner：lims

## 验收范围

- 课程封面 JPG/JPEG、PNG、WebP 上传与 10 MiB 上限。
- `course-cover/{yyyy}/{MM}/{uuid}.{ext}` 私有对象键；`source`、`derivative` 保持不变。
- 课程创建、替换、清空封面及跨机构、逻辑删除、伪造文件拒绝。
- 按课程 ID 内联查看和显式下载；安全响应头、稳定 ETag 与错误语义。
- OpenAPI Controller 分组、字段、示例、权限和错误码同步。

## TDD 证据

1. 首轮 Red：目标测试在测试编译阶段因 `CourseCoverService`、`CourseCoverController` 不存在而失败。
2. 第二轮 Red：真库测试证明 MyBatis 默认 null 更新不会清空 `cover_file_id`。
3. Green：以 `@TableField(updateStrategy = FieldStrategy.ALWAYS)` 明确课程封面列的全量 PUT 清空语义；全部目标测试转绿。
4. Review 修正：封住通用上传伪造保留目录、同步系统文件契约、严格校验对象键、分离保存 400 与读取 404、补齐对象写入补偿和 ETag；
   底层对象缺失或读取异常不透传 bucket、对象键、本地路径或 SDK 信息。

## 自动化结果

| 验证                                                                | 结果                             |
|-------------------------------------------------------------------|--------------------------------|
| 课程封面及受影响目标零 DB                                                    | 47 个通过，0 失败                    |
| `mvn -pl sts-service-admin -am test`                              | 共运行 718 个，0 失败，1 个既有 qpdf 条件跳过 |
| `CourseCoverPersistenceIT`                                        | 2 个通过，覆盖租户注入、映射、清空、跨租户和逻辑删除    |
| `mvn -pl sts-service-admin -am -Pintegration-test test`           | 192 个通过，0 失败                   |
| `jq empty docs/api/course.openapi.json`                           | 通过                             |
| `openspec validate 2026-08-27-complete-course-cover-api --strict` | 通过                             |
| `git diff --check`                                                | 通过                             |

## Review

- 规格符合性：通过。实现范围与确认后的 Task 1 一致，课程与系统文件 OpenAPI 均已同步，没有修改 `source`、`derivative`
  、数据库结构或前端。
- 代码质量：通过。上传来源不可由通用文件接口伪造；租户与逻辑删除由真实 MyBatis IT 证明；二进制响应及失败响应均不暴露存储实现。

## 未执行项

- 未实施前端页面、Mock 或联调，前端以 `docs/api/course.openapi.json` 为准另开。
- 未迁移旧对象，未操作生产环境或生产数据。
