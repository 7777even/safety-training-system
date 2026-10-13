# Tasks：课程发布版本

> 确认本 Change 后实施。前端不在本协作范围。按 Task 顺序一次一个。

## Task 1：发布版本数据层与快照发布能力

- 状态：已实施
- Owner：AI
- 输入：design.md；已评审 DDL `sts_course_version`；已有草稿编排与 `AssetReferenceService`
- 输出：migration、Entity/Mapper、快照组装、发布/幂等/下线、版本查询 API、权限、OpenAPI、单测与必要 IT
- API 依赖：本 Task 同步写入 `docs/api/course-publish-version.openapi.json`（或扩写现有课程契约）
- 数据影响：新增 `sts_course_version`；更新课程 `current_version_id` / `publish_status` / `published_time`
- 验收标准：
  - [x] 首次发布 `version_no=1`，课程变为 PUBLISHED，快照含章节/内容项/材料规则/直播/下载素材
  - [x] 内容变更后再发布 `version_no` 递增，旧版本 JSON/哈希不变
  - [x] 相同内容重复发布幂等（不插新行）
  - [x] 无章节 / 不可用素材 / LIVE 无场次 → 明确错误码
  - [x] 版本无 update/delete 接口；下线后历史版本仍可查
  - [x] 权限点 `course:course:publish` 已登记 menus.json
- 测试方式：`CoursePublishServiceImplTest`（零 DB）；可选 `CourseVersionMapperIT`；`mvn test` 相关用例通过
- 测试结果：`CoursePublishServiceImplTest` 5/5 通过；`CourseServiceImplTest` 8/8 通过（2026-07-22）

## Task 2（可选后续，本 Change 可不做）

- 与步骤 5 开课联动的“已开课版本不可被误操作”守卫——开课表未建，Explicit 非本 Change。
