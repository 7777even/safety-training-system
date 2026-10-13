# 第七批失败修复与提交前复验

日期：2026-10-12；执行：Codex。范围：第七批及当前工作区提交，排除 PC/H5 vite.config.ts 与两个后端 application.yml。状态：本次新增失败已修复，代码和定向数据库验证通过；真实界面验收仍由 WorkBuddy 完成，短信延期。

## 根因与最小修复
- 人员特征、矩阵、计划 IT 插入后回读数据库默认版本，矩阵旧版本对象独立复制，继续断言过期更新返回零。
- JDBC 旁路修改后清理测试 SqlSession 一级缓存，确保分类、动态成员和停用资格读取当前数据库事实；生产缓存策略未修改。
- 考试 IT 按 ExamService 接口注入；变更测试切片启用与应用默认一致的类代理，保留事务代理与回滚校验。
- 考试执行端口夹具补齐 requireById 回读，关联同一执行编号、考试编号及来源；人员展开、候选和评卷名单仍真实持久化。执行创建端口仍为 Mock，此 IT 不证明执行创建持久化。
- 考试旧静态断言改为已确认的统一 PersonSelectionDialog，继续验证评卷确认事件；既有行为测试覆盖资格失败、名单保护及单改评卷不重展考生。

## 最新验证结果
| 验证 | 结果 |
|---|---|
| 后端20个目标单测类 | 233项，零失败、零错误 |
| 7个业务MySQL IT | 17项，零失败、零错误 |
| 第七批Flyway升级不扩权 | 4项，零失败、零错误 |
| PC第七批/培训/消息定向 | 75项全部通过 |
| 人员选择旧断言目标回归 | 1项通过 |
| H5培训与考试须知 | 14项全部通过 |
| PC类型检查及改动生产文件lint | 通过 |
| OpenSpec strict / OpenAPI本地引用 / 菜单唯一性 / diff检查 | 通过；1210处引用、258个唯一菜单 |
| 两轴独立修复Review | 规格及质量均无阻断发现 |

最终数据库命令在 backend 执行：

~~~powershell
mvn -pl sts-service-admin -am test -Pintegration-test "-Dtest=PersonClassificationIT,PersonDispatchIT,TrainingMatrixIT,TrainingMatrixTransactionIT,TrainingPlanApprovalIT,TrainingPlanChangeIT,ExamBatch7DispatchIT,FlywayMigrationIT#shouldUpgradePersonClassificationWithoutGrantingExistingRoles+shouldUpgradeTrainingMatrixWithoutGrantingExistingRoles+shouldUpgradePlanApprovalWithoutGrantingExistingRoles+shouldUpgradePlanChangesWithoutGrantingExistingRoles" "-Dsurefire.failIfNoSpecifiedTests=false"
~~~

本机原始输出：.workbuddy/tmp/batch7-final-it-20261012.log、batch7-fix-pc-20261012.log、batch7-final-h5-20261012.log、batch7-final-exam-20261012.log（本地运行产物不提交）。

## 基线及交付边界
- 2026-10-11独立报告为当时结果，本记录覆盖其本次修复范围的最终结论；不抹去历史失败证据。
- exam-management + exam-workspace-navigation-and-paper-defaults 合计34项，当前27通过、7失败；用HEAD临时副本对照同样27通过、7失败，新增1项失败已消除。未宣称整个考试回归套件全通过，存量失败未扩大修复范围。
- MySQL分类、名单快照、乐观锁、审批行锁竞争、年度关联/版本回滚和变更事务已按目标IT验证；仍不等于真实浏览器、真机或短信送达验收。
- 按用户要求不提交运行配置：backend/sts-service-admin-init/src/main/resources/application.yml 的 schema-version 20261011130000 仍是本地改动；新库初始化前须显式同步该版本，已有库按migration升级，不重跑init。两个vite配置及Admin环境profile切换也不提交。
- 53/71/72/73仍保持开发完成待真实验收；85短信延期保持部分完成，主清单整项不关闭。未操作生产数据，未推送。
