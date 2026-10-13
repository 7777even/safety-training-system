# 系统管理初始化（sts-service-admin-init）

> 拓荒与运行态隔离：初始化走独立 CLI，不在 `sts-service-admin` 暴露 HTTP 初始化接口。

## 定位

| 维度 | sts-service-admin          | sts-service-admin-init              |
|----|----------------------------|-------------------------------------|
| 形态 | 长期在线 Web 服务                | 一次性 CLI / Job，跑完退出                  |
| 网络 | 对外 API                     | `web-application-type: none`，无 HTTP |
| 职责 | 日常系统管理、启动时执行 Flyway `V` 增量 | 首次执行模块化完整 SQL、登记 baseline 并写种子      |

首次部署只运行一次初始化 CLI。CLI 按固定模块顺序执行
`backend/sts-service-admin-init/src/main/resources/schema/schema-*.sql`，这些文件只包含最新最终结构且不得出现
`ALTER TABLE`；随后以 `sts.init.schema-version` 调用 Flyway `baseline` 建立 `flyway_schema_history`
，写入角色、菜单、用户、字典和配置种子后退出。之后启动 `sts-service-admin`，由其 Flyway 只执行 baseline 版本之后尚未应用的 `V`
。禁止 init 与 Admin 并发启动。

## 密码处理

遵循 [password-security.md](../architecture/password-security.md)：

1. 明文 → SHA-256（模拟前端传输）
2. 再 BCrypt（工作因子 10）写入 `us_sys_auth_identity.credential`

禁止在 SQL 中硬编码明文或弱哈希。

## 种子数据文件

位于 `backend/sts-service-admin-init/src/main/resources/data/`：

| 文件 | 内容 |
|------|------|
| `roles.json` | 默认角色 |
| `users.json` | 超管 / 管理员账号 |
| `menus.json` | 菜单树与权限点 |
| `dicts.json` | 字典类型与项 |
| `configs.json` | 系统参数 |

根部门名称可由配置 `sts.init.root-dept-name` 指定。

## 运行

```bash
cd backend
mvn -s .mvn/settings.xml -pl sts-service-admin-init -am spring-boot:run
```

需可用的空 MySQL 数据库；`sts.init.enabled=false` 会禁用完整建库、baseline 登记和种子写入。
