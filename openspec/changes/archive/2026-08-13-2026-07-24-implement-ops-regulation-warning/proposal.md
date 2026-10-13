# Proposal：实现运营工作台 / 监管预警后端（M5）

## 背景

M0～M4 后端已形成课程、考试、培训执行、正式综合结果、档案与证书等稳定事实。M5 在其上建设运营工作台待办与统计投影，以及园区监管查询与预警规则/检测/处置闭环。DDL：
`sts_regulation_warning_*`（`06_*`）；OPS 以只读聚合 API 为主，不另建统计事实表。

## 目标

交付 lims 纯后端 M5：

1. OPS-01：企业/学员/教师工作台待办与任务摘要（读现有事实）。
2. OPS-02：课程/考试/培训/综合结果统计计数与概览。
3. OPS-03：统计列表筛选、下钻明细 ID 列表、导出任务最小 API（同步生成 JSON/CSV 占位文件元数据即可）。
4. REG-01：监管机构视图与被监管机构培训/预警汇总。
5. REG-02：预警规则与条件；检测作业幂等生成预警与证据（含 PLAN_HOURS 等最小指标）。
6. REG-03：预警查看、分派、评论、关闭/忽略/重开流水。
7. REG-04：OpenAPI、权限、进度表、QA 关闭 M5。

## 用户价值

管理员可见真实待办与统计；园区监管可按规则检测学时类预警并处置留痕。

## 功能范围

- 包：`com.sts.ops`、`com.sts.regulation`
- migration 预警五表 + schema IT
- OpenAPI：`ops.openapi.json`、`regulation.openapi.json`
- 零 DB + Mapper IT；menus；错误码 ops 9600+、regulation 9800+

## 非目标范围

- 任何 `frontend/`、页面、Mock、图表 UI
- 素材转码（M6）、消息推送渠道、复杂 OLAP/数仓
- 全量指标字典产品定稿（本期实现透明 metric_code：PLAN_TOTAL_HOURS、PLAN_ONLINE_RATIO 等最小集）

## 前后端影响

仅后端；前端以 OpenAPI 为准另开。
