# OnlyOffice Document Server 9.2

镜像：`onlyoffice/documentserver:9.2`，服务端口：`8090`。

启动前设置与后端一致的 JWT 密钥：

```bash
export ONLYOFFICE_JWT_SECRET='替换为至少16字节的强随机密钥'
docker compose up -d
docker compose ps
curl http://localhost:8090/healthcheck
```

健康检查返回 `true` 后，后端使用以下配置：

```bash
DOCUMENT_TRANSCODE_PROVIDER=onlyoffice
ONLYOFFICE_BASE_URL=http://localhost:8090
ONLYOFFICE_JWT_SECRET=与容器相同的密钥
ONLYOFFICE_ALLOWED_DOWNLOAD_HOSTS=localhost
```

Compose 已为 OnlyOffice 设置 `nofile=65536`、`nproc=65535`，避免宿主机默认文件描述符限制导致其 Node.js、
PostgreSQL 和 RabbitMQ 子进程启动失败。

当前 ARM 测试机因容器内 Node.js 无法创建线程，Compose 暂时启用了完整特权模式 `privileged: true`。该配置会显著降低
容器与宿主机之间的安全隔离，仅限受控测试环境；确认兼容性原因并升级 Docker/containerd 后，应移除该配置并重新验证。

`ONLYOFFICE_INTERNAL_SOURCE_BASE_URL` 必须填写 OnlyOffice 容器能够访问的后端地址，不能填写容器内指向自身的
`localhost`。例如后端运行在宿主机且 Docker 支持该域名时，可填写 `http://host.docker.internal:8080`；若后端也在
Docker 网络中，应填写后端容器的服务名和端口。

中文字体文件（`.ttf`/`.otf`）放入 `fonts/` 后，执行：

```bash
docker exec onlyoffice-ds /usr/bin/documentserver-generatefonts.sh
docker restart onlyoffice-ds
```

`fontconfig-local.conf` 已配置以下缺失字体替代规则：

- 微软雅黑、黑体 → `Noto Sans CJK SC`
- 宋体、仿宋、楷体 → `Noto Serif CJK SC`

检查字体和替代结果：

```bash
docker exec onlyoffice-ds fc-list | grep -E "Noto (Sans|Serif) CJK SC"
docker exec onlyoffice-ds fc-match "Microsoft YaHei"
docker exec onlyoffice-ds fc-match "SimSun"
```

预期后两条命令分别返回 Noto Sans 和 Noto Serif。每次修改字体文件或替代规则后，都需要重新生成字体索引并重启容器。

Compose 使用命名卷保存 OnlyOffice 数据、日志及内置 PostgreSQL 数据。停止服务使用
`docker compose down`；不要执行 `docker compose down -v`，除非确认需要删除持久化数据。
