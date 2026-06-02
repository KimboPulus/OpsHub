from datetime import datetime, time

import pandas as pd


TEMP_WARNING = 78.0
TEMP_CRITICAL = 88.0
VIBRATION_WARNING = 6.5
VIBRATION_CRITICAL = 8.0
ENERGY_FACTOR_WARNING = 1.65


def shift_for(timestamp: datetime) -> str:
    local_time = timestamp.time()
    if time(6, 0) <= local_time < time(14, 0):
        return "A"
    if time(14, 0) <= local_time < time(22, 0):
        return "B"
    return "C"


def detect_anomalies(reading, machine) -> list[dict]:
    anomalies = []

    if reading.temperature >= TEMP_CRITICAL:
        anomalies.append({
            "kind": "temperature",
            "severity": "critical",
            "value": reading.temperature,
            "threshold": TEMP_CRITICAL,
            "message": f"{machine.code}: temperatura jest krytycznie wysoka ({reading.temperature:.1f} C).",
        })
    elif reading.temperature >= TEMP_WARNING:
        anomalies.append({
            "kind": "temperature",
            "severity": "warning",
            "value": reading.temperature,
            "threshold": TEMP_WARNING,
            "message": f"{machine.code}: temperatura jest powyżej normy ({reading.temperature:.1f} C).",
        })

    if reading.vibration >= VIBRATION_CRITICAL:
        anomalies.append({
            "kind": "vibration",
            "severity": "critical",
            "value": reading.vibration,
            "threshold": VIBRATION_CRITICAL,
            "message": f"{machine.code}: drgania wyglądają niebezpiecznie ({reading.vibration:.2f} mm/s).",
        })
    elif reading.vibration >= VIBRATION_WARNING:
        anomalies.append({
            "kind": "vibration",
            "severity": "warning",
            "value": reading.vibration,
            "threshold": VIBRATION_WARNING,
            "message": f"{machine.code}: drgania rosną ponad normalny poziom ({reading.vibration:.2f} mm/s).",
        })

    if reading.produced_units > 0:
        energy_per_unit = reading.energy_kwh / reading.produced_units
        threshold = machine.ideal_energy_per_unit * ENERGY_FACTOR_WARNING
        if energy_per_unit >= threshold:
            anomalies.append({
                "kind": "energy",
                "severity": "warning",
                "value": round(energy_per_unit, 3),
                "threshold": round(threshold, 3),
            "message": f"{machine.code}: zużycie energii na sztukę jest wysokie ({energy_per_unit:.2f} kWh/szt.).",
            })

    if reading.status == "DOWN" or reading.error_code:
        anomalies.append({
            "kind": "machine_status",
            "severity": "critical" if reading.status == "DOWN" else "warning",
            "value": None,
            "threshold": None,
            "message": f"{machine.code}: maszyna zgłosiła status {reading.status}"
                       + (f" i błąd {reading.error_code}." if reading.error_code else "."),
        })

    return anomalies


def daily_oee(rows) -> dict:
    if not rows:
        return {
            "availability": 0,
            "performance": 0,
            "quality": 100,
            "oee": 0,
            "production_count": 0,
            "energy_per_unit": 0,
            "machine_uptime": [],
            "production_by_shift": [],
            "downtime_causes": [],
        }

    data = [{
        "machine": row.machine.code,
        "target_cycle_time": row.machine.target_cycle_time,
        "status": row.status,
        "cycle_time": row.cycle_time,
        "energy_kwh": row.energy_kwh,
        "produced_units": row.produced_units,
        "error_code": row.error_code or "no_error",
        "shift": row.shift,
    } for row in rows]

    df = pd.DataFrame(data)
    running = df["status"].eq("RUNNING").sum()
    availability = running / len(df)

    produced = int(df["produced_units"].sum())
    avg_target = float(df["target_cycle_time"].mean())
    avg_cycle = float(df.loc[df["produced_units"] > 0, "cycle_time"].mean() or avg_target)
    performance = min(1.0, avg_target / max(avg_cycle, 1))

    error_rows = df["error_code"].ne("no_error").sum()
    quality = max(0.0, 1 - (error_rows / max(len(df), 1)) * 0.5)
    oee = availability * performance * quality
    energy_per_unit = float(df["energy_kwh"].sum() / produced) if produced else 0.0

    uptime = (
        df.assign(running=df["status"].eq("RUNNING"))
        .groupby("machine")["running"]
        .mean()
        .reset_index()
        .sort_values("running", ascending=False)
    )

    by_shift = (
        df.groupby("shift")["produced_units"]
        .sum()
        .reset_index()
        .sort_values("shift")
    )

    downtime = (
        df.loc[df["status"].ne("RUNNING")]
        .groupby("error_code")
        .size()
        .reset_index(name="count")
        .sort_values("count", ascending=False)
    )

    return {
        "availability": round(availability * 100, 1),
        "performance": round(performance * 100, 1),
        "quality": round(quality * 100, 1),
        "oee": round(oee * 100, 1),
        "production_count": produced,
        "energy_per_unit": round(energy_per_unit, 3),
        "machine_uptime": [
            {"machine": row.machine, "uptime": round(row.running * 100, 1)}
            for row in uptime.itertuples(index=False)
        ],
        "production_by_shift": [
            {"shift": row.shift, "produced_units": int(row.produced_units)}
            for row in by_shift.itertuples(index=False)
        ],
        "downtime_causes": [
            {"cause": row.error_code, "count": int(row.count)}
            for row in downtime.itertuples(index=False)
        ],
    }
