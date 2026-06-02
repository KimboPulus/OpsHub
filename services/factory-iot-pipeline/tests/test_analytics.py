from datetime import datetime, timezone
from types import SimpleNamespace

from app.analytics import daily_oee, detect_anomalies, shift_for


def test_shift_for():
    assert shift_for(datetime(2026, 1, 1, 7, 0, tzinfo=timezone.utc)) == "A"
    assert shift_for(datetime(2026, 1, 1, 15, 0, tzinfo=timezone.utc)) == "B"
    assert shift_for(datetime(2026, 1, 1, 23, 0, tzinfo=timezone.utc)) == "C"


def test_detects_temperature_and_vibration_anomalies():
    machine = SimpleNamespace(code="CNC-03", ideal_energy_per_unit=1.5)
    reading = SimpleNamespace(
        temperature=91,
        vibration=8.5,
        produced_units=2,
        energy_kwh=8,
        status="RUNNING",
        error_code=None,
    )

    kinds = {item["kind"] for item in detect_anomalies(reading, machine)}

    assert "temperature" in kinds
    assert "vibration" in kinds
    assert "energy" in kinds


def test_daily_oee_has_dashboard_fields():
    machine = SimpleNamespace(code="WLD-01", target_cycle_time=40, ideal_energy_per_unit=1.2, line="Welding")
    rows = [
        SimpleNamespace(machine=machine, status="RUNNING", cycle_time=42, energy_kwh=3, produced_units=2, error_code=None, shift="A"),
        SimpleNamespace(machine=machine, status="DOWN", cycle_time=60, energy_kwh=1, produced_units=0, error_code="OVERHEAT", shift="A"),
    ]

    result = daily_oee(rows)

    assert result["production_count"] == 2
    assert result["machine_uptime"][0]["machine"] == "WLD-01"
    assert result["downtime_causes"][0]["cause"] == "OVERHEAT"
