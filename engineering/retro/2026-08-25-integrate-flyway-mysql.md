# init 完整建库与 Admin Flyway 增量复盘

## 做得好的地方

- 最终将首次安装和运行期升级分开：init 维护可读的模块化完整结构，Admin 只承担 `V` 增量。
- init 通过 Flyway API登记 baseline，使 Admin 无需自动接管未知非空库，也不必携带完整 SQL。
- MySQL IT 真实验证了 13 个模块建库、baseline、种子和 Admin 接管。

## 问题与原因

- 前两版把“完整 SQL”理解成 Flyway `B`，没有充分对齐用户要求的原模块拆分方式。
- init、Admin 和 Flyway history 三者交接没有一开始画清，导致设计来回调整。
- init 菜单种子存在与本次无关的既有一致性失败。

## 改进

- 数据库设计先明确三个独立问题：谁建空库、谁升级已有库、Flyway history 由谁建立。
- 后续每次变更固定同步：修改完整模块 SQL、新增 Admin `V`、更新 `sts.init.schema-version`。
- 已执行 `V` 保持不可变；完整模块 SQL始终只表达当前最终结构，禁止堆积 `ALTER`。
