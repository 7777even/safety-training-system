# 补漏 C 批最小复用设计（骨架，待确认）

T1（63 综合管理）：复用 `asset.openapi.json` 资源库；新增公开范围标记、分类数量统计端点、权限分配、列表导出、按岗位分配引用、讲师字段；企业文库/问卷仅做入口聚合，不重做底层。

T2（80 题型富媒体）：题目表扩展题型枚举（名词/案例）；富文本编辑器支持公式/符号/表格（复用现有图文/音视频字段）；新增开放范围与 `joinPractice` 开关；双端渲染复用现有富媒体组件。

API：扩展 `asset`/`question` openapi；新增最小端点与 `x-permission`、错误码。

数据库：资源公开范围/讲师字段、题目题型枚举与开放范围/加入练习字段；同步 init/new migration/schema-version/Entity/IT。不改旧 migration，不操作业务库，不引入依赖。

UI：沿用 pc-web 资源库与题库编辑组件；富文本编辑器集成公式/表格；双端展示复用现有富媒体渲染。

验证：Red→Green 单测 + SQL/事务 IT + 两端行为测试/typecheck/定向 lint + 双轴 Review + QA/Retro。
