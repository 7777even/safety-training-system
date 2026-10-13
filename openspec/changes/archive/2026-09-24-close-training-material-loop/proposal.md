# Proposal：补齐培训计划资料（影像/附件）上传与展示闭环

## 状态

- **待人工确认。** 2026-09-24 已由文祥确认业务口径：**影像资料不绑死线下培训，与奖惩、附件同级作为「培训资料」，挂培训计划并可关联阶段**。
- 本 Change 只做**培训资料本身**（上传、维护、受控预览）的闭环，**不含**档案事实组装、档案文件格式、签到口径与统计口径；后三者另开 Change。
- 功能项依据：`docs/product/功能项.csv` 中山 34「可设置关于培训的全部内容，包含培训基本信息设置、线上培训、线下培训，培训人员及附件资料上传等」、36「支持上传**培训过程中**产生的影像、培训附件、奖惩评价、综合评价等资料」；`docs/product/安全教育培训需求规格说明书.md` FR-ENT-TRN-003、FR-ENT-TRN-004。

## 现状与问题

- 后端 `TrainingAttachment` 表、Entity、Mapper、Service，以及 `/api/training/attachments`（查询、新增）和 `/api/training/attachments/{id}`（详情、编辑、删除）与 `training:attachment:*` 权限**均已存在**。
- PC 工作台**只能读取**：`getTrainingAttachmentPage`（`frontend/pc-web/src/api/training-process.ts:84`）是全前端唯一调用点，新增/编辑/删除**零调用**；`PlanProcessResultsPanel.vue` 的附件页签与 `attachmentTypeText` 被整体注释，管理员既看不到已上传资料，也无法在计划内上传。
- 附件**缺少受控文件读取端点**（对照奖惩已有 `GET /api/training/reward-penalties/{id}/evidence`），即使恢复页签也无法预览或下载影像与附件。
- `attachmentType` 的类型集合**契约与 DDL 列注释都已约定** `GENERAL/IMAGE/VIDEO/REWARD_PENALTY/EVALUATION`（`docs/api/training.openapi.json` 的 `attachmentType` 已声明 `enum`，`sts_training_attachment.attachment_type` 列注释同一套），**缺的是服务端校验**与前端入口。
- 后端写入未校验计划状态、阶段归属与文件可见性，与补齐前的奖惩问题同构。

## 目标与边界

1. 管理员在培训计划内维护培训资料：一般资料、影像（图片/视频）、奖惩佐证与评价佐证，并可**可选**关联到某个培训阶段。
2. 服务端在新增、编辑、删除与文件读取前校验计划状态、计划/机构归属与文件可见性；已结束计划只读。
3. 沿用已有表、端点与权限，复用统一文件上传组件；**不新增业务表、不引入影像转码、不引入媒体播放器或移动端流程**。
4. 不恢复签到（口径已另行确认为「教师录入线下成绩即视为签到」），不改动综合分、证书签发与归档语义。

## 实施顺序

- Task 1：后端附件文件受控读取端点、写入校验与 OpenAPI 同步。
- Task 2：PC 计划内培训资料区域与上传、编辑、删除、预览。
- Task 3：纵向验收、Review、QA、Retro 与状态同步。
