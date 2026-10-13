# Tasks：移除正式培训档案的伪出勤前置条件

> 状态：**已完成**（2026-09-24 复核）。Task 1～3 全部完成；Task 2 的自动化验收（`ArchiveServiceIT` 5/5，含 Flyway 后文件 ID 回读断言）与真实验收（真实对象存储生成/下载档案文件、结果不完整 409/9204、管理员真实浏览器下载）已于 2026-09-24 由档案闭环纵向验收实跑覆盖，证据见 `engineering/qa/2026-09-24-close-training-archive-loop.md`。头条早前记录的「受环境和登录态限制 / 环境阻断」已作废。

## Task 1：归档判定与契约收口

- Owner：后端。
- 是否完成：是。
- 输入：既定线下“教师评分即完成”规则、归档服务、archive OpenAPI。
- 输出：归档不再产生 `ATTENDANCE`；完整结果的 ENDED 培训可归档；不完整结果仍返回 `RESULTS` 与 9204。
- 前后端/数据影响：后端与 `docs/api/archive.openapi.json`；无数据库、权限、菜单或新接口变更。
- 验收与测试：先有失败测试；新增 `ArchiveServiceImplTest` 3/3 通过，覆盖完整结果不含 `ATTENDANCE`、不完整结果仅返回 `RESULTS`、当前归档版本文件 ID 回读；OpenAPI JSON 同步。

## Task 2：真实文件与 PC 下载回归

- Owner：后端/前端。
- 是否完成：是（实现与真实下载验收均完成，2026-09-24）。
- 输入：Task 1 完成的归档服务和既有文件端点。
- 输出：真实档案文件按既有端点下载；PC 仅依据通用 `missingItems` 与 `fileInfoId` 展示状态，不再提示签到前置。
- 发现的范围差异：既有 `ArchiveServiceImpl#createFromPlan` 已生成文件，但 `sts_archive_training`、`sts_archive_version` 均没有保存文件 ID，`getByPlan/openPlanFile` 无法回读，故当前下载仍会返回“档案文件尚未生成”。
- 前后端/数据影响：新增 `sts_archive_version.file_info_id`，同步 Flyway migration、init schema、`ArchiveVersion` Entity 和归档服务回读；不新增接口、权限、菜单或前端字段。
- 自动化验收：`ArchiveServiceImplTest` 3/3 通过，覆盖当前归档版本文件 ID 回读；`ArchiveServiceIT` **5/5 通过**
  （`-P integration-test` + `TESTCONTAINERS_RYUK_DISABLED=true`），含 Flyway 后文件 ID 回读断言。
- 已验收（2026-09-24，真实环境）：结果完整 ENDED 计划（plan 2）经真实对象存储生成并下载到 PDF 正本
  （`%PDF-1.6`、198562 字节）；结果不完整返 409/9204 且无脏残留；管理员真实浏览器完成档案页与文件下载验收。
  证据 `engineering/qa/2026-09-24-close-training-archive-loop.md`。

## Task 3：质量与状态同步

- Owner：后端/前端。
- 是否完成：是（2026-09-24）。定向单测、Flyway/归档 IT、进度表与 Tasks 状态均已同步；双轴 Review 与真实管理员
  验收证据复用档案闭环记录 `engineering/qa/2026-09-24-close-training-archive-loop.md`（按 2026-09-24 决定，
  本 Change 不单独再建 QA 文档）。本 Change 的 spec delta（`m4-minimum-closure` MODIFIED 2 条）**早已并入基线**，
  归档时用 `--skip-specs` 避免仅措辞换行的无意义改写。
- 输入：Task 1～2 自动化与真实验收结果。
- 输出：定向单测、Flyway/归档 IT、QA、Retro、进度表和 Tasks 状态。
- 验收与测试：双轴 Review 已完成；自动化与真实管理员验收分开记录（见档案闭环 QA）；早前记录的环境阻塞已解除并作废。
