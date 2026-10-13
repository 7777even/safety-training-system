# Retro：M2 前端 Task 2 试卷组卷、预览与版本发布

- 做得好：组卷页不使用 Dialog/Drawer，固定、随机、自定义三种方式按试卷类型分流；发布前由页面状态强制完成预览。
- 问题：`currentVersionNo` 与版本详情参数名称不同。
- 结论：已按人工确认将 `currentVersionNo` 直接作为 `paperVersionId`；真实联调时优先验证该映射，若后端返回独立版本 ID，先更新
  OpenAPI 再调整调用方，不在页面推断。
