from datetime import datetime

from pydantic import BaseModel, Field


class MachineOut(BaseModel):
    id: int
    code: str
    name: str
    line: str
    target_cycle_time: float
    ideal_energy_per_unit: float

    model_config = {"from_attributes": True}


class TelemetryIn(BaseModel):
    machine_id: int
    timestamp: datetime | None = None
    vibration: float = Field(ge=0)
    temperature: float
    cycle_time: float = Field(gt=0)
    energy_kwh: float = Field(ge=0)
    produced_units: int = Field(ge=0)
    status: str
    error_code: str | None = None
    shift: str | None = None


class TelemetryOut(TelemetryIn):
    id: int
    timestamp: datetime
    shift: str

    model_config = {"from_attributes": True}


class AnomalyOut(BaseModel):
    id: int
    machine_id: int
    telemetry_id: int
    timestamp: datetime
    kind: str
    severity: str
    value: float | None
    threshold: float | None
    message: str
    open: bool

    model_config = {"from_attributes": True}


class AlertOut(BaseModel):
    id: int
    machine_id: int
    anomaly_id: int | None
    created_at: datetime
    severity: str
    title: str
    message: str
    open: bool

    model_config = {"from_attributes": True}
