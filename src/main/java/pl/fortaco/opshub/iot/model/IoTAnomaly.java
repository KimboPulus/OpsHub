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
@Table(name = "iot_anomalies")
public class IoTAnomaly {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private IoTMachine machine;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private IoTTelemetry telemetry;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(nullable = false, length = 60)
    private String kind;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(name = "measured_value")
    private Double value;
    private Double threshold;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "is_open", nullable = false)
    private boolean open = true;

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

    public IoTTelemetry getTelemetry() {
        return telemetry;
    }

    public void setTelemetry(IoTTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }
}
