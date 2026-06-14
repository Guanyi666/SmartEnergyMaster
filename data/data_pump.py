import argparse
import logging
import os
import random
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pandas as pd
import requests

try:
    from ucimlrepo import fetch_ucirepo
except ImportError:
    fetch_ucirepo = None

API_URL = os.getenv("SENSOR_API_URL", "http://localhost:8080/api/sensor/upload")
API_KEY = os.getenv("SENSOR_API_KEY", "dev-sensor-key-please-rotate-in-prod")
DEFAULT_SLEEP_INTERVAL = 3
DEFAULT_HISTORY_HOURS = 24
DEFAULT_FAULT_RATE = 0.012
HISTORY_STEP_MINUTES = 15
BACKFILL_SLEEP = 0.05
SHANGHAI_TZ = timezone(timedelta(hours=8))
DATA_FILE = Path(__file__).resolve().parent / "raw_steel_data.csv"
logger = logging.getLogger("data_pump")

# ═══════════════════════════════════════════════════════════════════════
# 基准参数：70吨电弧炉中型炼钢厂
# ═══════════════════════════════════════════════════════════════════════
EAF_RATED_MW = 40.0          # 电弧炉额定有功功率 (MW)
INTERVAL_HOURS = 0.25        # 15分钟采样间隔
EAF_TON_STEEL_KWH = 400.0    # 吨钢电耗基准 (kWh/t)
GRID_EMISSION_FACTOR = 0.0005703   # 中国电网排放因子 (tCO2/kWh)
PROCESS_EMISSION_FACTOR = 0.00015  # 电极消耗+渣料工艺排放 (tCO2/kWh)

# 电价：陕西省大工业电价 1-10kV (元/kWh)
PRICE_CRITICAL_PEAK = 1.25
PRICE_PEAK = 0.95
PRICE_FLAT = 0.60
PRICE_VALLEY = 0.32
PRICE_DEEP_VALLEY = 0.22


def get_price_tier(nsm, month=None, is_holiday=False):
    """陕西省大工业分时电价时段判定"""
    hour = nsm // 3600
    # 深谷：法定节假日 0:00-6:00
    if is_holiday and hour < 6:
        return "DEEP_VALLEY"
    # 谷：23:00-6:00
    if hour >= 23 or hour < 6:
        return "VALLEY"
    # 平：6:00-8:00, 11:00-18:00
    if (6 <= hour < 8) or (11 <= hour < 18):
        return "FLAT"
    # 尖峰：夏季 7-8月 19:00-21:00, 冬季 1/12月 18:00-20:00
    if month and month in (7, 8) and 19 <= hour < 21:
        return "CRITICAL_PEAK"
    if month and month in (1, 12) and 18 <= hour < 20:
        return "CRITICAL_PEAK"
    # 峰：8:00-11:00, 18:00-23:00
    if (8 <= hour < 11) or (18 <= hour < 23):
        return "PEAK"
    return "FLAT"


def get_price_value(tier):
    """返回电价数值"""
    return {
        "CRITICAL_PEAK": PRICE_CRITICAL_PEAK,
        "PEAK": PRICE_PEAK,
        "FLAT": PRICE_FLAT,
        "VALLEY": PRICE_VALLEY,
        "DEEP_VALLEY": PRICE_DEEP_VALLEY,
    }.get(tier, PRICE_FLAT)


def load_dataset():
    if DATA_FILE.exists():
        logger.info("[dataset] load local csv: %s", DATA_FILE)
        return pd.read_csv(DATA_FILE)

    if fetch_ucirepo is None:
        raise RuntimeError("raw_steel_data.csv does not exist and ucimlrepo is unavailable")

    logger.info("[dataset] local csv not found, fetching UCI dataset 851")
    steel_dataset = fetch_ucirepo(id=851)
    df = pd.concat([steel_dataset.data.features, steel_dataset.data.targets], axis=1)
    df.to_csv(DATA_FILE, index=False)
    return df


def clamp(value, lower, upper):
    return max(lower, min(upper, value))


def lerp(current, target, ratio):
    return current + (target - current) * ratio


def infer_stage(usage_kwh, hour, week_status_text):
    """EAF 工况判定 — 偏向 RUNNING 以减少停机占比（目标 ~10%）"""
    is_production = (8 <= hour < 22)  # 生产时段 8:00-22:00
    # 仅在非生产时段且极低功耗才判定 STOPPED
    if usage_kwh < 1.0 and not is_production:
        return "STOPPED"
    # 凌晨低功耗 → IDLE（待机保温）
    if usage_kwh < 8.0 or (usage_kwh < 20 and hour < 8):
        return "IDLE"
    # 高负荷废钢熔化期
    if usage_kwh > 80:
        return "HIGH_LOAD"
    return "RUNNING"


class DeviceSimulator:
    def __init__(self, device_code, device_name, seed, fault_rate=DEFAULT_FAULT_RATE):
        self.device_code = device_code
        self.device_name = device_name
        self.random = random.Random(seed)
        self.fault_rate = clamp(fault_rate, 0.0, 1.0)
        self.temperature = 80.0
        self.vibration = 1.0
        self.pressure = 200.0
        self.fault_mode = None
        self.fault_remaining = 0
        self.sensor_drift_remaining = 0
        self.sensor_bias = {
            "usageKwh": 0.0,
            "temperature": 0.0,
            "vibration": 0.0,
            "pressure": 0.0,
        }

    def maybe_start_fault(self, stage):
        self.tick_sensor_drift()
        if self.fault_mode is not None:
            self.fault_remaining -= 1
            if self.fault_remaining <= 0:
                self.fault_mode = None
            return

        if self.device_code != "EAF-01" or self.random.random() >= self.fault_rate:
            return

        roll = self.random.random()
        if stage in {"RUNNING", "HIGH_LOAD"} and roll < 0.22:
            self.fault_mode = "COOLING_INTERRUPT"
            self.fault_remaining = self.random.randint(2, 4)
        elif stage in {"RUNNING", "HIGH_LOAD"} and roll < 0.55:
            self.fault_mode = "BEARING_WEAR"
            self.fault_remaining = self.random.randint(18, 36)
        elif stage in {"IDLE", "RUNNING"} and roll < 0.82:
            self.fault_mode = "INTERMITTENT_JAM"
            self.fault_remaining = self.random.randint(6, 12)
        elif stage in {"IDLE", "RUNNING", "HIGH_LOAD"}:
            self.fault_mode = "MECHANICAL_JAM"
            self.fault_remaining = self.random.randint(3, 5)

    def tick_sensor_drift(self):
        if self.sensor_drift_remaining > 0:
            self.sensor_drift_remaining -= 1
            for field in self.sensor_bias:
                self.sensor_bias[field] = lerp(self.sensor_bias[field], self.sensor_bias[field] * 1.015, 0.25)
            return

        if any(abs(value) > 0.02 for value in self.sensor_bias.values()):
            for field in self.sensor_bias:
                self.sensor_bias[field] = lerp(self.sensor_bias[field], 0.0, 0.10)
            return

        if self.random.random() < self.fault_rate * 0.18:
            direction = -1 if self.random.random() < 0.5 else 1
            self.sensor_drift_remaining = self.random.randint(24, 72)
            self.sensor_bias = {
                "usageKwh": direction * self.random.uniform(50, 250),
                "temperature": direction * self.random.uniform(4.0, 24.0),
                "vibration": direction * self.random.uniform(0.15, 1.2),
                "pressure": direction * self.random.uniform(5, 20),
            }

    def apply_sensor_drift(self, payload):
        if not any(abs(value) > 0.01 for value in self.sensor_bias.values()):
            return payload

        adjusted = dict(payload)
        limits = {
            "usageKwh": (0.0, 60000.0),
            "temperature": (0.0, 1800.0),
            "vibration": (0.0, 28.0),
            "pressure": (0.0, 1000.0),
        }
        for field, bias in self.sensor_bias.items():
            if field in adjusted and adjusted[field] is not None:
                lower, upper = limits[field]
                adjusted[field] = round(clamp(float(adjusted[field]) + bias, lower, upper), 2)
        return adjusted

    def active_faults(self):
        faults = []
        if self.fault_mode:
            faults.append(self.fault_mode)
        if self.sensor_drift_remaining > 0:
            faults.append("SENSOR_DRIFT")
        return faults

    def build_payload(self, row, simulated_time):
        raise NotImplementedError


class FurnaceSimulator(DeviceSimulator):
    def __init__(self, device_code, device_name, seed, fault_rate=DEFAULT_FAULT_RATE):
        super().__init__(device_code, device_name, seed, fault_rate)
        self.hearth_heat = 0.25
        self.cooling_health = 1.0
        self.bearing_health = 1.0
        self._prev_usage_raw = None
        self._stage_history = []
        self._current_stage = "STOPPED"
        self._stage_hysteresis = 5  # 降低迟滞（8→5）加快响应

    def build_payload(self, row, simulated_time):
        usage_csv = float(row["Usage_kWh"])
        co2_raw = max(0.0, float(row["CO2(tCO2)"]))
        nsm = int(row["NSM"])
        week_status_text = str(row["WeekStatus"]).strip()
        week_status = 1 if week_status_text == "Weekday" else 0
        hour = nsm // 3600
        power_factor = float(row["Lagging_Current_Power_Factor"])
        day_of_week = str(row["Day_of_week"])

        # ── 行间插值平滑 ──
        if self._prev_usage_raw is not None:
            usage_csv = lerp(self._prev_usage_raw, usage_csv, 0.12)
        self._prev_usage_raw = usage_csv

        # ── 功率放大：UCI 数据集 0-157 → 中型炼钢厂 0-50000 kWh/15min ──
        # scale = EAF_RATED_MW * 1000 * INTERVAL_HOURS / 140 ≈ 40000*0.25/140 ≈ 71.4
        # 用 ~300x 因子让 HIGH_LOAD 达到 9000-10500 kWh/15min
        SCALE = 300.0
        usage_raw = usage_csv * SCALE

        # ── 工况判定（偏置：减少 STOPPED）──
        raw_stage = infer_stage(usage_csv, hour, week_status_text)
        self._stage_history.append(raw_stage)
        if len(self._stage_history) > self._stage_hysteresis:
            self._stage_history.pop(0)
        if len(self._stage_history) >= self._stage_hysteresis:
            if all(s == raw_stage for s in self._stage_history[-self._stage_hysteresis:]):
                self._current_stage = raw_stage
        stage = self._current_stage

        self.maybe_start_fault(stage)

        # 工艺强度指数（归一化到 0-1）
        usage_norm = clamp(usage_raw / 45000.0, 0.0, 1.0)
        electrical_stress = clamp((100 - power_factor) / 35, 0.0, 1.0)

        # ── 炉膛热量 ──
        stage_heat_target = {
            "STOPPED": 0.08, "IDLE": 0.28, "RUNNING": 0.74, "HIGH_LOAD": 0.95
        }[stage]
        self.hearth_heat = clamp(lerp(self.hearth_heat, stage_heat_target, 0.14), 0.05, 1.0)

        # ── 轴承 / 冷却健康度衰减 ──
        self.bearing_health = clamp(
            self.bearing_health - (0.0012 * usage_norm + 0.0008 * electrical_stress) + 0.0007,
            0.72, 1.0,
        )
        self.cooling_health = clamp(self.cooling_health + 0.0015, 0.78, 1.0)

        # ── 故障修改 ──
        if self.fault_mode == "MECHANICAL_JAM":
            stage = "IDLE"
            self.bearing_health = clamp(self.bearing_health - 0.06, 0.45, 1.0)
        elif self.fault_mode == "INTERMITTENT_JAM":
            self.bearing_health = clamp(self.bearing_health - 0.025, 0.50, 1.0)
            if self.random.random() < 0.55:
                stage = "IDLE"
        elif self.fault_mode == "COOLING_INTERRUPT":
            stage = "HIGH_LOAD"
            self.cooling_health = clamp(self.cooling_health - 0.18, 0.35, 1.0)
        elif self.fault_mode == "BEARING_WEAR":
            self.bearing_health = clamp(self.bearing_health - 0.011, 0.50, 1.0)

        # ── 各工况功率范围 (kWh/15min) ──
        usage = {
            "STOPPED":   clamp(usage_raw * 0.01 + self.random.uniform(0, 15), 0, 25),
            "IDLE":      clamp(usage_raw * 0.15 + self.random.uniform(-80, 120), 250, 1250),
            "RUNNING":   clamp(usage_raw * 0.85 + self.random.uniform(-500, 800), 4500, 10000),
            "HIGH_LOAD": clamp(usage_raw * 1.05 + self.random.uniform(-800, 1200), 7500, 15000),
        }[stage]

        # ── 温度：200-1700°C（钢水出钢温度 1620-1680°C）──
        temperature_target = (
            200 + 1500 * self.hearth_heat + 48 * usage_norm
            + 36 * electrical_stress + self.random.uniform(-4.0, 4.0)
        )
        # ── 振动：保持原有合理范围 ──
        vibration_target = (
            0.8 + 6.5 * usage_norm + 5.5 * (1 - self.bearing_health)
            + self.random.uniform(-0.10, 0.12)
        )
        # ── 压力：80-400 kPa (冷却水系统) ──
        pressure_target = (
            120 + 180 * usage_norm - 30 * electrical_stress
            + 50 * self.cooling_health + self.random.uniform(-2.0, 2.0)
        )

        self.temperature = lerp(self.temperature, temperature_target, 0.04)
        self.vibration = lerp(self.vibration, vibration_target, 0.12)
        self.pressure = lerp(self.pressure, pressure_target, 0.08)

        # ── 故障时传感器异常值 ──
        if self.fault_mode == "MECHANICAL_JAM":
            self.temperature = lerp(self.temperature, 380 + self.random.uniform(-20, 30), 0.35)
            self.vibration = lerp(self.vibration, self.random.uniform(18.0, 24.5), 0.92)
            self.pressure = lerp(self.pressure, self.random.uniform(130, 160), 0.35)
        elif self.fault_mode == "INTERMITTENT_JAM":
            if self.random.random() < 0.60:
                self.temperature = lerp(self.temperature, 350 + self.random.uniform(-20, 25), 0.25)
                self.vibration = lerp(self.vibration, self.random.uniform(12.5, 20.5), 0.75)
                self.pressure = lerp(self.pressure, self.random.uniform(120, 150), 0.28)
            else:
                self.vibration = lerp(self.vibration, self.random.uniform(5.0, 8.5), 0.30)
        elif self.fault_mode == "COOLING_INTERRUPT":
            self.temperature = lerp(self.temperature, self.random.uniform(1040, 1165), 0.68)
            self.vibration = lerp(self.vibration, self.random.uniform(7.5, 10.8), 0.45)
            self.pressure = lerp(self.pressure, self.random.uniform(30, 55), 0.88)  # 冷却中断→压力骤降
        elif self.fault_mode == "BEARING_WEAR":
            wear_index = 1 - self.bearing_health
            self.temperature = lerp(self.temperature, self.temperature + 42 * wear_index, 0.18)
            self.vibration = lerp(self.vibration, 6.5 + 30 * wear_index + self.random.uniform(-0.5, 1.4), 0.42)

        op_status = {"STOPPED": 0, "IDLE": 1, "RUNNING": 2, "HIGH_LOAD": 3}[stage]

        # ── 碳排放：电网 0.5703 + 工艺 0.15 = 0.7203 kg/kWh → tCO2/kWh ──
        co2_emission = usage * (GRID_EMISSION_FACTOR + PROCESS_EMISSION_FACTOR)

        payload = {
            "deviceCode": self.device_code,
            "usageKwh": round(usage, 2),
            "co2Emission": round(max(co2_raw * SCALE * 0.0005, co2_emission), 2),
            "nsm": nsm,
            "weekStatus": week_status,
            "dayOfWeek": day_of_week,
            "loadType": stage,
            "xianPriceTier": get_price_tier(nsm),
            "temperature": round(clamp(self.temperature, 100, 1750), 2),
            "vibration": round(clamp(self.vibration, 0.2, 26), 2),
            "pressure": round(clamp(self.pressure, 30, 450), 2),
            "operatingStatus": op_status,
            "time": simulated_time.isoformat(),
        }
        return self.apply_sensor_drift(payload)


class PumpSimulator(DeviceSimulator):
    def __init__(self, device_code, device_name, seed, fault_rate=DEFAULT_FAULT_RATE):
        super().__init__(device_code, device_name, seed, fault_rate)
        self.flow_health = 1.0

    def build_payload(self, furnace_payload, simulated_time):
        self.tick_sensor_drift()
        furnace_status = furnace_payload["operatingStatus"]
        furnace_pressure = furnace_payload["pressure"]
        furnace_temperature = furnace_payload["temperature"]

        # 冷却需求跟随炉温
        demand_index = clamp((furnace_temperature - 100) / 1500, 0.1, 1.0)

        # 功率：0.3-1.8 MW → 75-450 kWh/15min
        usage = (0.3 + 1.2 * demand_index + self.random.uniform(-0.03, 0.03)) * 1000 * INTERVAL_HOURS

        self.flow_health = clamp(self.flow_health + 0.0012 - 0.001 * demand_index, 0.82, 1.0)

        # 温度：25-48°C（冷却水回水）
        temp_target = 28 + 18 * demand_index + self.random.uniform(-0.4, 0.4)
        vibration_target = 0.8 + 3.8 * demand_index + 2.0 * (1 - self.flow_health) + self.random.uniform(-0.06, 0.08)
        # 压力：150-400 kPa（泵出口 > 炉入口）
        pressure_target = clamp(furnace_pressure + 30 + 40 * demand_index, 150, 400)

        self.temperature = lerp(self.temperature, temp_target, 0.10)
        self.vibration = lerp(self.vibration, vibration_target, 0.15)
        self.pressure = lerp(self.pressure, pressure_target, 0.12)

        # 降低停机阈值：< 75 kWh/15min 才停机
        op_status = 1 if usage < 100 else (2 if usage < 250 else 3)  # 水泵最低 IDLE，不 STOPPED

        payload = {
            "deviceCode": self.device_code,
            "usageKwh": round(clamp(usage, 50, 500), 2),
            "co2Emission": round(usage * GRID_EMISSION_FACTOR, 2),
            "nsm": furnace_payload["nsm"],
            "weekStatus": furnace_payload["weekStatus"],
            "dayOfWeek": furnace_payload["dayOfWeek"],
            "loadType": "COOLING_SUPPORT",
            "xianPriceTier": furnace_payload["xianPriceTier"],
            "temperature": round(clamp(self.temperature, 22, 52), 2),
            "vibration": round(clamp(self.vibration, 0.2, 7.5), 2),
            "pressure": round(clamp(self.pressure, 140, 420), 2),
            "operatingStatus": op_status,
            "time": simulated_time.isoformat(),
        }
        return self.apply_sensor_drift(payload)


class CompressorSimulator(DeviceSimulator):
    def __init__(self, device_code, device_name, seed, fault_rate=DEFAULT_FAULT_RATE):
        super().__init__(device_code, device_name, seed, fault_rate)
        self.air_health = 1.0

    def build_payload(self, furnace_payload, simulated_time):
        self.tick_sensor_drift()
        furnace_usage = furnace_payload["usageKwh"]

        # 用气需求跟随 EAF 负荷
        load_index = clamp(furnace_usage / 45000.0, 0.15, 1.0)

        # 功率：0.4-3.5 MW → 100-875 kWh/15min
        usage = (0.4 + 2.6 * load_index + self.random.uniform(-0.05, 0.05)) * 1000 * INTERVAL_HOURS

        self.air_health = clamp(self.air_health + 0.001 - 0.0009 * load_index, 0.85, 1.0)

        # 温度：70-110°C（排气温度）
        temp_target = 72 + 38 * load_index + self.random.uniform(-1.0, 1.0)
        vibration_target = 1.0 + 4.6 * load_index + 1.8 * (1 - self.air_health) + self.random.uniform(-0.08, 0.10)
        # 压力：500-850 kPa（工业压缩空气标准）
        pressure_target = 520 + 280 * load_index + self.random.uniform(-5, 5)

        self.temperature = lerp(self.temperature, temp_target, 0.08)
        self.vibration = lerp(self.vibration, vibration_target, 0.15)
        self.pressure = lerp(self.pressure, pressure_target, 0.10)

        op_status = 1 if usage < 120 else (2 if usage < 400 else 3)  # 空压机最低 IDLE

        payload = {
            "deviceCode": self.device_code,
            "usageKwh": round(clamp(usage, 70, 950), 2),
            "co2Emission": round(usage * GRID_EMISSION_FACTOR, 2),
            "nsm": furnace_payload["nsm"],
            "weekStatus": furnace_payload["weekStatus"],
            "dayOfWeek": furnace_payload["dayOfWeek"],
            "loadType": "AIR_SUPPLY",
            "xianPriceTier": furnace_payload["xianPriceTier"],
            "temperature": round(clamp(self.temperature, 65, 115), 2),
            "vibration": round(clamp(self.vibration, 0.4, 7.8), 2),
            "pressure": round(clamp(self.pressure, 480, 880), 2),
            "operatingStatus": op_status,
            "time": simulated_time.isoformat(),
        }
        return self.apply_sensor_drift(payload)


class LadleFurnaceSimulator(DeviceSimulator):
    """钢包精炼炉 70t LF — 出钢后合金化、脱硫、调温"""

    def __init__(self, device_code, device_name, seed, fault_rate=DEFAULT_FAULT_RATE):
        super().__init__(device_code, device_name, seed, fault_rate)
        self.electrode_health = 1.0

    def build_payload(self, furnace_payload, simulated_time):
        self.tick_sensor_drift()
        furnace_stage = furnace_payload["loadType"]
        furnace_temp = furnace_payload["temperature"]

        # 降低激活温度门槛（900→600）
        lf_active = furnace_temp > 600 and furnace_stage in ("RUNNING", "HIGH_LOAD")
        demand = clamp((furnace_temp - 600) / 1000, 0.0, 1.0) if lf_active else 0.0

        # 0-20 MW → 0-5000 kWh/15min
        usage = (demand * 18 + self.random.uniform(-0.2, 0.3)) * 1000 * INTERVAL_HOURS
        temp_target = 1480 + 180 * demand + self.random.uniform(-4.0, 4.0)
        self.electrode_health = clamp(self.electrode_health + 0.0006 - 0.0003 * demand, 0.88, 1.0)
        vibration_target = 0.4 + 2.6 * demand + 1.6 * (1 - self.electrode_health) + self.random.uniform(-0.06, 0.08)
        pressure_target = 120 + 100 * demand + self.random.uniform(-2.0, 2.0)

        self.temperature = lerp(self.temperature, temp_target, 0.05)
        self.vibration = lerp(self.vibration, vibration_target, 0.10)
        self.pressure = lerp(self.pressure, pressure_target, 0.08)

        op_status = 1 if demand < 0.2 else (2 if demand < 0.6 else 3)  # LF 最低 IDLE

        payload = {
            "deviceCode": self.device_code,
            "usageKwh": round(clamp(usage, 0, 5200), 2),
            "co2Emission": round(usage * GRID_EMISSION_FACTOR, 2),
            "nsm": furnace_payload["nsm"],
            "weekStatus": furnace_payload["weekStatus"],
            "dayOfWeek": furnace_payload["dayOfWeek"],
            "loadType": "REFINING",
            "xianPriceTier": furnace_payload["xianPriceTier"],
            "temperature": round(clamp(self.temperature, 1450, 1680), 2),
            "vibration": round(clamp(self.vibration, 0.2, 4.8), 2),
            "pressure": round(clamp(self.pressure, 100, 250), 2),
            "operatingStatus": op_status,
            "time": simulated_time.isoformat(),
        }
        return self.apply_sensor_drift(payload)


class ContinuousCasterSimulator(DeviceSimulator):
    """连铸机 — 钢水连续浇铸成坯"""

    def __init__(self, device_code, device_name, seed, fault_rate=DEFAULT_FAULT_RATE):
        super().__init__(device_code, device_name, seed, fault_rate)
        self.mold_health = 1.0

    def build_payload(self, furnace_payload, simulated_time):
        self.tick_sensor_drift()
        furnace_temp = furnace_payload["temperature"]
        furnace_usage = furnace_payload["usageKwh"]

        # 降低激活阈值
        casting_active = furnace_temp > 600 and furnace_usage > 2000
        demand = clamp((furnace_temp - 600) / 1000, 0.0, 1.0) if casting_active else 0.0

        # 0-6 MW → 0-1500 kWh/15min
        usage = (1.5 + 4.5 * demand + self.random.uniform(-0.1, 0.1)) * 1000 * INTERVAL_HOURS
        temp_target = 720 + 480 * demand + self.random.uniform(-3.0, 3.0)
        self.mold_health = clamp(self.mold_health + 0.0004 - 0.0005 * demand, 0.82, 1.0)
        vibration_target = 0.5 + 2.4 * demand + 1.2 * (1 - self.mold_health) + self.random.uniform(-0.05, 0.06)
        pressure_target = 120 + 150 * demand + self.random.uniform(-2.0, 2.0)

        self.temperature = lerp(self.temperature, temp_target, 0.06)
        self.vibration = lerp(self.vibration, vibration_target, 0.10)
        self.pressure = lerp(self.pressure, pressure_target, 0.08)

        op_status = 1 if demand < 0.15 else (2 if demand < 0.5 else 3)  # 连铸机最低 IDLE

        payload = {
            "deviceCode": self.device_code,
            "usageKwh": round(clamp(usage, 0, 1600), 2),
            "co2Emission": round(usage * GRID_EMISSION_FACTOR, 2),
            "nsm": furnace_payload["nsm"],
            "weekStatus": furnace_payload["weekStatus"],
            "dayOfWeek": furnace_payload["dayOfWeek"],
            "loadType": "CASTING",
            "xianPriceTier": furnace_payload["xianPriceTier"],
            "temperature": round(clamp(self.temperature, 680, 1280), 2),
            "vibration": round(clamp(self.vibration, 0.2, 4.5), 2),
            "pressure": round(clamp(self.pressure, 100, 300), 2),
            "operatingStatus": op_status,
            "time": simulated_time.isoformat(),
        }
        return self.apply_sensor_drift(payload)


class DustCollectorSimulator(DeviceSimulator):
    """除尘系统 — 电弧炉烟气捕集与布袋除尘"""

    def __init__(self, device_code, device_name, seed, fault_rate=DEFAULT_FAULT_RATE):
        super().__init__(device_code, device_name, seed, fault_rate)
        self.filter_health = 1.0

    def build_payload(self, furnace_payload, simulated_time):
        self.tick_sensor_drift()
        furnace_usage = furnace_payload["usageKwh"]
        furnace_stage = furnace_payload["loadType"]

        # 降低激活阈值
        dc_active = furnace_usage > 500 or furnace_stage in ("RUNNING", "HIGH_LOAD")
        demand = clamp(furnace_usage / 45000.0, 0.0, 1.0) if dc_active else 0.05

        # 0.2-3.5 MW → 50-875 kWh/15min
        usage = (0.2 + 2.8 * demand + self.random.uniform(-0.02, 0.02)) * 1000 * INTERVAL_HOURS
        self.filter_health = clamp(self.filter_health + 0.0002 - 0.0008 * demand, 0.78, 1.0)
        pressure_diff = 0.8 + 2.8 * demand + 3.5 * (1 - self.filter_health) + self.random.uniform(-0.08, 0.10)
        temp_target = 55 + 38 * demand + self.random.uniform(-1.5, 1.5)
        vibration_target = 0.4 + 3.5 * demand + 1.2 * (1 - self.filter_health) + self.random.uniform(-0.05, 0.06)

        self.temperature = lerp(self.temperature, temp_target, 0.06)
        self.vibration = lerp(self.vibration, vibration_target, 0.10)
        self.pressure = lerp(self.pressure, pressure_diff, 0.08)

        # 基本保持运行（环保要求）
        op_status = 2 if dc_active else 1

        payload = {
            "deviceCode": self.device_code,
            "usageKwh": round(clamp(usage, 40, 950), 2),
            "co2Emission": round(0.0, 2),
            "nsm": furnace_payload["nsm"],
            "weekStatus": furnace_payload["weekStatus"],
            "dayOfWeek": furnace_payload["dayOfWeek"],
            "loadType": "DUST_COLLECTION",
            "xianPriceTier": furnace_payload["xianPriceTier"],
            "temperature": round(clamp(self.temperature, 38, 120), 2),
            "vibration": round(clamp(self.vibration, 0.2, 5.5), 2),
            "pressure": round(clamp(self.pressure, 0.5, 7.5), 2),
            "operatingStatus": op_status,
            "time": simulated_time.isoformat(),
        }
        return self.apply_sensor_drift(payload)


def send_payload(payload):
    headers = {"X-Api-Key": API_KEY} if API_KEY else {}
    response = requests.post(API_URL, json=payload, headers=headers, timeout=8)
    response.raise_for_status()


def send_cycle(furnace_sim, pump_sim, compressor_sim, ladle_sim, caster_sim, dust_sim,
               row, simulated_time, delay):
    furnace_payload = furnace_sim.build_payload(row, simulated_time)
    pump_payload = pump_sim.build_payload(furnace_payload, simulated_time)
    compressor_payload = compressor_sim.build_payload(furnace_payload, simulated_time)
    ladle_payload = ladle_sim.build_payload(furnace_payload, simulated_time)
    caster_payload = caster_sim.build_payload(furnace_payload, simulated_time)
    dust_payload = dust_sim.build_payload(furnace_payload, simulated_time)

    payloads = [furnace_payload, pump_payload, compressor_payload,
                ladle_payload, caster_payload, dust_payload]
    now_time = datetime.now(SHANGHAI_TZ).strftime("%H:%M:%S")

    try:
        send_payload(payloads)
        for payload in payloads:
            logger.info(
                "[%s] sent %-8s load=%8.0fkWh status=%s temp=%7.0f press=%6.0f tier=%s",
                now_time,
                payload["deviceCode"],
                payload["usageKwh"],
                payload["operatingStatus"],
                payload["temperature"],
                payload["pressure"],
                payload["xianPriceTier"],
            )
    except Exception as exc:
        for payload in payloads:
            logger.warning("[%s] failed %s: %s", now_time, payload["deviceCode"], exc)

    for sim in (furnace_sim, pump_sim, compressor_sim, ladle_sim, caster_sim, dust_sim):
        faults = sim.active_faults()
        if faults:
            logger.warning("   anomaly on %s: %s", sim.device_code, ", ".join(faults))

    time.sleep(delay)


def parse_args():
    parser = argparse.ArgumentParser(description="SmartEnergyMaster plant simulator data pump")
    parser.add_argument("--interval", type=float, default=DEFAULT_SLEEP_INTERVAL)
    parser.add_argument("--fault-rate", type=float, default=DEFAULT_FAULT_RATE)
    parser.add_argument("--history-hours", type=float, default=DEFAULT_HISTORY_HOURS)
    return parser.parse_args()


def main():
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    args = parse_args()
    live_interval = max(0.2, args.interval)
    history_hours = max(0.25, args.history_hours)
    backfill_points = max(1, int(round(history_hours * 60 / HISTORY_STEP_MINUTES)))
    fault_rate = clamp(args.fault_rate, 0.0, 1.0)

    df = load_dataset()
    logger.info("[dataset] rows=%s", len(df))
    logger.info(
        "[config] interval=%ss fault_rate=%s history_hours=%s backfill_points=%s",
        live_interval,
        fault_rate,
        history_hours,
        backfill_points,
    )
    logger.info(
        "[model] 70t EAF, rated=%sMW, CO2_factor=%.5ft/kWh",
        EAF_RATED_MW,
        GRID_EMISSION_FACTOR + PROCESS_EMISSION_FACTOR,
    )

    furnace_sim = FurnaceSimulator("EAF-01", "1号电弧炉", 20260320, fault_rate)
    pump_sim = PumpSimulator("PUMP-01", "循环水泵", 20260321, fault_rate * 0.35)
    compressor_sim = CompressorSimulator("COMP-01", "空压机A", 20260322, fault_rate * 0.30)
    ladle_sim = LadleFurnaceSimulator("LF-01", "钢包精炼炉", 20260323, fault_rate * 0.25)
    caster_sim = ContinuousCasterSimulator("CC-01", "1号连铸机", 20260324, fault_rate * 0.20)
    dust_sim = DustCollectorSimulator("DC-01", "主除尘系统", 20260325, fault_rate * 0.10)

    start_index = random.randint(0, max(0, len(df) - backfill_points - 1))
    backfill_slice = df.iloc[start_index:start_index + backfill_points].reset_index(drop=True)
    live_index = start_index + backfill_points

    backfill_start_time = datetime.now(SHANGHAI_TZ) - timedelta(hours=history_hours)
    logger.info("[backfill] sending %gh history data", history_hours)
    for offset, (_, row) in enumerate(backfill_slice.iterrows()):
        simulated_time = backfill_start_time + timedelta(minutes=HISTORY_STEP_MINUTES * offset)
        send_cycle(furnace_sim, pump_sim, compressor_sim, ladle_sim, caster_sim, dust_sim,
                   row, simulated_time, BACKFILL_SLEEP)

    logger.info("[live] switching to realtime push mode")
    while True:
        row = df.iloc[live_index % len(df)]
        simulated_time = datetime.now(SHANGHAI_TZ)
        send_cycle(furnace_sim, pump_sim, compressor_sim, ladle_sim, caster_sim, dust_sim,
                   row, simulated_time, live_interval)
        live_index += 1


if __name__ == "__main__":
    main()
