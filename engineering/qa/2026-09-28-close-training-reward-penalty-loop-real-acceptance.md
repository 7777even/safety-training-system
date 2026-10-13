# 培训计划奖惩闭环真实验收（Task 3）

关联 Change：`2026-09-23-close-training-reward-penalty-loop`。记录时间：2026-09-28。
承接 `engineering/qa/2026-09-23-close-training-reward-penalty-loop.md` 的「待验与阻断」一节。

## 验收环境（真实管理员登录态）

- 后端：本地 `sts-service-admin`（当前工作树代码，`spring-boot:run -Dspring-boot.run.profiles=vpn`，端口 20080），
  数据源为团队远程库 `19.191.192.146:8045/sts`、Redis `19.191.192.146:16379`（探测三端口全通）。
- 账号：`DEMO/admin`（user 2，示例企业 org 2，INTERNAL），经 `POST /api/auth/login`
  （`identityType=local`、`password` 为 SHA-256 摘要）取得真实令牌，后续请求均携带 `Bearer`。
- 目标数据：进行中计划 `plan 5「092301-培训计划」`（participant 8 / user 3）与
  已结束计划 `plan 4「123」`（participant 7）。
- 本轮**未做**任何代码改动；所有结论均来自当前工作树运行实例的真实 HTTP 往返与库内核对。

## 一、服务端写入校验与拒绝（Task 1 验收标准）

| # | 验收点 | 结果 | 事实证据 |
|---|---|---|---|
| 1 | 运行中计划可新增奖励 | 通过 | `POST /training/reward-penalties` → 200 / code 0 |
| 2 | 运行中计划可新增处罚 | 通过 | 200 / code 0 |
| 3 | `scoreChange=0` 为有效值 | 通过 | 200 / code 0（带说明时） |
| 4 | `status=0` 记录保留但不计入综合分 | 通过 | 200 / code 0，综合分不因该记录变化 |
| 5 | 结束计划写入被拒 | 通过 | `plan 4` 新增 → **409 / code 8242**「仅进行中的培训计划可录入奖惩」 |
| 6 | 参训人不属于该计划被拒 | 通过 | `plan 5` + `participant 6`（属 plan 3）→ **400 / code 8243** |
| 7 | 计划不存在/跨机构被拒 | 通过 | `trainingPlanId=99999` → **404 / code 8101** |
| 8 | 非法奖惩类型被拒 | 通过 | `recordType=BAD` → **400 / code 8245**「奖惩类型无效」 |
| 9 | 分值方向与类型不一致被拒 | 通过 | `REWARD` 配 `-2` → **400 / code 8245** |
| 10 | 引用不存在的佐证被拒 | 通过 | `evidenceFileId=99999999` → **400 / code 8246** |
| 11 | 陈旧版本写入被拒 | 通过 | 用旧 `version` 更新 → **409 / code 8244**「奖惩记录版本冲突」 |
| 12 | 校验失败无结果副作用 | 通过 | 失败前后该参训人综合分取值不变 |
| 13 | **奖惩写入提升计划版本（并发保护）** | 通过 | 写入前后 `sts_training_plan.version` **14 → 15** |

## 二、综合分自动刷新（数值级验证）

进行中计划的参训人三项来源分原本全为空，综合分公式 `课程×0.3 + 考试×0.3 + 线下×0.4 + 启用奖惩合计`
按要求三项齐全才出分，否则落 `INCOMPLETE`、`composite_score` 保持 null，因此无法在原始数据集上正向演示。
经人工确认后，**临时在共享库为 participant 8 填入来源分（课程 60 / 考试 70 / 线下 80，基础分 71）**，
逐项核对奖惩对综合分的影响，验收后已把三项改回 NULL 并把综合分还原为 NULL：

| # | 操作 | 期望综合分 | 实测 |
|---|---|---|---|
| 1 | 显式同步后的基线（71 + 既有奖励 `优异表现` +10） | 81 | 81 ✓ |
| 2 | 新增奖励 +5 | 86 | 86 ✓ |
| 3 | 新增处罚 -3 | 83 | 83 ✓ |
| 4 | 编辑该奖励 5 → 8 | 86 | 86 ✓ |
| 5 | 把该奖励置为 `status=0`（不计入） | 78（71 + 10 - 3） | 78 ✓ |
| 6 | 删除处罚记录 | 81 | 81 ✓ |
| 7 | 删除全部验收记录 | 81（仅剩既有 +10） | 81 ✓ |

结论：**奖惩分值按有符号语义实时进入综合分，新增/编辑/停用/删除四条路径均重算**。

## 三、佐证受控读取与可选字段（Task 2.1 验收标准）

| # | 验收点 | 结果 | 事实证据 |
|---|---|---|---|
| 1 | 有佐证的记录可受控读取 | 通过 | `GET /training/reward-penalties/1/evidence` → **200 / image/jpeg / 31205B** |
| 2 | 无佐证的记录不泄露内容 | 通过 | → **404 / code 8247**「奖惩佐证文件不存在或不可读取」 |
| 3 | 记录不存在 | 通过 | → **404 / code 8241**「奖惩记录不存在」 |
| 4 | 省略说明与发生日期可保存 | 通过 | 应用迁移后 → 200 / code 0 |
| 5 | 编辑清空说明可保存 | 通过 | 200 / code 0 |
| 6 | 编辑清空发生日期可保存 | 通过 | 200 / code 0；回读两字段均为 null |
| 7 | 逻辑删除约定 | 通过 | 删除后 `deleted` = 主键 ID（非 0/1），与仓约定一致 |

## 四、本次因共享库缺迁移而暴露的环境阻断（已按人工确认解决）

共享库 `flyway_schema_history` 原止于 `20260923130000`，缺本次 Change 依赖的两条迁移，
导致「说明/发生日期可省略或清空」在真实环境表现为 **HTTP 500**：

- 省略说明：`java.sql.SQLException: Field 'description' doesn't have a default value`
- 清空说明：`SQLIntegrityConstraintViolationException: Column 'description' cannot be null`

经人工确认后在共享库应用两条迁移（Flyway 记账，`Successfully applied 2 migrations ... now at version v20260924180000`）：

- `V20260924093000__allow_optional_training_reward_fields.sql`：纯放宽，`description`/`occurred_date` 改为可空，无数据损失。
- `V20260924180000__narrow_training_material_type.sql`：共享库内 `attachment_type='REWARD_PENALTY'` 行为 **0**，
  仅 1 行 `IMAGE`，故实际只改列注释，零数据影响（与迁移自身注释一致）。

应用后第二、三节相关用例全部转为通过。

## 五、未覆盖与遗留（如实登记）

1. **PC 界面真实浏览器取证未完成（环境阻断）**：本会话环境的无头 Chrome 无法工作——进程启动并创建
   profile 目录后立即退出，`--dump-dom`（含 `data:` URL）、`--remote-debugging-port`（9222 未监听）
   与沙箱外重跑均无任何输出，判定为环境限制而非产品问题。因此 Task 2 的「管理员界面操作」
   未取得浏览器证据；本轮以真实管理员登录态的 HTTP 往返替代。
   按该 Change 自定的「测试方式：…真实浏览器/Network…；环境未覆盖时不得标记闭环完成」，此项构成闭环缺口。
2. **成果/档案的奖惩事实回读未覆盖**：需「已结束计划上的奖惩记录」作样本，而奖惩只能写入运行中计划，
   库中不存在可结束计划前置的既有记录，故未构造。`plan 4`（已结束）无奖惩记录，其成果与档案的奖惩区为空，
   与库内事实一致但属弱证据。
3. **容器 MySQL IT 未能重跑**：本机无可用 Docker（`Could not find a valid Docker environment`），
   `TrainingRewardPenaltyServiceIT` 3 例全部以 `ExceptionInInitializer` 报错，无法取证。
   该 IT 中失败用例所断言的「奖惩写入提升计划版本」，已在本文件第一节第 13 条**真实环境实测通过（14→15）**，
   即产品行为在真实 MySQL 与生产配置下有效；IT 内的失败疑似切片上下文未生效，需在有 Docker 的环境复跑确认。
4. **真实并发时序未构造**：未在同一时刻并发执行「结束计划」与「奖惩写入」，仅以版本号提升间接证明保护生效。

## 六、验收过程中附带发现的既有缺陷（不在本 Change 范围，单独登记）

**`composite_score` 无法被同步写回 NULL。** `TrainingResultSyncServiceImpl.synchronizeParticipant(participant, contents)`
在 `applyAggregates` 里调用 `compositeScoreService.refresh(participant)`；当来源分不齐、规则要求齐全时，
`CompositeScoreServiceImpl.refresh` 会执行 `markIncomplete`（`compositeScore = null` + `resultStatus = INCOMPLETE`），
随后 `participantMapper.updateById(participant)` 落库。但 MyBatis-Plus 默认 `updateStrategy = NOT_NULL`
会跳过空字段，`TrainingParticipant.compositeScore` 又未声明 `@TableField(updateStrategy = ALWAYS)`，
因此 **`SET composite_score = NULL` 不会进入 SQL**。

实测复现（夹具还原阶段）：把三项来源分改回 NULL 后调用 `POST /training/plans/5/results/synchronize`，
**接口返回体里 `compositeScore` 已是 `null`，而库内该行仍是 `81.00`**；再次调用同步结果相同（行版本继续自增，
说明 UPDATE 确实执行、只是没写这一列），最终只能以直接 SQL 还原。

影响与边界：只影响「综合分由有值变为空」的方向；「由空变为有值」与「值变化」均正常（本文件第二节 7 条实测已证）。
判定为**既有缺陷**——`TrainingResultSyncServiceImpl` 与 `CompositeScoreServiceImpl` 均非本 Change 改动文件，
本轮未修，登记在此供后续独立收口；修复方向是给该字段补 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`。

## 七、结论

- Task 1（服务端归属/状态/冻结校验）与 Task 2.1（佐证受控预览、可选字段）的**全部验收标准已在真实
  管理员登录态下通过**，且综合分刷新取得数值级证据（第二节 7 条），并发保护取得计划版本提升证据（第一节第 13 条）。
- Task 2 的界面部分与「成果/档案奖惩回读」两项见第五节：前者因本机无头 Chrome 不可用未取得浏览器证据，
  后者缺「已结束计划上的奖惩记录」数据集样本，均未构造。
- **收口口径**：上述两处缺口已如实登记，经人工于 2026-09-28 签核，Task 1/2/2.1/3 全部标记完成，
  该 Change 归档至 `openspec/changes/archive/`；PC 界面真实操作仍可由人工随时补做，届时另开记录即可。
