# Task 1 盘点：M4 范围基线与超范围入口

盘点日期：2026-09-20。协作身份：`lims`（仅后端 + OpenAPI；前端调用方列出但不实施）。

## 1. 保留入口（可追溯到中山 50）

| 入口                        | 功能项      | 判定                                |
|---------------------------|----------|-----------------------------------|
| 问卷 CRUD、换题、投放、绑定参与人、提交、统计 | 35、42    | 最小闭环；复制/双端预览/二维码/可见性由 Task 2 补齐   |
| 评价表单 CRUD 与发布             | 39、40、43 | 当前同时承载评分表和专家查课表；Task 3/4 再按语义拆分   |
| 专家查课提交、培训内查询              | 43       | 已有真实 API                          |
| 从计划归档 `create-from-plan`  | 44       | 内部仍写快照；个人/企业/期次查询与真实文件由 Task 5 补齐 |
| 证书模板 CRUD/发布、规则 CRUD、证书分页 | 19、50    | 自动签发、真实文件、本人查询由 Task 6 补齐         |

## 2. 本次删除的公开入口

| 入口                                               | 分类        | 后端调用方        | 前端调用方                                          |
|--------------------------------------------------|-----------|--------------|------------------------------------------------|
| `POST /survey/surveys/{id}/publish`              | 独立问卷版本管理  | 无（投放改为内部快照）  | 当前 `frontend/` 无直接 URL 调用                      |
| `POST /evaluation/records`、`POST .../submit`     | 通用人员/课程评价 | 仅 Controller | 无直接 URL；权限菜单挂在评价表单下                            |
| `POST /evaluation/calculation-runs/calculate`    | 人工计算批次    | 仅 Controller | `frontend/pc-web/src/api/evaluation-result.ts` |
| `POST /evaluation/calculation-runs/{id}/confirm` | 正式确认      | 仅 Controller | 同上                                             |
| `GET /evaluation/calculation-runs`               | 批次分页      | 仅 Controller | 同上                                             |
| `GET /evaluation/calculation-runs/{id}/sources`  | 批次来源追溯    | 仅 Controller | 同上                                             |
| `GET /archive/trainings/{id}/versions`           | 档案版本列表    | 仅 Controller | 无                                              |
| `GET /archive/versions/{id}`                     | 档案版本详情/哈希 | 仅 Controller | 无                                              |
| `GET/POST /archive/export-jobs`                  | 异步导出任务中心  | 仅 Controller | 无                                              |
| `POST /certificate/certificates/issue`           | 客户端任意手工签发 | 仅 Controller | 无                                              |
| `POST .../revoke`、`POST .../reissue`             | 撤销/重发     | 仅 Controller | 无                                              |

培训结束门禁「必须存在已确认正式计算批次」随计算批次入口一并删除；综合分完整性改由 Task 3 按来源事实判定。

## 3. SQL 归类

### 暂时隐藏（整表）

- `sts_evaluation_calculation_run`、`sts_evaluation_calculation_source`
- `sts_archive_export_job`
- `sts_certificate_revocation`
- `sts_survey_target_binding`、`sts_survey_answer_asset`、`sts_survey_sub_item`

### 暂时隐藏（列）

- `sts_training_participant.official_calculation_run_id`
- 证书有效期/撤销时间、规则条件值/领取说明/规则有效期
- 问卷匿名、高级题型字段、投放绑定 ID

### 内部保留

- 问卷/评价表单/证书模板/档案的快照版本表及哈希列
- 参训人 `composite_score`（Task 3 唯一读取口径）

不删表、列、索引和历史数据。

## 4. 权限与菜单

删除/隐藏：`evaluation:calculation:*`、`archive:export:*`、`certificate:cert:issue|revoke|reissue`、`survey:survey:publish`。

保留专家查课所需 `evaluation:record:add` / `submit` / `list`。

## 5. 后续 Task 缺口（不在 Task 1 实施）

- Task 2：复制、PC/手机预览、二维码、结果可见性
- Task 3：自动综合分、已结束培训结果页
- Task 4：查课表产品语义收敛
- Task 5：签到前置 + 三级档案真实文件（当前受签到缺失阻塞）
- Task 6：真实证书文件、自动签发、学员本人查询
- OpenAPI 历史欠账（问卷/证书部分 Controller 路径未完整登记）由对应 Task 补齐
