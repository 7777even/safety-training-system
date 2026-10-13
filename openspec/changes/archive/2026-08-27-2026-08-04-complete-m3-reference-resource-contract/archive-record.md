# 归档记录

- 归档日期：2026-08-27
- 归档原因：Task 1 查询已落地，Task 2～3 未在本 Change 关闭，不能按全部 Task 完成归档。
- 规格同步：未整体写入 `openspec/specs/`。查询接口与同机构隔离已由后续培训/隔离 Change 吸收；发布冻结未在本目录验收。

## 任务状态

| Task                   | 状态                                 | 说明                                                                                          |
|------------------------|------------------------------------|---------------------------------------------------------------------------------------------|
| Task 1 当前机构可引用资源统一查询   | 进行中（实现与零 DB 完成，当时 IT 因 Docker 不可用） | `TrainingReferenceResourceController` / Mapper / `TrainingReferenceResourceMapperIT` 已在仓库中。 |
| Task 2 模板和计划同机构写入与发布校验 | 待确认                                | 本 Change 未执行。进度表 TRAIN-02 记录后续隔离 Change 已做模板同机构校验，是否覆盖本 Task 的发布复核需另开 Change 核对。            |
| Task 3 契约与工程闭环         | 待确认                                | 未执行。                                                                                        |

## 后续若重启

只补“发布事务内复核并冻结版本”的缺口，不要重复实现已有 `GET /api/training/reference-resources`。
