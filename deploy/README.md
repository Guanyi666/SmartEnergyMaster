# Docker 部署指南

## 1. 环境准备

安装 Docker Desktop，或在 macOS 使用 Colima。确认下面命令可执行：

```bash
docker version
docker compose version
```

所有部署命令都在项目根目录执行。

## 2. 配置环境变量

默认配置可直接运行。生产或共享环境建议先创建配置文件：

```bash
cp deploy/.env.example deploy/.env
```

至少修改 `DB_PASSWORD` 和 `JWT_SECRET`。Compose 会自动读取 `deploy/.env`。

## 3. 启动项目

推荐启动基础服务和数据模拟器：

```bash
docker compose -f deploy/docker-compose.yml --profile simulator up -d --build
```

启动完成后访问：

- 前端：http://localhost:5173
- 后端 Swagger：http://localhost:8080/swagger-ui.html
- 默认管理员：`admin / admin123`
- 默认维修工程师：`E001 / 123456`

数据模拟器会先回填 24 小时历史数据，再每 3 秒上传实时数据。

仅启动基础服务：

```bash
docker compose -f deploy/docker-compose.yml up -d --build
```

## 4. AI 预测服务

AI 容器需要较大的 Python/PyTorch 镜像：

```bash
docker compose -f deploy/docker-compose.yml --profile simulator --profile ai up -d --build
```

仓库默认不包含 `ai_models/artifacts/*.pt` 和 `*.pkl` 模型文件。缺少模型权重时，
预测服务 `/health` 可用，但预测接口会降级；不影响其他页面运行。

## 5. 查看状态和日志

```bash
docker compose -f deploy/docker-compose.yml ps
docker compose -f deploy/docker-compose.yml logs -f backend
docker compose -f deploy/docker-compose.yml logs -f frontend
docker compose -f deploy/docker-compose.yml logs -f data-pump
```

验证接口：

```bash
curl http://localhost:8080/api/sensor/latest/EAF-01
curl http://localhost:5173
```

## 6. 停止和重启

停止容器，保留数据库数据：

```bash
docker compose -f deploy/docker-compose.yml --profile simulator down
```

重新启动：

```bash
docker compose -f deploy/docker-compose.yml --profile simulator up -d
```

删除容器及数据库数据，重新执行全部初始化 SQL：

```bash
docker compose -f deploy/docker-compose.yml --profile simulator down -v
docker compose -f deploy/docker-compose.yml --profile simulator up -d --build
```

`down -v` 会永久删除现有数据库数据，仅应在需要全新初始化时使用。

## 7. 常见问题

端口被占用时，在 `deploy/.env` 修改 `FRONTEND_PORT`、`BACKEND_PORT`、`DB_PORT` 或
`REDIS_PORT`，然后重新启动。

数据库表结构更新后，已有数据库卷不会自动重新执行初始化 SQL。开发环境可使用
`down -v` 重建；需要保留数据时，应手动执行对应迁移 SQL。

后端构建首次需要下载 Maven 依赖，前端构建首次需要下载 npm 依赖，AI profile
首次需要下载 PyTorch，因此第一次构建耗时较长。
