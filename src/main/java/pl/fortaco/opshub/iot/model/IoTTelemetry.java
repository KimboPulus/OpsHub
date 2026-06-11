package pl.fortaco.opshub.iot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "iot_telemetry")
public class IoTTelemetry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private IoTMachine machine;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    private double vibration;
    private double temperature;

    @Column(name = "cycle_time")
    private double cycleTime;

    @Column(name = "energy_kwh")
    private double energyKwh;

    @Column(name = "produced_units")
    private int producedUnits;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(nullable = false, length = 20)
    private String shiftName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public IoTMachine getMachine() {
        return machine;
    }

    public void setMachine(IoTMachine machine) {
        this.machine = machine;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }

    public double getVibration() {
        return vibration;
    }

    public void setVibration(double vibration) {
        this.vibration = vibration;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getCycleTime() {
        return cycleTime;
    }

    public void setCycleTime(double cycleTime) {
        this.cycleTime = cycleTime;
    }

    public double getEnergyKwh() {
        return energyKwh;
    }

    public void setEnergyKwh(double energyKwh) {
        this.energyKwh = energyKwh;
    }

    public int getProducedUnits() {
        return producedUnits;
    }

    public void setProducedUnits(int producedUnits) {
        this.producedUnits = producedUnits;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getShiftName() {
        return shiftName;
    }

    public void setShiftName(String shiftName) {
        this.shiftName = shiftName;
    }
}
