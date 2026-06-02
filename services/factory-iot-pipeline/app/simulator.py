import random
from datetime import datetime, timezone

from app.analytics import shift_for


DEMO_MACHINES = [
    {"code": "WLD-01", "name": "Welding robot cell", "line": "Welding", "target_cycle_time": 38.0, "ideal_energy_per_unit": 1.35},
    {"code": "PRS-02", "name": "Hydraulic press", "line": "Forming", "target_cycle_time": 31.0, "ideal_energy_per_unit": 1.7},
    {"code": "CNC-03", "name": "CNC machining center", "line": "Machining", "target_cycle_time": 46.0, "ideal_energy_per_unit": 1.55},
    {"code": "ASM-04", "name": "Final assembly station", "line": "Assembly", "target_cycle_time": 52.0, "ideal_energy_per_unit": 0.9},
]

ERROR_CODES = ["E_STOP", "OVERHEAT", "TOOL_WEAR", "PART_JAM", "SENSOR_LOSS"]


def generate_reading(machine_id: int, timestamp: datetime | None = None) -> dict:
    timestamp = timestamp or datetime.now(timezone.utc)
    anomaly = random.random() < 0.09
    down = random.random() < 0.04
    idle = not down and random.random() < 0.08

    if down:
        status = "DOWN"
    elif idle:
        status = "IDLE"
    else:
        status = "RUNNING"

    produced_units = 0 if status != "RUNNING" else random.randint(1, 4)
    error_code = random.choice(ERROR_CODES) if down or (anomaly and random.random() < 0.35) else None

    return {
        "machine_id": machine_id,
        "timestamp": timestamp,
        "vibration": round(random.uniform(2.2, 5.8) + (random.uniform(2.5, 4.5) if anomaly else 0), 2),
        "temperature": round(random.uniform(48, 74) + (random.uniform(12, 28) if anomaly else 0), 1),
        "cycle_time": round(random.uniform(30, 58) + (random.uniform(10, 25) if idle or anomaly else 0), 1),
        "energy_kwh": round(random.uniform(1.2, 7.5) + (random.uniform(2.5, 4.0) if anomaly else 0), 2),
        "produced_units": produced_units,
        "status": status,
        "error_code": error_code,
        "shift": shift_for(timestamp),
    }
