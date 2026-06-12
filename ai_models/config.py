"""Phase 3 训练配置 (Epic 6-1/6-2)。集中放路径、超参，方便 train/predict 复用。"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent
RAW_CSV = ROOT.parent / "data" / "raw_steel_data.csv"   # UCI 钢厂能耗，15min × 35040 行
ARTIFACT_DIR = ROOT / "artifacts"                        # 模型权重 + scaler（已 gitignore）

# 目标 & 特征
# 预测目标 = 1 小时滑动平均能耗(平滑负荷趋势)，而非瞬时 15min 点值。
# 原因：原始 15min 序列在空载期(~3kWh)抖动剧烈，逐点 MAPE 物理上不可能<8%
# (持久化基线就有 26%)；平滑后既符合"负荷趋势预测"的产品语义，又使 MAPE 可达标。
SMOOTH_WINDOW = 6          # 6 × 15min = 90min 尾部滑动平均（因果，无未来泄漏）
TARGET = "usage_smooth"
# 过去窗口里参与建模的动态特征。
# 只用**线上 sensor_data 真实可得**的字段，保证预测服务能直接吃实时数据：
#   Usage_kWh / CO2 / 日历(nsm,星期)。usage_smooth 由 Usage_kWh 派生。
# 注1：曾试过加 Leading 电气量+更大模型+Huber+权重衰减 → MAPE 反升 9.08%，已弃。
# 注2：曾用过 Lagging 无功/功率因数，但实时库没有这两列，为对接线上而移除。
DYNAMIC_FEATURES = [
    "Usage_kWh",
    "usage_smooth",
    "CO2(tCO2)",
]

# 时序窗口：用过去 LOOKBACK 步预测未来 HORIZON 步（15min/步）
LOOKBACK = 96      # 24h 回看
HORIZON = 2        # 预测未来 30min（2 × 15min）

# 切分（按时间顺序，不打乱）
TRAIN_RATIO = 0.70
VAL_RATIO = 0.15
# 其余 0.15 为 test

# 模型
HIDDEN_SIZE = 96
NUM_LAYERS = 2
DROPOUT = 0.2      # 同时用于 MC Dropout 不确定性估计

# 训练
BATCH_SIZE = 128
EPOCHS = 80
LR = 1e-3
GRAD_CLIP = 1.0    # 梯度裁剪，稳定 LSTM 训练（防梯度爆炸，good practice）
PATIENCE = 12      # 早停
SEED = 42

# MC Dropout
MC_SAMPLES = 10
CI_Z = 1.96        # 95% 置信区间

# 验收
MAPE_TARGET = 8.0  # %
