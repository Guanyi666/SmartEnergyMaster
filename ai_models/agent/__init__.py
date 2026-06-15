"""
智驭能效 系统级智能体 (SmartEnergy AI Agent)

基于多Agent架构的工业能源管理智能系统。

架构：
  agent_service.py   — FastAPI 服务入口（端口 8001）
  orchestrator.py    — 主控协调器（意图识别+任务路由）
  chat_handler.py    — LLM对话处理（百炼 API + Tool Calling）
  tools.py           — 25+ Tool定义（REST API封装）
  data_fetcher.py    — 数据获取层（HTTP调用Spring Boot后端）
  memory.py          — 对话记忆管理
  config.py          — 配置管理（API Key持久化）
  sub_agents/        — 6个专业子Agent：
    ├── production_monitor.py    — 生产监控（L3）
    ├── fault_diagnosis.py       — 故障诊断（L1-L2）
    ├── maintenance_dispatch.py  — 维修调度（L2）
    ├── energy_optimization.py   — 能耗优化（L1）
    ├── management_decision.py   — 管理决策（L3/L1）
    └── knowledge_agent.py       — 知识管理（L3）

启动方式：
  python -m agent.agent_service          # 直接启动
  uvicorn agent.agent_service:app --host 0.0.0.0 --port 8001
"""
__version__ = "1.0.0"
__author__ = "SmartEnergyMaster Team"
