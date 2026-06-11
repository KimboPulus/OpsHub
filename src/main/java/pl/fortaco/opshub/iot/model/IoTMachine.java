package pl.fortaco.opshub.iot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "iot_machines")
public class IoTMachine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "line_name", nullable = false, length = 80)
    private String line;

    @Column(name = "target_cycle_time", nullable = false)
    private double targetCycleTime;

    @Column(name = "ideal_energy_per_unit", nullable = false)
    private double idealEnergyPerUnit;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLine() {
        return line;
    }

    public void setLine(String line) {
        this.line = line;
    }

    public double getTargetCycleTime() {
        return targetCycleTime;
    }

    public void setTargetCycleTime(double targetCycleTime) {
        this.targetCycleTime = targetCycleTime;
    }

    public double getIdealEnergyPerUnit() {
        return idealEnergyPerUnit;
    }

    public void setIdealEnergyPerUnit(double idealEnergyPerUnit) {
        this.idealEnergyPerUnit = idealEnergyPerUnit;
    }
}
