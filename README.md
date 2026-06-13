# SmartEnergyMaster ⚡

SmartEnergyMaster 是一个基于 **Spring Boot + Vue 3 + TimescaleDB** 构建的智能能源管理平台，支持 Docker 一键部署，也支持本地开发模式运行。

---

## 📌 1. 项目技术栈

- 后端：Spring Boot + Maven
- 前端：Vue 3 + Vite
- 数据库：TimescaleDB（基于 PostgreSQL）
- 容器化：Docker + Docker Compose
- 可选：AI 模型模块（Python / 其他）

---

## 📁 2. 项目结构

SmartEnergyMaster/
├── ai_models/     # AI 预测/分析模型
├── backend/       # Spring Boot 后端服务
├── frontend/      # Vue 3 前端项目
├── deploy/        # Docker 与部署配置
│   ├── docker-compose.yml
│   ├── Dockerfile-backend
│   └── init-sql/  # 数据库初始化 SQL
├── data/          # 数据处理与脚本
└── docs/          # 项目文档

---

## 🧰 3. 环境要求

在运行前请确保已安装：

- Docker & Docker Compose
- Node.js ≥ 16
- Java ≥ 17
- Maven ≥ 3.6

---

## 🚀 4. 快速启动（推荐 Docker 方式）

### 4.1 克隆项目

```bash
git clone https://github.com/Guanyi666/SmartEnergyMaster.git
cd SmartEnergyMaster
```

4.2 使用 Docker Compose 启动完整项目
```bash
docker compose -f deploy/docker-compose.yml --profile simulator up -d --build
```

首次启动会构建并运行 TimescaleDB、Redis、Spring Boot 后端、Vue/Nginx 前端和数据模拟器。

- 前端：http://localhost:5173
- 后端：http://localhost:8080
- Swagger：http://localhost:8080/swagger-ui.html

完整配置、AI 预测服务和排错方式见 [deploy/README.md](deploy/README.md)。

⚙️ 5. 配置说明（重要）
后端数据库配置（application.yml）
```ymal
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/your_db
    username: your_username
    password: your_password

```
前端接口地址（.env）

```env
VITE_API_BASE_URL=http://localhost:8080
```

🧪 6. 常见问题（FAQ）
❓ Docker 启动失败
```bash
docker ps
docker logs <container_id>
```

❓ 数据库连不上
检查 TimescaleDB 是否启动
检查 5432 端口是否占用
检查 application.yml 配置

❓ 前端无法访问后端
后端是否运行在 8080
是否配置 CORS
API 地址是否正确

📌 7. 启动顺序（推荐）
启动数据库（Docker）
启动后端
启动前端

📈 8. 可扩展方向
AI 能耗预测
实时数据流处理
Grafana 可视化监控
多租户能源系统

🔧 9. Git 协作规范（团队开发）
9.1 分支规范
main：生产稳定分支（禁止直接提交）
develop：开发主分支
feature/xxx：功能开发
fix/xxx：bug 修复
hotfix/xxx：紧急修复

9.2 开发流程

```bash
git checkout develop
git pull origin develop

git checkout -b feature/your-feature

git add .
git commit -m "feat: add new feature"

git push origin feature/your-feature

```
9.3 Commit 规范
feat：新功能
fix：修复 bug
docs：文档
style：格式调整
refactor：重构
chore：构建/工具

示例：
```bash
feat(backend): add energy API
fix(frontend): fix chart bug
docs(readme): update guide
```

9.4 常用 Git 命令

```bash
git status
git pull origin develop
git checkout -b feature/name
git commit -m "message"
git push origin feature/name
git branch -a
git branch -d feature/name
git reset --hard HEAD~1
```

9.5 注意事项
禁止直接 push main
必须通过 PR 合并
提交信息必须规范
不允许强推公共分支
