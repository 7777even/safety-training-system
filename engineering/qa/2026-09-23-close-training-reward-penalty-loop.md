# 培训计划奖惩闭环 QA（进行中）

## 范围

执行中计划的奖惩新增、编辑、删除、停用、佐证及综合结果同步；结束后只读，成果和档案回读。依据已确认的 `2026-09-23-close-training-reward-penalty-loop` Change。

## 已验证

- 2026-09-24 Task 2.1：按奖惩记录 ID 受控读取佐证；列表及新增/编辑弹窗可预览，已保存文件从服务端读取，本次上传文件从本地读取。服务端对无记录/无佐证/文件不可读返回 404，租户隔离由现有 MyBatis 拦截器作用于奖惩与文件表。
- 2026-09-24 定向回归：`TrainingRewardPenaltyServiceImplTest` 7/7、`TrainingExecutionControllerTest` 5/5；PC typecheck、所改两文件 ESLint、OpenAPI JSON 解析、OpenSpec strict、`git diff --check` 通过。HTTP 用例覆盖 PDF 内联与 HTML 降级为八位流且禁止 MIME 猜测。
- 2026-09-24 用户确认按 B 保持奖惩说明与发生日期为可选；`V20260924093000__allow_optional_training_reward_fields.sql`、init schema 与基线版本已同步。真实 MySQL：旧库升级迁移 1/1、受新增迁移影响的既有升级用例 3/3、奖惩佐证归属与可选字段清空 2/2、Admin init 1/1 通过。测试进程临时设置 `TESTCONTAINERS_RYUK_DISABLED=true` 以避开 Ryuk 镜像拉取失败；未修改 Docker 配置。
- 后端 `TrainingRewardPenaltyServiceImplTest`、`TrainingExecutionControllerTest`、`TrainingWorkflowControllerTest`：14 个定向测试通过，覆盖新增接口载荷、计划/参训人校验、结束后拒绝、计划版本冲突及现有成果接口委托。此为零 DB/Mock 测试，不证明真实 SQL 与并发事务。
- PC `training-reward-penalty.test.mjs`：1/1 通过；`pnpm --filter pc-web typecheck`、受影响文件 ESLint 通过。此为静态/类型验证，不替代真实点击和 Network 核对。
- `openspec validate 2026-09-23-close-training-reward-penalty-loop --strict` 通过；`training.openapi.json` JSON 解析通过；`git diff --check` 无空白错误。

## 待验与阻断

Task 2.1 尚需在真实管理员登录态下，用对象存储中的图片、PDF 和 Office 佐证核对列表、编辑回显、新增上传后预览，以及结束计划只读预览；当前自动化不能替代该项验收。

- 可选字段与表约束冲突已按用户确认的 B 方案修复并通过上述 IT。完整 `TrainingRewardPenaltyServiceIT` 中原有 `rewardWriteBumpsPlanVersion_soStaleEndCannotFreeze` 仍失败：同一事务内旧计划对象更新返回 1，期望为 0；该计划乐观锁问题不属于佐证预览或两列可空迁移，本轮未改变计划并发逻辑。相关两条目标 IT 已单独通过，不把整类报告为通过。

用户已明确本次开发交付只以代码和自动化验证为范围；真实环境的管理员操作、成果及档案回读由用户在 Task 3 自行验收。MySQL IT 属于开发侧自动化验证，不计入用户手工验收。

- 早期测试源编译和归档版本列重复迁移阻断已不再出现；本轮受影响的数据库升级与佐证读取用例均已进入断言并通过。奖惩计划并发 IT 仍需单独诊断，不计入本次预览能力的通过项。
- 待在非生产环境以管理员登录：运行中计划新增奖励、处罚、0 分记录，编辑/停用/删除后核对列表和个人综合分；结束后确认按钮只读并直接调用写接口得到拒绝；核对已结束成果及一期/企业/个人档案的奖惩事实。
- 真实并发结束/奖惩写入、真实对象存储预览与归档下载尚未验收；预览 Retro 已补。因此当前不能宣称全链路验收完成。

## Task 2.1 双轴 Review

- 规格符合性：预览入口覆盖列表、新增上传后和编辑旧/新佐证；接口按奖惩记录与文件的当前机构归属读取，结束后仍可只读访问；`description`、`occurred_date` 可选口径已同步迁移、init 和测试。真实登录态与对象存储场景仍由 Task 3 验收。
- 代码质量：复核本次差异中的权限、文件名清理、MIME 白名单、请求切换竞态和弹窗关闭清理；定向测试、类型检查及差异检查通过。完整奖惩 IT 类的既有计划乐观锁失败单独记录，不归为预览通过项。
