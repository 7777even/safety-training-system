# 补漏 A 批：导入导出基础

## 状态与授权
- 状态：提案已定稿，待人工确认后实施。
- 覆盖编号：11（用户 Excel 导入导出）、16（考试名单导入）、38（培训人员导入/导出/扫码加入）。
- 授权：用户已确认按推荐从 A 批开始（2026-10-13）。
- 技术决策：**不引入 POI/EasyExcel 等新依赖**；导入导出统一采用 CSV（UTF-8 BOM，Excel 可直接打开/编辑），复用现有 `Utf8CsvExporter`；导入端点直接接收 `MultipartFile`，不经素材库。如后续确需二进制 xlsx 导入，再单独确认引入 EasyExcel。
- 本批只补"单/多组织 Excel(CSV) 导入导出、文件导入考生名单、培训人员批量导入/导出"的差异；已有 CRUD、模糊查询、选择式添加直接复用。原文未指定导入格式，CSV 为最小可行且 Excel 兼容。

## 原文覆盖
### 11．用户 Excel 导入导出
包括用户信息的添加、删除及编辑功能；支持根据用户名、姓名等属性进行模糊查询，且支持按单组织/多组织两种方式通过 Excel 批量导入/导出用户账号。

### 16．考试名单导入
考试管理模块能够实现管理员在线安排考试的功能。管理员安排考试时可以设置参与考试的人员，包括直接分配给班级或导入考试学员名单两种方式；设置阅卷人；设置考试有效时间段、有效时长、有效次数等考试信息；

### 38．培训人员导入、扫码加入与导出
支持查看已添加至该培训的人员，支持添加新成员，支持单个添加人员、批量导入人员和学员扫码注册账号加入培训三种添加人员方式，支持将培训名单导出至本地。

## 已确认最小范围
- 11：在 `SysUserController` 新增 `export`（权限 `sys:user:export`）与 `import`（权限 `sys:user:import`）；导出按当前组织筛选（单/多组织）生成 CSV；导入逐行校验组织归属、必填、登录名/手机号重复，错误行可定位且不回滚已写入行。前端 `user/index.vue` 增加导入/导出按钮。
- 16：在 `ExamController` 新增 `POST /exams/{examId}/candidates/import`（权限 `exam:exam:import`），读取 CSV 展开为 candidate，复用现有考生写入校验；与 `bind-selection` 等价。导出复用现有 `statistics/export`（xlsx，已具备）。前端 `ExamAssignmentPanel` 增加"导入名单"入口。
- 38：在 `TrainingPlanController` 新增 `POST /plans/{id}/participants/import`（权限 `training:participant:import`，`joinSource=IMPORT`）与 `GET .../participants/export`（权限 `training:participant:export`）；复用现有 `bind-selection`/`assign` 写入；导出复用 CSV。扫码加入（`joinSource=QR`）列为本批**延期子能力**（需二维码生成与邀请令牌链路，独立确认），本期不实现，不影响 38 导入/导出勾选。
- 权限新增：`sys:user:import`、`sys:user:export`、`exam:exam:import`、`training:participant:import`、`training:participant:export`；按种子口径补菜单/权限点并同步 `sts.init.schema-version` 与 Flyway 基线。
- 不新建独立账号体系、不新建权限模型、不新定业务数量上限。
