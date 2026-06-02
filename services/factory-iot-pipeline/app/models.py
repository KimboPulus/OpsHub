from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db import Base


def utc_now():
    return datetime.now(timezone.utc)


class Machine(Base):
    __tablename__ = "machines"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    code: Mapped[str] = mapped_column(String(30), unique=True, index=True)
    name: Mapped[str] = mapped_column(String(120))
    line: Mapped[str] = mapped_column(String(80))
    target_cycle_time: Mapped[float] = mapped_column(Float, default=42.0)
    ideal_energy_per_unit: Mapped[float] = mapped_column(Float, default=1.2)

    telemetry: Mapped[list["Telemetry"]] = relationship(back_populates="machine")


class Telemetry(Base):
    __tablename__ = "telemetry"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    machine_id: Mapped[int] = mapped_column(ForeignKey("machines.id"), index=True)
    timestamp: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    vibration: Mapped[float] = mapped_column(Float)
    temperature: Mapped[float] = mapped_column(Float)
    cycle_time: Mapped[float] = mapped_column(Float)
    energy_kwh: Mapped[float] = mapped_column(Float)
    produced_units: Mapped[int] = mapped_column(Integer)
    status: Mapped[str] = mapped_column(String(30), index=True)
    error_code: Mapped[str | None] = mapped_column(String(40), nullable=True)
    shift: Mapped[str] = mapped_column(String(20), index=True)

    machine: Mapped[Machine] = relationship(back_populates="telemetry")


class Anomaly(Base):
    __tablename__ = "anomalies"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    machine_id: Mapped[int] = mapped_column(ForeignKey("machines.id"), index=True)
    telemetry_id: Mapped[int] = mapped_column(ForeignKey("telemetry.id"), index=True)
    timestamp: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    kind: Mapped[str] = mapped_column(String(60))
    severity: Mapped[str] = mapped_column(String(20))
    value: Mapped[float | None] = mapped_column(Float, nullable=True)
    threshold: Mapped[float | None] = mapped_column(Float, nullable=True)
    message: Mapped[str] = mapped_column(Text)
    open: Mapped[bool] = mapped_column(Boolean, default=True)


class Alert(Base):
    __tablename__ = "alerts"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    machine_id: Mapped[int] = mapped_column(ForeignKey("machines.id"), index=True)
    anomaly_id: Mapped[int | None] = mapped_column(ForeignKey("anomalies.id"), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    severity: Mapped[str] = mapped_column(String(20))
    title: Mapped[str] = mapped_column(String(160))
    message: Mapped[str] = mapped_column(Text)
    open: Mapped[bool] = mapped_column(Boolean, default=True)
