# Tasks：统一课程源文件下载模式

> 状态：已完成（2026-08-14）。
>
> Owner：lims

## Task 1：修复课程附件下载模式判断并统一契约

- 状态：已完成（2026-08-14）
- Owner：lims
- 输入：人工确认后的 proposal/design；既有课程枚举 `NONE/ALL/PARTIAL`；冻结课程版本与附件清单
- 输出：统一后的详情附件可用状态、学员附件下载授权、`docs/api/course.openapi.json`、自动化测试、QA/Retro 和进度记录
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：既有 `GET /course/learning/enrollments/{enrollmentId}` 与
  `GET /course/learning/enrollments/{enrollmentId}/download-assets/{assetId}/file`；接口结构不变，仅纠正
  `sourceDownloadMode` 枚举及既有下载授权行为
- 数据影响：不修改数据库和存量数据，不创建 migration
- 验收标准：
  - [x] `sourceDownloadMode=NONE` 时，详情附件返回 `SOURCE_DOWNLOAD_DISABLED`，文件下载返回业务码 5087
  - [x] `sourceDownloadMode=ALL` 时，冻结清单内的启用且有效附件可生成下载地址并完成受控下载
  - [x] `sourceDownloadMode=PARTIAL` 时，仅冻结 `downloadAssets` 清单内的启用且有效附件可生成下载地址并完成受控下载
  - [x] 空值或其他未知值均失败关闭，不生成下载地址且不读取文件存储；未新增 `ALLOW` 专项回归用例
  - [x] 冻结附件归属、附件/素材状态、处理状态、来源类型、文件记录和存储可用性校验保持不变
  - [x] `docs/api/course.openapi.json` 中课程创建、更新和学员响应统一使用 `NONE/ALL/PARTIAL`，无学员下载语义下的 `ALLOW`
    示例或枚举
  - [x] 新增/修改测试按 Red → Green → Refactor 完成，受影响零 DB 测试和 Admin 全量零 DB 测试通过
  - [x] 规格符合性与代码质量双轴 Review 结论均为“通过”，QA、Retro、Task 和进度表证据一致
- 测试方式：
  - 先修改/新增 `LearnerCourseResourceAssemblerTest`、`LearnerCourseDownloadServiceTest`，运行目标测试并观察预期失败
  - 在 `backend/` 运行目标零 DB 测试，再运行 `mvn -pl sts-service-admin -am test`
  - 本 Change 默认不运行 IT；若实施中触及 Mapper、SQL、Entity 或持久化事务，则升级并运行相关 IT
  - `openspec validate 2026-08-14-fix-course-source-download-mode --strict`
  - `git diff --check`

- 验证结果（2026-08-14）：
  - Red：目标 23 个用例中 9 failure、1 error，真实 `ALL/PARTIAL` 被旧 `ALLOW` 判断错误拒绝
  - Green：目标测试 23/23、Admin 全量零 DB 609/609 通过，均为 0 failure、0 error、0 skipped
  - IT：未运行；本 Change 未修改 Entity、Mapper、自定义 SQL、schema、索引或持久化事务
  - OpenAPI JSON、OpenSpec 严格校验和 `git diff --check` 均通过
  - 规格符合性 Review：通过；代码质量 Review：通过
