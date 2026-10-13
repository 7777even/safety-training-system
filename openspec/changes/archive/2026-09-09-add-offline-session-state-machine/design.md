# Design：线下场次受控状态机

## Context

`sessionStatus` 已有字段。生成写 `SCHEDULED`，PUT 可任意覆盖。教师任务仅在 `SCHEDULED`/`IN_PROGRESS` 给出 `RECORD_SCORE`
。最小子集已去掉签到，录分是唯一教师写操作。

## Goals / Non-Goals

**Goals:**

- 状态只能由 `start` / `complete` / `cancel` 推进。
- 录分仅 `IN_PROGRESS`。
- 教师或培训管理员可推进本人/任意场次。

**Non-Goals:**

- 按时间自动开始/结束。
- 全员录分后自动 `COMPLETED`。
- 签到窗口、计划取消连带取消场次。

## Decisions

### D1：状态与转移

```text
DRAFT ──(生成/不经此动作)──► SCHEDULED
SCHEDULED ──start──► IN_PROGRESS ──complete──► COMPLETED
SCHEDULED|IN_PROGRESS ──cancel──► CANCELLED
```

手工创建固定 `DRAFT`。`DRAFT` 不可 start/complete/cancel，可删改元数据。

非法转移 409 / `8202`。版本冲突 409 / `8204`。场次不存在 404 / `8201`。

### D2：接口

`POST /training/offline-sessions/{id}/start|complete|cancel`  
body：`TrainingPublishRequest.version`。  
权限：`training:plan:edit` **或** `training:teacher:task`。  
服务端：有 `training:plan:edit` 可操作任意场次；否则必须是本场教师，否则 403 / `8131`。

PUT 忽略且契约删除 `sessionStatus`。创建忽略请求状态，写 `DRAFT`。

### D3：录分与教师动作

`POST /training/offline-scores` 仅 `IN_PROGRESS`，否则 409 / `8203`。

教师详情 `availableActions`：

| 状态          | 动作                                   |
|-------------|--------------------------------------|
| SCHEDULED   | `START`, `CANCEL`                    |
| IN_PROGRESS | `RECORD_SCORE`, `COMPLETE`, `CANCEL` |
| 其它          | 空                                    |

### D4：计划结束

既有 `IN_PROGRESS_SESSION` 门禁继续：场次 `IN_PROGRESS` 时计划不可 end。`COMPLETED` / `CANCELLED` / `SCHEDULED` 不因此项阻断。

## Risks

- 生成后未 start 则无法录分，前端必须调 start。契约写清。
- OpenAPI 曾写 `RUNNING`/`ENDED`，与代码 `IN_PROGRESS`/`COMPLETED` 对齐。
