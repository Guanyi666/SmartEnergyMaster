import argparse
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
# ★ NH2: 传感器上传 API Key (与 backend application.yml app.sensor.api-key 一致)
API_KEY = os.getenv("SENSOR_API_KEY", "dev-sensor-key-please-rotate-in-prod")
DEFAULT_SLEEP_INTERVAL = 3
DEFAULT_HISTORY_HOURS = 24
DEFAULT_FAULT_RATE = 0.012
HISTORY_STEP_MINUTES = 15
BACKFILL_SLEEP = 0.05
SHANGHAI_TZ = timezone(timedelta(hours=8))
DATA_FILE = Path(__file__).resolve().parent / "raw_steel_data.csv"


def get_price_tier(nsm, month=8):
    hour = nsm // 3600
    if month in [7, 8] and 19 <= hour < 21:
        return "CRITICAL_PEAK"
    if month in [1, 12] and 18 <= hour < 20:
        return "CRITICAL_PEAK"
    if hour < 6 or 11 <= hour < 14:
        return "VALLEY"
    if 16 <= hour < 23:
        return "PEAK"
    return "FLAT"


def load_dataset():
    if DATA_FILE.exists():
        print(f"[dataset] load local csv: {DATA_FILE}")
        return pd.read_csv(DATA_FILE)

    if fetch_ucirepo is None:
        raise RuntimeError("raw_steel_data.csv does not exist and ucimlrepo is unavailable")

    print("[dataset] local csv not found, fetching UCI dataset 851")
    steel_dataset = fetch_ucirepo(id=851)
    df = pd.concat([steel_dataset.data.features, steel_dataset.data.targets], axis=1)
    df.to_csv(DATA_FILE, index=False)
    return df


def clamp(value, lower, upper):
    return max(lower, min(upper, value))


def lerp(current, target, ratio):
    return current + (target - current) * ratio


def infer_stage(usage_kwh, power_factor, day_type, hour):
    # The raw dataset mostly contains low-load points at night. We fold in
    # power factor and shift timing so the furnace does not look "busy but cold"
    # or "idle but still extremely hot" on the dashboard.
    if day_type == "Weekend" and hour < 6 and usage_kwh < 8:
        return "STOPPED"
    if usage_kwh < 2.5:
        return "STOPPED"
    if usage_kwh < 12 or power_factor < 72:
        return "IDLE"
    if usage_kwh < 90:
        return "RUNNING"
    return "HIGH_LOAD"


class DeviceSimulator:
    def __init__(self, device_code, device_name, seed, fault_rate=DEFAULT_FAULT_RATE):
        self.device_code = device_code
        self.device_name = device_name
        self.random = random.Random(seed)
        self.fault_rate = clamp(fault_rate, 0.0, 1.0)
        self.temperature = 80.0
        self.vibration = 1.0
        self.pressure = 120.0
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
                "usageKwh": direction * self.random.uniform(0.6, 2.8),
                "temperature": direction * self.random.uniform(4.0, 24.0),
                "vibration": direction * self.random.uniform(0.15, 1.2),
                "pressure": direction * self.random.uniform(1.8, 8.5),
            }

    def apply_sensor_drift(self, payload):
        if not any(abs(value) > 0.01 for value in self.sensor_bias.values()):
            return payload

        adjusted = dict(payload)
        limits = {
            "usageKwh": (0.0, 160.0),
            "temperature": (0.0, 1300.0),
            "vibration": (0.0, 28.0),
            "pressure": (0.0, 190.0),
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
        # 行间插值：平滑过渡用
        self._prev_usage_raw = None
        # 阶段迟滞：连续 N 次同一阶段才切换
        self._stage_history = []
        self._current_stage = "STOPPED"
        self._stage_hysteresis = 8  # 需连续 8 次（~24s 实时）同阶段才切换

    def build_payload(self, row, simulated_time):
        usage_raw = float(row["Usage_kWh"])
        # ── 行间插值平滑 ──
        # 数据集行间 usage 差异可达 100+ kWh。直接用 raw 值会导致 3s 内剧烈跳变。
        # 在相邻两行之间做线性插值（lerp），模拟真实工业过程的渐变特性。
        if self._prev_usage_raw is not None:
            usage_raw = lerp(self._prev_usage_raw, usage_raw, 0.12)
        self._prev_usage_raw = usage_raw

        co2_raw = max(0.0, float(row["CO2(tCO2)"]))
        nsm = int(row["NSM"])
        week_status_text = str(row["WeekStatus"]).strip()
        week_status = 1 if week_status_text == "Weekday" else 0
        hour = nsm // 3600
        lagging_reactive = float(row["Lagging_Current_Reactive.Power_kVarh"])
        power_factor = float(row["Lagging_Current_Power_Factor"])
        day_of_week = str(row["Day_of_week"])

        # ── 阶段迟滞（Hysteresis）──
        # 数据集相邻行可能分属完全不同的工况（如 HIGH_LOAD→STOPPED）。
        # 要求新阶段连续出现 _stage_hysteresis 次后才正式切换，避免频繁跳变。
        raw_stage = infer_stage(usage_raw, power_factor, week_status_text, hour)
        self._stage_history.append(raw_stage)
        if len(self._stage_history) > self._stage_hysteresis:
            self._stage_history.pop(0)
        # 仅当历史窗口中全部为同一阶段且与当前不同时才切换
        if len(self._stage_history) >= self._stage_hysteresis:
            if all(s == raw_stage for s in self._stage_history[-self._stage_hysteresis:]):
                self._current_stage = raw_stage
        stage = self._current_stage

        self.maybe_start_fault(stage)

        reactive_ratio = lagging_reactive / max(usage_raw, 1.0)
        electrical_stress = clamp((100 - power_factor) / 35, 0.0, 1.0)
        process_intensity = clamp(usage_raw / 140, 0.0, 1.0)

        # Hearth heat rises slowly when smelting and cools slowly when idling.
        stage_heat_target = {
            "STOPPED": 0.08,
            "IDLE": 0.28,
            "RUNNING": 0.74,
            "HIGH_LOAD": 0.95,
        }[stage]
        self.hearth_heat = clamp(lerp(self.hearth_heat, stage_heat_target, 0.14), 0.05, 1.0)

        # Persistent stress gradually ages the mechanical side and raises
        # vibration under the same production load.
        self.bearing_health = clamp(
            self.bearing_health - (0.0012 * process_intensity + 0.0008 * electrical_stress) + 0.0007,
            0.72,
            1.0,
        )
        self.cooling_health = clamp(self.cooling_health + 0.0015, 0.78, 1.0)

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

        # ── 缩减随机噪声（原幅度过大导致毛刺）──
        usage = {
            "STOPPED": clamp(usage_raw * 0.25 + self.random.uniform(0.05, 0.30), 0.2, 2.0),
            "IDLE": clamp(usage_raw * 0.92 + self.random.uniform(-0.25, 0.35), 2.8, 11.5),
            "RUNNING": clamp(usage_raw * 1.03 + self.random.uniform(-0.80, 1.00), 12.0, 82.0),
            "HIGH_LOAD": clamp(usage_raw * 1.01 + self.random.uniform(-1.20, 1.50), 82.0, 148.0),
        }[stage]

        # ── 增大热惯性（降低 lerp ratio）──
        # 真实电弧炉：温度升降以分钟计，不是秒级。ratio 从 0.24 → 0.04 模拟慢响应。
        temperature_target = (
            60
            + 980 * self.hearth_heat
            + 36 * process_intensity
            + 28 * electrical_stress
            + self.random.uniform(-3.0, 3.0)   # 原 ±12 → ±3
        )
        vibration_target = (
            0.8
            + 6.5 * process_intensity
            + 3.2 * reactive_ratio
            + 5.5 * (1 - self.bearing_health)
            + self.random.uniform(-0.10, 0.12)  # 原 ±0.35/0.45 → ±0.10/0.12
        )
        pressure_target = (
            112
            + 38 * process_intensity
            - 16 * electrical_stress
            + 10 * self.cooling_health
            + self.random.uniform(-1.0, 1.0)    # 原 ±3.5 → ±1.0
        )

        # 热惯性：温度变化最慢（0.04），振动最快（0.12），压力居中（0.08）
        self.temperature = lerp(self.temperature, temperature_target, 0.04)
        self.vibration = lerp(self.vibration, vibration_target, 0.12)
        self.pressure = lerp(self.pressure, pressure_target, 0.08)

        if self.fault_mode == "MECHANICAL_JAM":
            self.temperature = lerp(self.temperature, 330 + self.random.uniform(-18, 25), 0.35)
            self.vibration = lerp(self.vibration, self.random.uniform(18.0, 24.5), 0.92)
            self.pressure = lerp(self.pressure, self.random.uniform(112, 125), 0.35)
        elif self.fault_mode == "INTERMITTENT_JAM":
            jam_pulse = self.random.random() < 0.60
            if jam_pulse:
                self.temperature = lerp(self.temperature, 280 + self.random.uniform(-15, 20), 0.25)
                self.vibration = lerp(self.vibration, self.random.uniform(12.5, 20.5), 0.75)
                self.pressure = lerp(self.pressure, self.random.uniform(106, 130), 0.28)
            else:
                self.vibration = lerp(self.vibration, self.random.uniform(5.0, 8.5), 0.30)
        elif self.fault_mode == "COOLING_INTERRUPT":
            self.temperature = lerp(self.temperature, self.random.uniform(1040, 1165), 0.68)
            self.vibration = lerp(self.vibration, self.random.uniform(7.5, 10.8), 0.45)
            self.pressure = lerp(self.pressure, self.random.uniform(26, 42), 0.88)
        elif self.fault_mode == "BEARING_WEAR":
            wear_index = 1 - self.bearing_health
            self.temperature = lerp(self.temperature, self.temperature + 42 * wear_index, 0.18)
            self.vibration = lerp(self.vibration, 6.5 + 30 * wear_index + self.random.uniform(-0.5, 1.4), 0.42)

        op_status = {
            "STOPPED": 0,
            "IDLE": 1,
            "RUNNING": 2,
            "HIGH_LOAD": 3,
        }[stage]

        payload = {
            "deviceCode": self.device_code,
            "usageKwh": round(usage, 2),
            "co2Emission": round(max(co2_raw, usage * (0.010 + 0.010 * process_intensity)), 2),
            "nsm": nsm,
            "weekStatus": week_status,
            "dayOfWeek": day_of_week,
            "loadType": stage,
            "xianPriceTier": get_price_tier(nsm),
            "temperature": round(clamp(self.temperature, 40, 1200), 2),
            "vibration": round(clamp(self.vibration, 0.2, 26), 2),
            "pressure": round(clamp(self.pressure, 20, 168), 2),
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

        demand_index = clamp((furnace_temperature - 120) / 980, 0.0, 1.0)
        status_usage_map = {
            0: (2.8, 5.8),
            1: (7.5, 13.0),
            2: (14.5, 24.0),
            3: (22.0, 34.0),
        }

        self.flow_health = clamp(self.flow_health + 0.0012 - 0.001 * demand_index, 0.82, 1.0)

        usage = self.random.uniform(*status_usage_map[furnace_status]) + 2.2 * demand_index
        temp_target = 34 + 42 * demand_index + self.random.uniform(-0.6, 0.6)
        vibration_target = 0.8 + 3.8 * demand_index + 2.0 * (1 - self.flow_health) + self.random.uniform(-0.06, 0.08)
        pressure_target = clamp(furnace_pressure + 9 + 12 * self.flow_health + self.random.uniform(-0.8, 0.8), 88, 176)

        # 水泵热惯性比电弧炉快，但仍需平滑
        self.temperature = lerp(self.temperature, temp_target, 0.10)
        self.vibration = lerp(self.vibration, vibration_target, 0.15)
        self.pressure = lerp(self.pressure, pressure_target, 0.12)

        op_status = 0 if usage < 5 else (1 if usage < 12 else 2 if usage < 25 else 3)

        payload = {
            "deviceCode": self.device_code,
            "usageKwh": round(clamp(usage, 2.5, 38.0), 2),
            "co2Emission": round(usage * 0.0075, 2),
            "nsm": furnace_payload["nsm"],
            "weekStatus": furnace_payload["weekStatus"],
            "dayOfWeek": furnace_payload["dayOfWeek"],
            "loadType": "COOLING_SUPPORT",
            "xianPriceTier": furnace_payload["xianPriceTier"],
            "temperature": round(clamp(self.temperature, 25, 92), 2),
            "vibration": round(clamp(self.vibration, 0.2, 7.5), 2),
            "pressure": round(clamp(self.pressure, 86, 178), 2),
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
        furnace_status = furnace_payload["operatingStatus"]
        furnace_load = furnace_payload["usageKwh"]
        load_index = clamp(furnace_load / 145, 0.0, 1.0)

        usage_target = 5 + 34 * load_index + self.random.uniform(-0.8, 0.8)
        temp_target = 42 + 52 * load_index + self.random.uniform(-0.8, 0.8)
        pressure_target = 114 + 38 * load_index + self.random.uniform(-0.8, 0.8)

        self.air_health = clamp(self.air_health + 0.001 - 0.0009 * load_index, 0.85, 1.0)
        vibration_target = 1.0 + 4.6 * load_index + 1.8 * (1 - self.air_health) + self.random.uniform(-0.08, 0.10)

        # 空压机响应速度介于电弧炉和水泵之间
        self.temperature = lerp(self.temperature, temp_target, 0.08)
        self.vibration = lerp(self.vibration, vibration_target, 0.15)
        self.pressure = lerp(self.pressure, pressure_target, 0.10)

        op_status = 0 if usage_target < 6 else (1 if usage_target < 15 else 2 if usage_target < 34 else 3)

        payload = {
            "deviceCode": self.device_code,
            "usageKwh": round(clamp(usage_target, 4.0, 46.0), 2),
            "co2Emission": round(usage_target * 0.006, 2),
            "nsm": furnace_payload["nsm"],
            "weekStatus": furnace_payload["weekStatus"],
            "dayOfWeek": furnace_payload["dayOfWeek"],
            "loadType": "AIR_SUPPLY",
            "xianPriceTier": furnace_payload["xianPriceTier"],
            "temperature": round(clamp(self.temperature, 36, 108), 2),
            "vibration": round(clamp(self.vibration, 0.4, 7.8), 2),
            "pressure": round(clamp(self.pressure, 108, 162), 2),
            "operatingStatus": op_status,
            "time": simulated_time.isoformat(),
        }
        return self.apply_sensor_drift(payload)


class LadleFurnaceSimulator(DeviceSimulator):
    """钢包精炼炉 — 电弧炉出钢后在钢包中进行合金化、脱硫、调温。
    钢水从 EAF 出钢后转运至 LF，通过电极加热和氩气搅拌完成精炼。
    精炼周期约 30-50 分钟，温度控制在 1550-1650°C。"""

    def __init__(self, device_code, device_name, seed, fault_rate=DEFAULT_FAULT_RATE):
        super().__init__(device_code, device_name, seed, fault_rate)
        self.electrode_health = 1.0

    def build_payload(self, furnace_payload, simulated_time):
        self.tick_sensor_drift()
        furnace_stage = furnace_payload["loadType"]
        furnace_temp = furnace_payload["temperature"]

        # LF 仅在电弧炉出钢后（温度 > 900°C 且处于 HIGH_LOAD 恢复期）进入活跃状态
        lf_active = furnace_temp > 900 and furnace_stage in ("RUNNING", "HIGH_LOAD")
        demand = clamp((furnace_temp - 900) / 250, 0.0, 1.0) if lf_active else 0.0

        usage = 6.0 + 22.0 * demand + self.random.uniform(-0.5, 0.5)
        temp_target = 1480 + 180 * demand + self.random.uniform(-4.0, 4.0)
        self.electrode_health = clamp(self.electrode_health + 0.0006 - 0.0003 * demand, 0.88, 1.0)
        vibration_target = 0.4 + 2.6 * demand + 1.6 * (1 - self.electrode_health) + self.random.uniform(-0.06, 0.08)
        pressure_target = 90 + 22 * demand + self.random.uniform(-1.0, 1.0)

        self.temperature = lerp(self.temperature, temp_target, 0.05)
        self.vibration = lerp(self.vibration, vibration_target, 0.10)
        self.pressure = lerp(self.pressure, pressure_target, 0.08)

        op_status = 0 if demand < 0.05 else (1 if demand < 0.3 else 2 if demand < 0.7 else 3)

        payload = {
            "deviceCode": self.device_code,
            "usageKwh": round(clamp(usage, 4.0, 32.0), 2),
            "co2Emission": round(usage * 0.012, 2),
            "nsm": furnace_payload["nsm"],
            "weekStatus": furnace_payload["weekStatus"],
            "dayOfWeek": furnace_payload["dayOfWeek"],
            "loadType": "REFINING",
            "xianPriceTier": furnace_payload["xianPriceTier"],
            "temperature": round(clamp(self.temperature, 1450, 1680), 2),
            "vibration": round(clamp(self.vibration, 0.2, 4.8), 2),
            "pressure": round(clamp(self.pressure, 78, 132), 2),
            "operatingStatus": op_status,
            "time": simulated_time.isoformat(),
        }
        return self.apply_sensor_drift(payload)


class ContinuousCasterSimulator(DeviceSimulator):
    """连铸机 — 将精炼后的钢水连续浇铸成钢坯。
    拉速和冷却水量是关键参数。结晶器温度过高会导致漏钢事故。"""

    def __init__(self, device_code, device_name, seed, fault_rate=DEFAULT_FAULT_RATE):
        super().__init__(device_code, device_name, seed, fault_rate)
        self.mold_health = 1.0

    def build_payload(self, furnace_payload, simulated_time):
        self.tick_sensor_drift()
        furnace_temp = furnace_payload["temperature"]
        furnace_usage = furnace_payload["usageKwh"]

        # 连铸在电弧炉高负荷 / 精炼活跃时联动
        casting_active = furnace_temp > 850 and furnace_usage > 20
        demand = clamp((furnace_temp - 850) / 300, 0.0, 1.0) if casting_active else 0.0

        usage = 8.0 + 38.0 * demand + self.random.uniform(-0.8, 0.8)
        temp_target = 720 + 480 * demand + self.random.uniform(-3.0, 3.0)
        self.mold_health = clamp(self.mold_health + 0.0004 - 0.0005 * demand, 0.82, 1.0)
        vibration_target = 0.5 + 2.4 * demand + 1.2 * (1 - self.mold_health) + self.random.uniform(-0.05, 0.06)
        pressure_target = 95 + 35 * demand + self.random.uniform(-0.8, 0.8)

        self.temperature = lerp(self.temperature, temp_target, 0.06)
        self.vibration = lerp(self.vibration, vibration_target, 0.10)
        self.pressure = lerp(self.pressure, pressure_target, 0.08)

        op_status = 0 if demand < 0.08 else (1 if demand < 0.3 else 2 if demand < 0.7 else 3)

        payload = {
            "deviceCode": self.device_code,
            "usageKwh": round(clamp(usage, 5.0, 52.0), 2),
            "co2Emission": round(usage * 0.010, 2),
            "nsm": furnace_payload["nsm"],
            "weekStatus": furnace_payload["weekStatus"],
            "dayOfWeek": furnace_payload["dayOfWeek"],
            "loadType": "CASTING",
            "xianPriceTier": furnace_payload["xianPriceTier"],
            "temperature": round(clamp(self.temperature, 680, 1280), 2),
            "vibration": round(clamp(self.vibration, 0.2, 4.5), 2),
            "pressure": round(clamp(self.pressure, 82, 146), 2),
            "operatingStatus": op_status,
            "time": simulated_time.isoformat(),
        }
        return self.apply_sensor_drift(payload)


class DustCollectorSimulator(DeviceSimulator):
    """除尘系统 — 捕集电弧炉冶炼产生的烟尘。
    风量和压差反映除尘效率，粉尘浓度是环保合规关键指标。"""

    def __init__(self, device_code, device_name, seed, fault_rate=DEFAULT_FAULT_RATE):
        super().__init__(device_code, device_name, seed, fault_rate)
        self.filter_health = 1.0

    def build_payload(self, furnace_payload, simulated_time):
        self.tick_sensor_drift()
        furnace_usage = furnace_payload["usageKwh"]
        furnace_stage = furnace_payload["loadType"]

        # 除尘系统在电弧炉运行时必须运行（环保要求）
        dc_active = furnace_usage > 5 or furnace_stage in ("RUNNING", "HIGH_LOAD")
        demand = clamp(furnace_usage / 120, 0.0, 1.0) if dc_active else 0.1

        usage = 3.0 + 18.0 * demand + self.random.uniform(-0.3, 0.3)
        self.filter_health = clamp(self.filter_health + 0.0002 - 0.0008 * demand, 0.78, 1.0)
        # 压差随滤袋堵塞程度升高
        pressure_diff = 0.8 + 2.8 * demand + 3.5 * (1 - self.filter_health) + self.random.uniform(-0.08, 0.10)
        temp_target = 55 + 38 * demand + self.random.uniform(-1.5, 1.5)
        vibration_target = 0.4 + 3.5 * demand + 1.2 * (1 - self.filter_health) + self.random.uniform(-0.05, 0.06)

        self.temperature = lerp(self.temperature, temp_target, 0.06)
        self.vibration = lerp(self.vibration, vibration_target, 0.10)
        self.pressure = lerp(self.pressure, pressure_diff, 0.08)

        op_status = 1 if dc_active else 0

        payload = {
            "deviceCode": self.device_code,
            "usageKwh": round(clamp(usage, 2.0, 24.0), 2),
            "co2Emission": round(0.0, 2),
            "nsm": furnace_payload["nsm"],
            "weekStatus": furnace_payload["weekStatus"],
            "dayOfWeek": furnace_payload["dayOfWeek"],
            "loadType": "DUST_COLLECTION",
            "xianPriceTier": furnace_payload["xianPriceTier"],
            "temperature": round(clamp(self.temperature, 38, 118), 2),
            "vibration": round(clamp(self.vibration, 0.2, 5.5), 2),
            "pressure": round(clamp(self.pressure, 0.6, 7.2), 2),
            "operatingStatus": op_status,
            "time": simulated_time.isoformat(),
        }
        return self.apply_sensor_drift(payload)


def send_payload(payload):
    # ★ NH2: 上传必须携带 X-Api-Key,服务端 SensorApiKeyFilter 会校验
    headers = {"X-Api-Key": API_KEY} if API_KEY else {}
    response = requests.post(API_URL, json=payload, headers=headers, timeout=8)
    response.raise_for_status()


def send_cycle(furnace_sim, pump_sim, compressor_sim, ladle_sim, caster_sim, dust_sim, row, simulated_time, delay):
    # 全厂 6 台设备联动 — 电弧炉是主工艺设备，其余 5 台从电弧炉状态派生。
    # 生产流程: EAF(熔化) → LF(精炼) → CC(连铸) + PUMP(冷却) + COMP(压缩空气) + DC(除尘)
    furnace_payload = furnace_sim.build_payload(row, simulated_time)
    pump_payload = pump_sim.build_payload(furnace_payload, simulated_time)
    compressor_payload = compressor_sim.build_payload(furnace_payload, simulated_time)
    ladle_payload = ladle_sim.build_payload(furnace_payload, simulated_time)
    caster_payload = caster_sim.build_payload(furnace_payload, simulated_time)
    dust_payload = dust_sim.build_payload(furnace_payload, simulated_time)

    payloads = [furnace_payload, pump_payload, compressor_payload, ladle_payload, caster_payload, dust_payload]
    now_time = datetime.now().strftime("%H:%M:%S")

    try:
        send_payload(payloads)
        for payload in payloads:
            print(
                f"[{now_time}] sent {payload['deviceCode']:<8} "
                f"load={payload['usageKwh']:>6}kWh status={payload['operatingStatus']} "
                f"temp={payload['temperature']:>7} pressure={payload['pressure']:>6} "
                f"tier={payload['xianPriceTier']}"
            )
    except Exception as exc:
        for payload in payloads:
            print(f"[{now_time}] failed {payload['deviceCode']}: {exc}")

    for simulator in (furnace_sim, pump_sim, compressor_sim, ladle_sim, caster_sim, dust_sim):
        faults = simulator.active_faults()
        if faults:
            print(f"   active anomaly on {simulator.device_code}: {', '.join(faults)}")

    time.sleep(delay)


def parse_args():
    parser = argparse.ArgumentParser(description="SmartEnergyMaster plant simulator data pump")
    parser.add_argument("--interval", type=float, default=DEFAULT_SLEEP_INTERVAL,
                        help="live push interval in seconds, default: 3")
    parser.add_argument("--fault-rate", type=float, default=DEFAULT_FAULT_RATE,
                        help="base anomaly probability per furnace sample, default: 0.012")
    parser.add_argument("--history-hours", type=float, default=DEFAULT_HISTORY_HOURS,
                        help="history backfill window in hours, default: 24")
    return parser.parse_args()


def main():
    args = parse_args()
    live_interval = max(0.2, args.interval)
    history_hours = max(0.25, args.history_hours)
    backfill_points = max(1, int(round(history_hours * 60 / HISTORY_STEP_MINUTES)))
    fault_rate = clamp(args.fault_rate, 0.0, 1.0)

    df = load_dataset()
    print(f"[dataset] rows={len(df)}")
    print(
        f"[config] interval={live_interval}s "
        f"fault_rate={fault_rate} history_hours={history_hours} "
        f"backfill_points={backfill_points}"
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
    print(f"[回填] 正在回填 {history_hours:g} 小时历史数据，使前端图表展示完整趋势")
    for offset, (_, row) in enumerate(backfill_slice.iterrows()):
        simulated_time = backfill_start_time + timedelta(minutes=HISTORY_STEP_MINUTES * offset)
        send_cycle(furnace_sim, pump_sim, compressor_sim, ladle_sim, caster_sim, dust_sim, row, simulated_time, BACKFILL_SLEEP)

    print("[实时] 切换到实时推送模式")
    while True:
        row = df.iloc[live_index % len(df)]
        simulated_time = datetime.now(SHANGHAI_TZ)
        send_cycle(furnace_sim, pump_sim, compressor_sim, ladle_sim, caster_sim, dust_sim, row, simulated_time, live_interval)
        live_index += 1


if __name__ == "__main__":
    main()
