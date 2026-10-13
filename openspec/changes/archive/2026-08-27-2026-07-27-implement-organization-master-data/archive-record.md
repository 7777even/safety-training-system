# 归档记录

- 归档日期：2026-08-27
- 归档原因：活跃 Change 清理。本 Change 未形成完整后端闭环，不能按“全部 Task 完成”归档；记录剩余工作后归档，避免继续占用活跃目录。
- 规格同步：未写入 `openspec/specs/`。后续多租户重建将机构语义收敛为 superAdmin 企业开户，本 Change 的园区/企业/培训机构四类型与
  ALL/本机构数据范围已过时，不得把未完成 delta 提升为主规格。

## 任务状态

| Task              | 状态              | 说明                                                                                                                                  |
|-------------------|-----------------|-------------------------------------------------------------------------------------------------------------------------------------|
| Task 1 机构登记与查询    | 进行中（2026-07-27） | 列表/详情/创建零 DB 已绿；当时 OrganizationMapperIT 因 Docker 不可用未形成通过证据。仓库现有 `OrganizationMapperIT`、`OrganizationOnboardingIT`，但本 Change 未回写完成。 |
| Task 2 维护、层级和状态保护 | 待确认             | 本 Change 未开工。当前 `OrganizationController` 已有 superAdmin 修改（含停用），语义来自后续租户开户，不是本 Task 的 ALL/本机构护栏。                                     |
| Task 3 验收与文档闭环    | 待确认             | 未执行。                                                                                                                                |

## 后续若重启

另开新 Change，以当前租户/开户实现和 `docs/api/org.openapi.json` 为准，不要直接复活本 proposal 的四类型机构登记模型。进度表
BASE-01A 保持进行中，直到新 Change 关闭。
