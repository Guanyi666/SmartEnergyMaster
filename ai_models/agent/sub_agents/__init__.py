"""
子 Agent 集群 (Sub-Agent Cluster)

每个子 Agent 负责一个专业领域，由主控 Agent 统一调度。
当前为 Phase 1 框架搭建阶段，子 Agent 预留接口和基础能力，
后续 Phase 2-3 逐步丰富各 Agent 的专属逻辑。

子 Agent 清单：
  - ProductionMonitorAgent: 生产监控（L3 自主执行）
  - FaultDiagnosisAgent:   故障诊断（L1-L2 AI建议+辅助执行）
  - MaintenanceDispatchAgent: 维修调度（L2 辅助执行）
  - EnergyOptimizationAgent:  能耗优化（L1 AI建议）
  - ManagementDecisionAgent:  管理决策（L3/L1 报表+建议）
  - KnowledgeAgent:           知识管理（RAG驱动）
"""
from .production_monitor import ProductionMonitorAgent
from .fault_diagnosis import FaultDiagnosisAgent
from .maintenance_dispatch import MaintenanceDispatchAgent
from .energy_optimization import EnergyOptimizationAgent
from .management_decision import ManagementDecisionAgent
from .knowledge_agent import KnowledgeAgent

__all__ = [
    "ProductionMonitorAgent",
    "FaultDiagnosisAgent",
    "MaintenanceDispatchAgent",
    "EnergyOptimizationAgent",
    "ManagementDecisionAgent",
    "KnowledgeAgent",
]
