# Design：前端封面受控访问适配

## 契约与职责

- 管理端培训类型详情、创建、更新使用 `coverFileId`；预览走 `GET /api/training/types/{trainingTypeId}/cover`。
- 学员课程、考试、培训列表只读取后端返回的 `coverUrl`。该值为 null 时直接展示占位，不发请求；非空时经现有请求层以 Blob 读取，保证携带登录态。
- 学员培训封面路径由 `trainingPlanId` 受控，不得用 `trainingTypeId` 拼管理端路径。
- `training/reference-resources` 的 EXAM 候选只消费服务端返回值，前端不追加来源筛选。

## 跨端处理

shared 模型以 OpenAPI 为准移除学员侧 `coverFileId`。PC 与移动端同步替换其 Mock 和加载条件；管理端课程、试卷、考试仍可保留各自编辑使用的 `coverFileId`，不属于学员响应模型。

## 失败体验

封面为 null 或受控读取失败时保留卡片占位，不阻断课程、考试、培训的进入操作，也不显示内部文件 ID。培训类型编辑页的既有封面读取失败同样仅显示未设置封面。
