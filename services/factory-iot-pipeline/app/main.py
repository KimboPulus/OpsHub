import asyncio
import os
from contextlib import asynccontextmanager
from datetime import date, datetime, time, timezone
from io import StringIO

import pandas as pd
from fastapi import Depends, FastAPI, HTTPException
from fastapi.responses import PlainTextResponse
from sqlalchemy import select
from sqlalchemy.orm import Session, joinedload

from app.analytics import daily_oee, detect_anomalies, shift_for
from app.db import get_db, init_database, SessionLocal
from app.models import Alert, Anomaly, Machine, Telemetry
from app.schemas import AlertOut, AnomalyOut, MachineOut, TelemetryIn, TelemetryOut
from app.simulator import DEMO_MACHINES, generate_reading


@asynccontextmanager
async def lifespan(_app: FastAPI):
    init_database()
    seed_machines()
    if os.getenv("SIMULATOR_ON_START", "false").lower() == "true":
        asyncio.create_task(background_simulator())
    yield


app = FastAPI(title="Factory IoT Sensor Pipeline", version="0.1.0", lifespan=lifespan)


def seed_machines():
    with SessionLocal() as db:
        if db.scalar(select(Machine).limit(1)):
            return
        db.add_all(Machine(**item) for item in DEMO_MACHINES)
        db.commit()


async def background_simulator():
    interval = float(os.getenv("SIMULATOR_INTERVAL_SECONDS", "2"))
    while True:
        with SessionLocal() as db:
            create_simulated_batch(db)
        await asyncio.sleep(interval)


@app.get("/health")
def health():
    return {"status": "ok", "service": "factory-iot-sensor-pipeline"}


@app.get("/machines", response_model=list[MachineOut])
def machines(db: Session = Depends(get_db)):
    return db.scalars(select(Machine).order_by(Machine.code)).all()


@app.post("/telemetry", response_model=TelemetryOut)
def ingest_telemetry(payload: TelemetryIn, db: Session = Depends(get_db)):
    machine = db.get(Machine, payload.machine_id)
    if not machine:
        raise HTTPException(status_code=404, detail="Nie znaleziono maszyny")

    timestamp = payload.timestamp or datetime.now(timezone.utc)
    row = Telemetry(
        machine_id=payload.machine_id,
        timestamp=timestamp,
        vibration=payload.vibration,
        temperature=payload.temperature,
        cycle_time=payload.cycle_time,
        energy_kwh=payload.energy_kwh,
        produced_units=payload.produced_units,
        status=payload.status.upper(),
        error_code=payload.error_code,
        shift=payload.shift or shift_for(timestamp),
    )
    db.add(row)
    db.flush()

    for item in detect_anomalies(row, machine):
        anomaly = Anomaly(
            machine_id=row.machine_id,
            telemetry_id=row.id,
            timestamp=row.timestamp,
            **item,
        )
        db.add(anomaly)
        db.flush()

        if item["severity"] == "critical":
            db.add(Alert(
                machine_id=row.machine_id,
                anomaly_id=anomaly.id,
                severity=item["severity"],
                title=f"{machine.code}: alert telemetryczny",
                message=item["message"],
            ))

    db.commit()
    db.refresh(row)
    return row


@app.post("/simulator/tick")
def simulator_tick(count: int = 1, db: Session = Depends(get_db)):
    created = 0
    for _ in range(max(1, min(count, 200))):
        created += create_simulated_batch(db)
    return {"created": created}


def create_simulated_batch(db: Session) -> int:
    machines = db.scalars(select(Machine).order_by(Machine.code)).all()
    created = 0
    for machine in machines:
        payload = TelemetryIn(**generate_reading(machine.id))
        ingest_telemetry(payload, db)
        created += 1
    return created


@app.get("/machines/{machine_id}/telemetry", response_model=list[TelemetryOut])
def machine_telemetry(machine_id: int, limit: int = 100, db: Session = Depends(get_db)):
    limit = max(1, min(limit, 500))
    return db.scalars(
        select(Telemetry)
        .where(Telemetry.machine_id == machine_id)
        .order_by(Telemetry.timestamp.desc())
        .limit(limit)
    ).all()


@app.get("/machines/{machine_id}/anomalies", response_model=list[AnomalyOut])
def machine_anomalies(machine_id: int, open_only: bool = False, db: Session = Depends(get_db)):
    query = select(Anomaly).where(Anomaly.machine_id == machine_id)
    if open_only:
        query = query.where(Anomaly.open.is_(True))
    return db.scalars(query.order_by(Anomaly.timestamp.desc()).limit(100)).all()


@app.get("/alerts/open", response_model=list[AlertOut])
def open_alerts(db: Session = Depends(get_db)):
    return db.scalars(
        select(Alert)
        .where(Alert.open.is_(True))
        .order_by(Alert.created_at.desc())
        .limit(100)
    ).all()


@app.get("/oee/daily")
def oee_daily(day: date | None = None, db: Session = Depends(get_db)):
    day = day or datetime.now(timezone.utc).date()
    start = datetime.combine(day, time.min, tzinfo=timezone.utc)
    end = datetime.combine(day, time.max, tzinfo=timezone.utc)
    rows = db.scalars(
        select(Telemetry)
        .options(joinedload(Telemetry.machine))
        .where(Telemetry.timestamp >= start, Telemetry.timestamp <= end)
        .order_by(Telemetry.timestamp)
    ).all()
    return {"date": day.isoformat(), **daily_oee(rows)}


@app.get("/dashboard/summary")
def dashboard_summary(db: Session = Depends(get_db)):
    oee = oee_daily(db=db)
    latest_rows = db.scalars(
        select(Telemetry)
        .options(joinedload(Telemetry.machine))
        .order_by(Telemetry.timestamp.desc())
        .limit(20)
    ).all()
    anomalies = db.scalars(
        select(Anomaly).order_by(Anomaly.timestamp.desc()).limit(10)
    ).all()
    alerts = open_alerts(db)
    return {
        "oee": oee,
        "latestTelemetry": [{
            "id": row.id,
            "machine_id": row.machine_id,
            "machine_code": row.machine.code,
            "timestamp": row.timestamp,
            "vibration": row.vibration,
            "temperature": row.temperature,
            "cycle_time": row.cycle_time,
            "energy_kwh": row.energy_kwh,
            "produced_units": row.produced_units,
            "status": row.status,
            "error_code": row.error_code,
            "shift": row.shift,
        } for row in latest_rows],
        "recentAnomalies": [{
            "id": row.id,
            "machine_id": row.machine_id,
            "telemetry_id": row.telemetry_id,
            "timestamp": row.timestamp,
            "kind": row.kind,
            "severity": row.severity,
            "value": row.value,
            "threshold": row.threshold,
            "message": row.message,
            "open": row.open,
        } for row in anomalies],
        "openAlerts": [{
            "id": row.id,
            "machine_id": row.machine_id,
            "anomaly_id": row.anomaly_id,
            "created_at": row.created_at,
            "severity": row.severity,
            "title": row.title,
            "message": row.message,
            "open": row.open,
        } for row in alerts],
    }


@app.get("/exports/powerbi.csv", response_class=PlainTextResponse)
def powerbi_csv(db: Session = Depends(get_db)):
    rows = db.scalars(
        select(Telemetry)
        .options(joinedload(Telemetry.machine))
        .order_by(Telemetry.timestamp.desc())
        .limit(5000)
    ).all()
    data = [{
        "timestamp": row.timestamp.isoformat(),
        "machine_code": row.machine.code,
        "line": row.machine.line,
        "vibration": row.vibration,
        "temperature": row.temperature,
        "cycle_time": row.cycle_time,
        "energy_kwh": row.energy_kwh,
        "produced_units": row.produced_units,
        "status": row.status,
        "error_code": row.error_code or "",
        "shift": row.shift,
    } for row in rows]
    buffer = StringIO()
    pd.DataFrame(data).to_csv(buffer, index=False)
    return buffer.getvalue()
