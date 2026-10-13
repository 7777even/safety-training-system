# 补漏 A 批最小复用设计（已定稿）

## 技术基线
- 无 POI/EasyExcel；复用 `Utf8CsvExporter`（UTF-8 BOM CSV 写出）。导入端点在 Controller 用 `MultipartFile` 直接读取 CSV，按行解析（简单逗号分隔 + 引号转义）。
- 导出回传：小文件直接 `ResponseEntity<byte[]>`（同 `ExamStatisticsController.export`），`Content-Type: text/csv;charset=utf-8`，`Content-Disposition: attachment`。

## T1（11 用户导入导出）
- 导出：`GET /api/system/users/export?orgIds=...`，复用现有列表查询条件（用户名/姓名模糊、组织），权限 `sys:user:export`；写出列：登录名、姓名、手机号、组织、状态、角色(可读名)。多组织：orgIds 多个或当前租户全部。
- 导入：`POST /api/system/users/import`（multipart `file`），权限 `sys:user:import`；表头：登录名,姓名,手机号,组织编码/名称,状态。逐行：解析 → 校验组织归属（必须在当前租户且操作者可见）→ 校验登录名/手机号非空且不与存量冲突 → 密码默认初始值（如 `123456` 或租户默认）→ 批量落库；错误行收集（行号+原因），成功行照常写入，最终返回 `{success, failedRows:[{line,reason}]}`。重复加入幂等：已存在登录名则跳过或更新（取最小：跳过并计入失败"已存在"）。
- 复用：`SysUserServiceImpl` 现有 create 逻辑与组织校验；`Utf8CsvExporter`。

## T2（16 考试名单导入）
- `POST /api/exam/exams/{examId}/candidates/import`（multipart `file`），权限 `exam:exam:import`；表头：手机号/登录名/姓名。逐行解析 → 解析为人员 → 复用现有 candidate 写入（同 `bind-selection` 展开逻辑，单人模式）→ 校验考试编排范围与去重；错误行定位返回。重复 candidate 幂等跳过。
- 导出：复用现有 `statistics/export`（xlsx，已具备），不新增。

## T3（38 培训人员导入/导出，扫码延期）
- 导入：`POST /api/training/plans/{id}/participants/import`（multipart `file`），权限 `training:participant:import`，写入 `joinSource=IMPORT`；复用 `bind-selection` 人员解析与 `assign` 写入。
- 导出：`GET /api/training/plans/{id}/participants/export`，权限 `training:participant:export`；CSV 列：姓名、手机号、组织、加入方式、状态。
- 扫码加入（`joinSource=QR`）：**本期不实现**，列为 38 延期子能力；实体 `TrainingParticipant.joinSource` 枚举已预留，仅导入/选择写入，不影响本批勾选。

## API 与数据库
- 扩展 `system-admin.openapi.json`、`exam.openapi.json`、`training.openapi.json`：新增端点加 `summary/description`、字段中文 `description`、example、`x-permission`、错误码。
- 权限点：`sys:user:import/export`、`exam:exam:import`、`training:participant:import/export`；按种子 `menus.json` 口径补菜单/权限并同步 `sts.init.schema-version`、Flyway `INIT_SCHEMA_VERSION`、受影响用例期望值。不新建业务表、不改旧 migration、不操作业务库、不引入依赖。

## UI
- 沿用 pc-web 现有 `user/index.vue`、`ExamAssignmentPanel.vue`、`PlanExecutionPanel.vue`；新增导入（`<input type=file>` + 上传）与导出按钮，错误行以表格/弹窗展示。中文状态/校验。

## 验证
- 每 Task Red→Green：后端单测（导出内容、导入成功/重复/越权/格式非法/错误行隔离）+ 必要时 SQL/事务 IT + 前端行为测试/typecheck/定向 lint + 双轴 Review + QA/Retro。
- Docker 不可用须如实报告阻塞；真实验收留 WorkBuddy。
