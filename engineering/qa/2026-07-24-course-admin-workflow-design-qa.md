# 课程模块交互优化视觉验收

## 验收对象

- source visual truth：
  - `C:\Users\careers\AppData\Local\Temp\codex-clipboard-c2b8b6ce-61bc-47a6-bb47-5df9b04f270b.png`
  - `C:\Users\careers\AppData\Local\Temp\codex-clipboard-ce7143e6-f47e-4c20-a038-6869c1c1659b.png`
  - `D:\xwechat_files\wxid_h2a3gsq6zn1p21_9b5d\msg\file\2026-07\详情编辑.html`（仅参考源码布局；本地文件运行态受浏览器安全策略限制）
- implementation screenshots：
  - `engineering/qa/evidence/course-workflow-2026-07-24/content-workbench-1440.png`
  - `engineering/qa/evidence/course-workflow-2026-07-24/content-workbench-1100.png`
  - `engineering/qa/evidence/course-workflow-2026-07-24/asset-picker-1440.png`
  - `engineering/qa/evidence/course-workflow-2026-07-24/unified-upload-fixed-1440.png`
  - `engineering/qa/evidence/course-workflow-2026-07-24/publish-panel-1440.png`
  - `engineering/qa/evidence/course-workflow-2026-07-24/offering-panel-1440.png`
- combined comparison evidence：
  - `engineering/qa/evidence/course-workflow-2026-07-24/asset-picker-comparison.png`
  - `engineering/qa/evidence/course-workflow-2026-07-24/unified-upload-comparison.png`

## 视口与归一化

- 桌面主视口：1440 × 1000 CSS px，deviceScaleFactor 1。
- 较窄桌面视口：1100 × 900 CSS px，实际内容宽 1085 px，无水平溢出。
- 来源附件像素：1920 × 953。
- 素材选择器和上传组件实现截图：1440 × 1000。
- 合并比较图：两侧均等比缩放到 900 px 高后横向拼接；仅比较对话框内容区域，不比较来源后台壳层。
- 课程内容工作台全页截图：1425 × 1302；较窄桌面截图：1085 × 888。

## 状态与交互

- 内容编排：已有章节、已有素材内容项、右侧材料规则展开。
- 发布版本：草稿、发布前检查完成、无历史版本。
- 开课配置：课程未发布，新增按钮禁用并展示原因，已有 Mock 开课列表。
- 素材选择器：多选、空选择、内嵌预览区域。
- 文件上传：多选、图片上传完成、进度成功、图片 Object URL 预览。
- 主要交互已检查：阶段切换、1100 px 自适应、文件选择、上传状态更新、图片预览。
- 控制台：验收入口未出现课程组件相关 error；应用已有的动态菜单路由 warning 不计入本次课程组件问题。

## Full-view comparison evidence

- 课程工作台使用顶部四阶段导航和三栏内容区，工作上下文连续，未出现业务弹窗嵌套。
- 1440 px 下章节、内容项和检查器比例清晰；1100 px 下没有横向溢出，固定操作区未遮挡表单。
- 发布和开课阶段延续相同容器、标题、边框与操作密度，阶段间视觉语言一致。

## Focused region comparison evidence

- 素材选择器对比图显示：实现保留了居中大弹窗、左侧筛选、顶部搜索、主体列表、底部确认操作；按本系统 Contract 将分类树改为素材类型，并新增右侧内嵌预览，属于确认方案中的有意差异。
- 上传组件对比图显示：实现保留了拖拽区、文件队列和固定底部操作；新增右侧预览区域并使用系统蓝色 Token，属于确认方案中的有意差异。
- 上传完成态单独检查了文件名、大小、成功进度、移除入口和图片预览，重要细节在全图中可读，因此无需额外裁切图。

## Required fidelity surfaces

- 字体与层级：复用项目字体 Token 和 Element Plus 字重；页面标题、分段标题、辅助说明和表单文字层级清晰，无异常换行。
- 间距与布局：工作台、三栏区、弹窗和抽屉遵循现有卡片间距、圆角和边框；1100 px 检查无横向溢出。
- 颜色与 Token：沿用系统主蓝、浅蓝选中态、语义成功/警告/危险色；未复制参考站点红色品牌色。
- 图片与资源：参考图没有要求复用品牌 Logo 或课程封面；上传预览使用真实选中文件，图标使用项目既有 Element Plus 图标。
- 文案：所有新增操作、禁用原因、错误降级和预览能力均使用独立可理解的中文文案。
- 可访问性：阶段导航和类型筛选使用原生按钮；上传区可聚焦；预览图有 alt；图标关闭按钮由 Element Plus 提供可访问名称。

## Findings

- P0：无。
- P1：无。
- P2：无。
- P3：素材选择器在独立视觉验收入口中为空数据状态，真实菜单接入后应再用生产等价 Mock 数据复核列表密度；不影响布局与组件能力验收。

## Comparison history

### Iteration 1

- 发现：上传进度达到 100% 后，界面仍可能显示“上传中”。
- 原因：入队前创建的普通对象在进入 Vue 响应式数组后仍被直接修改，最终状态没有触发渲染。
- 修复：入队后重新取得响应式队列项，再执行上传和状态更新。

### Iteration 2

- 证据：`unified-upload-fixed-1440.png` 显示绿色成功进度、成功图标、移除入口和图片预览。
- 结果：此前 P1 行为问题已消除；未发现新的 P0/P1/P2 问题。

## 最终结果

final result: passed
