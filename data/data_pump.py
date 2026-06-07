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

API_URL = "http://localhost:8080/api/sensor/upload"
SLEEP_INTERVAL = 3
BACKFILL_POINTS = 96
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
    def __init__(self, device_code, device_name, seed):
        self.device_code = device_code
        self.device_name = device_name
        self.random = random.Random(seed)
        self.temperature = 80.0
        self.vibration = 1.0
        self.pressure = 120.0
        self.fault_mode = None
        self.fault_remaining = 0

    def maybe_start_fault(self, stage):
        if self.fault_mode is not None:
            self.fault_remaining -= 1
            if self.fault_remaining <= 0:
                self.fault_mode = None
            return

        chance = self.random.random()
        if self.device_code == "EAF-01" and stage == "IDLE" and chance < 0.009:
            self.fault_mode = "MECHANICAL_JAM"
            self.fault_remaining = self.random.randint(3, 5)
        elif self.device_code == "EAF-01" and stage in {"RUNNING", "HIGH_LOAD"} and chance < 0.006:
            self.fault_mode = "COOLING_INTERRUPT"
            self.fault_remaining = self.random.randint(2, 4)

    def build_payload(self, row, simulated_time):
        raise NotImplementedError


class FurnaceSimulator(DeviceSimulator):
    def __init__(self, device_code, device_name, seed):
        super().__init__(device_code, device_name, seed)
        self.hearth_heat = 0.25
        self.cooling_health = 1.0
        self.bearing_health = 1.0

    def build_payload(self, row, simulated_time):
        usage_raw = float(row["Usage_kWh"])
        co2_raw = max(0.0, float(row["CO2(tCO2)"]))
        nsm = int(row["NSM"])
        week_status_text = str(row["WeekStatus"]).strip()
        week_status = 1 if week_status_text == "Weekday" else 0
        hour = nsm // 3600
        lagging_reactive = float(row["Lagging_Current_Reactive.Power_kVarh"])
        power_factor = float(row["Lagging_Current_Power_Factor"])
        day_of_week = str(row["Day_of_week"])

        stage = infer_stage(usage_raw, power_factor, week_status_text, hour)
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
        elif self.fault_mode == "COOLING_INTERRUPT":
            stage = "HIGH_LOAD"
            self.cooling_health = clamp(self.cooling_health - 0.18, 0.35, 1.0)

        usage = {
            "STOPPED": clamp(usage_raw * 0.25 + self.random.uniform(0.1, 0.8), 0.2, 2.0),
            "IDLE": clamp(usage_raw * 0.92 + self.random.uniform(-0.8, 1.2), 2.8, 11.5),
            "RUNNING": clamp(usage_raw * 1.03 + self.random.uniform(-2.5, 3.0), 12.0, 82.0),
            "HIGH_LOAD": clamp(usage_raw * 1.01 + self.random.uniform(-3.0, 4.0), 82.0, 148.0),
        }[stage]

        temperature_target = (
            60
            + 980 * self.hearth_heat
            + 36 * process_intensity
            + 28 * electrical_stress
            + self.random.uniform(-12, 12)
        )
        vibration_target = (
            0.8
            + 6.5 * process_intensity
            + 3.2 * reactive_ratio
            + 5.5 * (1 - self.bearing_health)
            + self.random.uniform(-0.35, 0.45)
        )
        pressure_target = (
            112
            + 38 * process_intensity
            - 16 * electrical_stress
            + 10 * self.cooling_health
            + self.random.uniform(-3.5, 3.5)
        )

        self.temperature = lerp(self.temperature, temperature_target, 0.24)
        self.vibration = lerp(self.vibration, vibration_target, 0.35)
        self.pressure = lerp(self.pressure, pressure_target, 0.30)

        if self.fault_mode == "MECHANICAL_JAM":
            self.temperature = lerp(self.temperature, 330 + self.random.uniform(-18, 25), 0.35)
            self.vibration = lerp(self.vibration, self.random.uniform(18.0, 24.5), 0.92)
            self.pressure = lerp(self.pressure, self.random.uniform(112, 125), 0.35)
        elif self.fault_mode == "COOLING_INTERRUPT":
            self.temperature = lerp(self.temperature, self.random.uniform(1040, 1165), 0.68)
            self.vibration = lerp(self.vibration, self.random.uniform(7.5, 10.8), 0.45)
            self.pressure = lerp(self.pressure, self.random.uniform(26, 42), 0.88)

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
        return payload


class PumpSimulator(DeviceSimulator):
    def __init__(self, device_code, device_name, seed):
        super().__init__(device_code, device_name, seed)
        self.flow_health = 1.0

    def build_payload(self, furnace_payload, simulated_time):
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
        temp_target = 34 + 42 * demand_index + self.random.uniform(-2.0, 2.0)
        vibration_target = 0.8 + 3.8 * demand_index + 2.0 * (1 - self.flow_health) + self.random.uniform(-0.2, 0.25)
        pressure_target = clamp(furnace_pressure + 9 + 12 * self.flow_health + self.random.uniform(-3, 3), 88, 176)

        self.temperature = lerp(self.temperature, temp_target, 0.38)
        self.vibration = lerp(self.vibration, vibration_target, 0.42)
        self.pressure = lerp(self.pressure, pressure_target, 0.44)

        op_status = 0 if usage < 5 else (1 if usage < 12 else 2 if usage < 25 else 3)

        return {
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


class CompressorSimulator(DeviceSimulator):
    def __init__(self, device_code, device_name, seed):
        super().__init__(device_code, device_name, seed)
        self.air_health = 1.0

    def build_payload(self, furnace_payload, simulated_time):
        furnace_status = furnace_payload["operatingStatus"]
        furnace_load = furnace_payload["usageKwh"]
        load_index = clamp(furnace_load / 145, 0.0, 1.0)

        usage_target = 5 + 34 * load_index + self.random.uniform(-2.5, 2.5)
        temp_target = 42 + 52 * load_index + self.random.uniform(-2.5, 2.5)
        pressure_target = 114 + 38 * load_index + self.random.uniform(-3.0, 3.0)

        self.air_health = clamp(self.air_health + 0.001 - 0.0009 * load_index, 0.85, 1.0)
        vibration_target = 1.0 + 4.6 * load_index + 1.8 * (1 - self.air_health) + self.random.uniform(-0.25, 0.35)

        self.temperature = lerp(self.temperature, temp_target, 0.35)
        self.vibration = lerp(self.vibration, vibration_target, 0.40)
        self.pressure = lerp(self.pressure, pressure_target, 0.42)

        op_status = 0 if usage_target < 6 else (1 if usage_target < 15 else 2 if usage_target < 34 else 3)

        return {
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


def send_payload(payload):
    response = requests.post(API_URL, json=payload, timeout=8)
    response.raise_for_status()


def send_cycle(furnace_sim, pump_sim, compressor_sim, row, simulated_time, delay):
    # Support equipment is derived from the furnace state so the entire plant
    # moves coherently on the dashboard instead of three unrelated random curves.
    furnace_payload = furnace_sim.build_payload(row, simulated_time)
    pump_payload = pump_sim.build_payload(furnace_payload, simulated_time)
    compressor_payload = compressor_sim.build_payload(furnace_payload, simulated_time)

    payloads = [furnace_payload, pump_payload, compressor_payload]
    now_time = datetime.now().strftime("%H:%M:%S")

    for payload in payloads:
        try:
            send_payload(payload)
            print(
                f"[{now_time}] sent {payload['deviceCode']:<8} "
                f"load={payload['usageKwh']:>6}kWh status={payload['operatingStatus']} "
                f"temp={payload['temperature']:>7} pressure={payload['pressure']:>6} "
                f"tier={payload['xianPriceTier']}"
            )
        except Exception as exc:
            print(f"[{now_time}] failed {payload['deviceCode']}: {exc}")

    if furnace_sim.fault_mode:
        print(f"   fault injected on {furnace_sim.device_code}: {furnace_sim.fault_mode}")

    time.sleep(delay)


def main():
    df = load_dataset()
    print(f"[dataset] rows={len(df)}")

    furnace_sim = FurnaceSimulator("EAF-01", "Electric Arc Furnace 01", 20260320)
    pump_sim = PumpSimulator("PUMP-01", "Cooling Pump 01", 20260321)
    compressor_sim = CompressorSimulator("COMP-01", "Air Compressor A", 20260322)

    start_index = random.randint(0, max(0, len(df) - BACKFILL_POINTS - 1))
    backfill_slice = df.iloc[start_index:start_index + BACKFILL_POINTS].reset_index(drop=True)
    live_index = start_index + BACKFILL_POINTS

    backfill_start_time = datetime.now(SHANGHAI_TZ) - timedelta(hours=24)
    print("[backfill] sending a 24h history window to make charts look reasonable")
    for offset, (_, row) in enumerate(backfill_slice.iterrows()):
        simulated_time = backfill_start_time + timedelta(minutes=15 * offset)
        send_cycle(furnace_sim, pump_sim, compressor_sim, row, simulated_time, BACKFILL_SLEEP)

    print("[live] switch to accelerated live mode")
    while True:
        row = df.iloc[live_index % len(df)]
        simulated_time = datetime.now(SHANGHAI_TZ)
        send_cycle(furnace_sim, pump_sim, compressor_sim, row, simulated_time, SLEEP_INTERVAL)
        live_index += 1


if __name__ == "__main__":
    main()
