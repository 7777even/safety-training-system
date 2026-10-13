# 考试工作台收敛设计

## 页面与路由

- `/admin/business-center/exams/exam/create` 和 `/admin/business-center/exams/exam/:examId` 均加载 `exam/workspace/index.vue`。
- 工作台通过是否存在 `examId` 区分创建和详情模式。创建模式只能停留在考试信息；首次创建成功后替换为详情 URL，并进入用户选择的下一阶段。
- 删除 `ExamAssignment` 内部路由。列表“人员配置”入口跳转至详情 URL 并使用 `stage=assignment` 激活工作台 Tab。

## 组件边界

- `ExamDefinitionForm` 保持不变，继续承载考试信息字段。
- 原人员配置页面逻辑迁移为 `components/ExamAssignmentPanel.vue`，只由工作台的人员配置 Tab 使用。
- `components/ExamPublishCheck.vue` 展示考试信息、考生绑定数量和批阅人数量；工作台向其传入既有查询结果及发布动作。
- 工作台持有创建、加载、更新、发布和阶段切换；子组件不定义新路由或 API 字段。

## 发布核查

- 草稿考试发布 Tab 展示考试基本信息、已绑定考生和已配置批阅人。
- 考生或批阅人缺失时显示中文核查提示并禁用前端发布按钮；该提示不替代服务端规则。
- 已发布考试展示完成态，不能再次调用发布接口。

## 风险与取舍

- 取消人员配置直链将影响旧书签；本次保留新增和详情入口，只按已确认范围移除人员配置内部路由。
- 不新增跨组件状态容器；人员配置面板保存后通过事件通知工作台刷新核查数据，避免引入无必要的 Pinia 状态。
