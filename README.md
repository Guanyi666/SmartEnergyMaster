# SmartEnergyMaster
这是一个基于 Spring Boot + Vue 3 的智能能源管理平台。本项目支持使用 Docker 进行一键式快速部署。

1. 克隆代码
Bash
git clone https://github.com/Guanyi666/SmartEnergyMaster.git
cd SmartEnergyMaster
2. Docker 一键运行 (推荐)
本项目包含完善的 Docker 配置，通过 docker-compose 可以自动拉起数据库服务：

Bash
# 进入部署目录
cd deploy

# 启动数据库容器 (TimescaleDB)
docker-compose up -d
注意：在启动前，请确保你的 deploy/init-sql 目录下已经存放了数据库初始化脚本，容器启动时会自动执行该目录下的 .sql 文件。

3. 后端运行
你可以选择 Docker 部署或本地运行：

Docker 方式：在 backend/ 目录下构建镜像并运行：

Bash
# 构建后端 jar
mvn clean package -DskipTests
# 使用你编写的 Dockerfile 构建镜像
docker build -t energy-backend -f ../deploy/Dockerfile-backend .
# 启动容器
docker run -d -p 8080:8080 --name energy-api energy-backend
本地方式：直接运行 mvn spring-boot:run。

4. 前端运行
进入 frontend/ 目录：

Bash
cd ../frontend
# 安装依赖
npm install
# 启动开发服务器
npm run dev
项目结构概览
Plaintext
SmartEnergyMaster/
├── ai_models/    # AI 算法模型
├── backend/      # Spring Boot 后端
├── data/         # 数据库脚本与处理逻辑
├── deploy/       # Docker 配置文件 (docker-compose.yml, Dockerfile-backend)
├── docs/         # 项目说明文档
└── frontend/     # Vue 3 前端
