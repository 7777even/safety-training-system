# 工作台 Panel 收敛设计

## 统一结构

根工作台保留标题、步骤导航、加载状态、数据加载、保存/发布动作和路由跳转；阶段内容均放入 `<main class="admin-detail-page__surface">` 中，并以 Panel 组件按当前阶段渲染。

## 考试工作台

- `ExamInformationPanel`：复用 `ExamDefinitionForm` 与已发布只读信息；向父组件发出保存和下一步操作。
- `ExamAssignmentPanel`：保留考生与批阅人配置及保存行为。
- `ExamPublishCheck`：保留发布核查和发布操作。

## 试卷工作台

- `PaperBasicPanel`：基础信息、封面上传入口和保存动作。
- `PaperComposePanel`：组卷配置、题目/规则编辑、预览与进入发布动作。
- `PaperPublishPanel`：预览结果、发布确认和已存在的预览入口。

## 组件通信

父组件通过 props 向 Panel 传入现有响应式状态及只读数据，通过 emits 接收既有动作。请求函数仍由工作台根组件调用；Panel 不改变接口或业务校验语义。
