import time
import random
import requests
import pandas as pd
from datetime import datetime
from ucimlrepo import fetch_ucirepo

API_URL = "http://localhost:8080/api/sensor/upload"
DEVICE_CODE = "EAF-01"  # 对应我们数据库里的 1号电弧炉
SLEEP_INTERVAL = 3      # 发送间隔(秒)。演示时设为3秒，答辩时感觉快了可以改成5或10秒

# ==========================================
# 西安分时电价
# ==========================================
def get_price_tier(nsm, month=8):
    """
    根据西安市最新工商业电价政策划分区间
    nsm: 午夜起算秒数
    month: 当前月份 (默认 8 月，用于触发夏季尖峰演示)
    """
    # 将秒数转换为当前的小时数 (0-23)
    hour = nsm // 3600

    # 1. 优先判断【尖峰时段】(特定月份的极端高价)
    if month in [7, 8] and (19 <= hour < 21):
        return "CRITICAL_PEAK"  # 夏季尖峰
    if month in [1, 12] and (18 <= hour < 20):
        return "CRITICAL_PEAK"  # 冬季尖峰

    # 2. 判断【低谷时段】(便宜电，火力全开)
    # 0:00-6:00 或 11:00-14:00 (新增的午间低谷)
    if hour < 6 or (11 <= hour < 14):
        return "VALLEY"

    # 3. 判断【高峰时段】(昂贵电，准备错峰)
    # 16:00-23:00 (刨去上面的尖峰时间后，剩下的就是普通高峰)
    if 16 <= hour < 23:
        return "PEAK"

    # 4. 剩余的全部为【平段】(基准电价)
    # 包括: 6:00-11:00, 14:00-16:00, 23:00-24:00
    return "FLAT"

# ==========================================
# 获取 UCI 原生数据集
# ==========================================
print("⏳ 正在从 UCI 机器学习库拉取钢铁能耗数据 (大约需要几秒钟)...")
steel_dataset = fetch_ucirepo(id=851)

# 将特征集(Features)和目标集(Targets)横向合并为一个完整的表
df_features = steel_dataset.data.features
df_targets = steel_dataset.data.targets
df = pd.concat([df_features, df_targets], axis=1)

print(f"✅ 数据集下载成功！共获取 {len(df)} 条记录。启动高频数据泵...")
print("-" * 60)

# ==========================================
# 组装、衍生与高频发送
# ==========================================
for index, row in df.iterrows():
    # 读取真实基础特征
    usage_kwh = float(row['Usage_kWh'])
    co2 = float(row['CO2(tCO2)'])
    nsm = int(row['NSM'])
    week_status_str = str(row['WeekStatus']).strip()
    week_status = 1 if week_status_str == 'Weekday' else 0

    # 💡 魔法 1：派生运行工况 (0=停机, 1=空转, 2=正常, 3=高负荷)
    if usage_kwh < 5:
        op_status = 0
    elif usage_kwh < 20:
        op_status = 1
    elif usage_kwh < 80:
        op_status = 2
    else:
        op_status = 3

    # 结合物理逻辑派生设备温度、振动、水压
    # 停机时降至室温/静止；运行时随耗电量增加而增加，并加上环境白噪声
    temperature = 25.0 if op_status == 0 else (800 + usage_kwh * 3 + random.uniform(-15, 15))
    vibration = 0.0 if op_status == 0 else (2.0 + usage_kwh * 0.05 + random.uniform(-0.5, 0.5))
    pressure = 0.0 if op_status == 0 else (150.0 + random.uniform(-5, 5))

    # 制造智能告警的“剧本数据” (1.5% 的概率触发)
    fault_chance = random.random()
    if fault_chance < 0.015 and op_status == 1:
        # 剧本 A: 机械卡涩 (空转时振动突然剧烈)
        vibration = 25.5
        print("   ⚠️ [注入剧本] 制造机械卡涩数据！注意观察后端控制台！")
    elif fault_chance > 0.985 and op_status >= 2:
        # 剧本 B: 冷却断流 (运行中水压骤降，温度飙升)
        temperature = 1150.0
        pressure = 30.0
        print("   ⚠️ [注入剧本] 制造冷却断流数据！注意观察后端控制台！")

    # 组装完整的 JSON 载荷
    payload = {
        "deviceCode": DEVICE_CODE,
        "usageKwh": round(usage_kwh, 2),
        "co2Emission": round(co2, 2),
        "nsm": nsm,
        "weekStatus": week_status,
        "dayOfWeek": str(row['Day_of_week']),
        "loadType": str(row['Load_Type']),
        "xianPriceTier": get_price_tier(nsm),
        "temperature": round(temperature, 2),
        "vibration": round(vibration, 2),
        "pressure": round(pressure, 2),
        "operatingStatus": op_status
        # time 字段故意不传，让 Spring Boot 自动补全当前的真实时序
    }

    # 发送 HTTP POST 请求到 Spring Boot
    try:
        response = requests.post(API_URL, json=payload)
        now_time = datetime.now().strftime("%H:%M:%S")
        print(f"[{now_time}] 🚀 发送成功 | 能耗: {payload['usageKwh']} kWh | 工况: {op_status} | 电价: {payload['xianPriceTier']:<13} | 响应: {response.status_code}")
    except Exception as e:
        print(f"❌ 发送失败，请检查 Spring Boot 是否启动。错误: {e}")

    # 暂停等待下一次发送
    time.sleep(SLEEP_INTERVAL)