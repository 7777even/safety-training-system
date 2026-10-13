# 考试补考前后端协作说明

机器可读契约以 `docs/api/exam.openapi.json` 为准。本文只说明怎么读契约、页面怎么拆，不替代 OpenAPI。

前端实施、Mock、联调由前端协作方另开。

## 一句话

补考不是再排一场考试。创建/编辑考试时把补考策略写入考试详情；对某个考生点「补考」时，只调用补考接口开一次新作答。后端按策略拦截，前端不要自造时间、范围或成绩规则。

## 两处页面，两套接口

### 1. 考试编辑：配置补考

跟考试一起保存 `policy`，不要等到成绩发布后再单独创建考试。

| 字段                                  | 含义                                                                        |
|-------------------------------------|---------------------------------------------------------------------------|
| `isRetakeEnabled`                   | 0/1。关闭则不能开补考                                                              |
| `retakeScope`                       | `FAILED` 仅已正式发布且不及格；`FAILED_OR_ABSENT` 不及格或未参加；`ABSENT` 仅未参加。空则按 `FAILED` |
| `retakeStartTime` / `retakeEndTime` | 整场补考窗口，可空（空=不限制该端）。不是给单个考生再设考试时间                                          |
| `retakeMaxCount`                    | 补考次数上限，不含首考                                                               |
| `retakeScoreMode`                   | `HIGHEST` / `LATEST`。发布成绩时回写考生正式分，开补考时不用传                                 |
| `isRetakeRecordVisible`             | 学员能否看补考记录                                                                 |

正考时间仍是考试上的 `openStartTime` / `openEndTime` / `durationSeconds`。迟到禁考不适用于补考。补考单次作答时长沿用原考试
`durationSeconds`。

### 2. 成绩/考生列表：给某人开补考

```http
POST /api/exam/candidates/{examCandidateId}/retake?clientAttemptToken={token}
```

权限：`exam:attempt:start`。

只有两个参数：

- `examCandidateId`：路径，哪个考生
- `clientAttemptToken`：查询参数，必填。同一考生同一令牌重复请求返回原尝试

不要传考试时间、时长、试卷、范围、次数。这些都在考试策略里。

成功返回 `ExamAttemptVO`，等同一次新开考（`attemptType=RETAKE`），随后走既有作答、交卷、批阅、发布成绩。

普通开考 `POST /api/exam/attempts/start` 和培训 enter **不要**用 `attemptType` 声明首考或补考。服务端按已有答卷派生；已交卷再进入返回原卷。补考只走本接口。

## 按钮预判（以后端为准）

同时满足再展示「补考」：

1. 策略 `isRetakeEnabled=1`
2. 考试状态为 `PUBLISHED` / `RUNNING` / `ENDED`
3. 当前时间在补考窗口内（有开始不得早于开始，有结束不得晚于结束）
4. 考生状态命中范围：`FAILED` 须已发布且不及格；`ABSENT` 为未参加；及格、待批阅、待发布、作答中不要开新补考
5. 该考生非作废 `RETAKE` 次数尚未达到 `retakeMaxCount`

进行中的补考应进入原尝试继续作答。若误调且令牌相同，后端会恢复原尝试。

## 错误码

用 `Result.code` 与 `message` 提示，HTTP 仍为 4xx。

| code | 文案         | 含义           |
|------|------------|--------------|
| 7051 | 未启用补考      | 策略未开         |
| 7054 | 不在补考开放时间内  | 窗口外          |
| 7055 | 当前考生不在补考范围 | 未发布、已及格或范围不符 |
| 7052 | 已超过补考次数    | 次数用完         |
| 7056 | 考试状态不允许补考  | 草稿或已取消       |
| 7011 | 考试人员不存在    | 考生 ID 无效     |

## 不要做的事

1. 不要为补考再创建一场考试。
2. 管理端不要用 `POST /api/exam/attempts/start` 传 `attemptType=RETAKE` 当作开补考入口，应走 retake 接口。
3. 不要在补考接口传 `openStartTime` 或 `durationSeconds`。
4. 不要前端自己改正式分；正式分在发布成绩时按 `retakeScoreMode` 回写。
5. 一次「开补考」动作生成一个 `clientAttemptToken`，失败重试沿用，不要每次换新。
