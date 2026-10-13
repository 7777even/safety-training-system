# Tasks：补齐课程封面上传与受控读取

> 状态：已人工确认（2026-08-27）；Task 1 已完成。
>
> Owner：lims
>
> 执行约束：确认后一次只执行一个 Task；新增行为采用 Red → Green → Refactor；测试与双轴 Review 通过后再更新 Task 和进度记录。

## Task 1：完成课程封面后端纵向闭环

- 状态：已完成（2026-08-27）
- Owner：lims
- 输入：既有课程 `cover_file_id`、`us_sys_file_info`、私有 Local/MinIO 存储适配、课程发布快照、
  `docs/api/course.openapi.json`，以及已确认的 `course-cover/{yyyy}/{MM}/...` 目录规则。
- 输出：课程封面上传、课程创建/更新关联校验、按课程受控查看/下载、OpenAPI 契约、自动化测试、QA/Retro 和进度记录。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：修改 `docs/api/course.openapi.json`，新增 `POST /api/course/covers`、
  `GET /api/course/courses/{courseId}/cover`，并为课程创建/更新请求补齐可空 `coverFileId`。
- 数据影响：无数据库结构或 migration 变化；复用 `us_sys_file_info` 与 `sts_course_course.cover_file_id`；新封面对象写入
  `course-cover/{yyyy}/{MM}/...`；不迁移旧对象，不修改 `source`、`derivative`。
- 验收标准：
  - [x] 先以自动化测试观察当前无课程封面上传/读取能力、DTO 不保存 `coverFileId` 的预期失败，再开始实现。
  - [x] 具备课程新增或编辑权限的当前机构用户可上传不超过确认上限的合法 JPG/JPEG、PNG、WebP，返回文件元数据但不暴露
    bucket、对象键和永久地址。
  - [x] 新封面在 Local 与 MinIO 下均写入 `course-cover/{yyyy}/{MM}/{uuid}.{ext}`；现有 `source`、`derivative` 路径行为不变。
  - [x] 空文件、超限图片、扩展名/MIME/文件签名不一致和非图片上传返回明确 HTTP 400 + 课程域错误码，失败不得留下不一致文件记录。
  - [x] 创建、更新和清空课程封面正确持久化；跨机构文件、非 `course-cover` 文件、不存在或已删除文件不得被关联。
  - [x] 已认证的同机构用户可通过课程 ID 内联查看封面，并可显式下载；跨机构、课程不存在或课程无封面不泄露文件信息。
  - [x] 课程发布快照继续冻结所选 `coverFileId`；换封面、清空或删除课程不误删文件对象。
  - [x] `docs/api/course.openapi.json` 按 `CourseCoverController` 分类，包含每个接口/字段中文说明、完整请求与响应
    example、错误码和权限语义，并与实现一致。
  - [x] Admin 零 DB 测试、受影响 MySQL IT、OpenSpec 严格校验和 `git diff --check` 通过；规格符合性与代码质量 Review
    结论均为“通过”。
- 测试方式：按 TDD 依次运行目标 Controller/Service/存储测试，再运行
  `mvn -pl sts-service-admin -am test` 与 `mvn -pl sts-service-admin -am -Pintegration-test test`；最后执行 OpenSpec
  严格校验和差异检查。

### 完成记录

- Red：新增课程封面 Service、Controller、课程保存和存储路径测试后，测试编译因生产能力不存在而失败；补充真库清空封面测试后，观察到
  MyBatis 默认跳过 null、数据库仍保留旧 `cover_file_id` 的失败。
- Green：新增 `CourseCoverController`、`CourseCoverServiceImpl` 和上传 VO；课程创建/更新补齐 `coverFileId`；Local/MinIO
  新封面统一写入
  `course-cover/{yyyy}/{MM}/{uuid}.{ext}`；课程实体对封面列使用显式 null 更新策略，支持真实清空。
- 安全收口：通用单文件和分片上传拒绝保留目录 `course-cover`；关联同时校验租户、逻辑删除、业务目录、严格对象键与图片后缀；读取只按课程真实关联开放，
  返回安全 Content-Disposition、Content-Length 和稳定 ETag；底层对象缺失或读取异常统一映射为不泄露存储细节的
  404/5092；数据库登记失败时补偿对象删除且不覆盖原异常。
- 契约：`docs/api/course.openapi.json` 新增上传和按课程查看/下载路径、`CourseCoverController` tag、请求/响应 schema、错误码
  5090～5092，
  并为课程创建、更新、列表补齐 `coverFileId`；`docs/api/system-admin.openapi.json` 同步 `course-cover` 保留目录错误语义。
- 验证：目标零 DB 47/47；最终 Admin 零 DB 共运行 718 个，0 失败（含 1 个既有 qpdf 条件跳过）；目标 `CourseCoverPersistenceIT`
  2/2；完整 MySQL IT
  192/192；OpenAPI JSON、OpenSpec 严格校验和 `git diff --check` 通过。
- 双轴 Review：规格符合性“通过”；代码质量“通过”。未修改数据库结构、权限模型、生产依赖、前端、`source` 或 `derivative` 路径。
- QA：`engineering/qa/2026-08-27-complete-course-cover-api.md`；Retro：
  `engineering/retro/2026-08-27-complete-course-cover-api.md`。
