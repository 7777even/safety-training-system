# 补漏 B 批最小复用设计（骨架，待确认）

T1（20/66 素材批量）：复用 `system-admin.openapi.json` 的分片上传与"批量删除文件"；前端素材库列表支持多选（multiple 上传、勾选批量下载/删除）。下载复用现有文件下载端点；删除复用批量删除并校验转码中状态保护（转码中不可下载/编辑/制作）。

T2（70/81 试题导入）：扩展 `question:import`，新增 Word 模板解析；导入批次内做重复题/答案唯一性校验与行级错误定位（复用现有 `QuestionImportRowRequest` 与错误码外壳）。

T3（82 题库批量管理）：复用题库查询；新增正确率统计（基于作答事实）、批量审核/导出/删除端点，删除前校验引用保护。

T4（83 试题回收站）：题目表增加软删标记与删除时间；新增回收站查询（搜索/筛选）、批量还原；还原时校验引用冲突。

T5（13 题目导出）：复用导出能力，按题库/筛选导出题目 Excel；公共/内部范围不对齐。

API：扩展 `asset`/`question` openapi；新增最小端点与 `x-permission`、错误码。

数据库：题目软删字段、必要统计视图；同步 init/new migration/schema-version/Entity/IT。不改旧 migration，不操作业务库，不引入依赖。

UI：沿用 pc-web 素材库与题库管理组件；多选、回收站页、批量操作条。中文状态/校验。

验证：Red→Green 单测 + SQL/事务 IT + 两端行为测试/typecheck/定向 lint + 双轴 Review + QA/Retro。
