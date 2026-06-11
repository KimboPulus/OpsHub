package pl.fortaco.opshub.iot.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.fortaco.opshub.iot.model.IoTAlert;
import pl.fortaco.opshub.iot.model.IoTAnomaly;
import pl.fortaco.opshub.iot.model.IoTMachine;
import pl.fortaco.opshub.iot.model.IoTTelemetry;
import pl.fortaco.opshub.iot.repository.IoTAlertRepository;
import pl.fortaco.opshub.iot.repository.IoTAnomalyRepository;
import pl.fortaco.opshub.iot.repository.IoTMachineRepository;
import pl.fortaco.opshub.iot.repository.IoTTelemetryRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class IoTDataService {
    private final IoTMachineRepository machines;
    private final IoTTelemetryRepository telemetry;
    private final IoTAnomalyRepository anomalies;
    private final IoTAlertRepository alerts;
    private final IoTSimulator simulator;

    public IoTDataService(
        IoTMachineRepository machines,
        IoTTelemetryRepository telemetry,
        IoTAnomalyRepository anomalies,
        IoTAlertRepository alerts,
        IoTSimulator simulator) {
        this.machines = machines;
        this.telemetry = telemetry;
        this.anomalies = anomalies;
        this.alerts = alerts;
        this.simulator = simulator;
    }

    public List<Map<String, Object>> machines() {
        return machines.findAllByOrderByCodeAsc()
            .stream()
            .map(this::machineMap)
            .toList();
    }

    @Transactional
    public Map<String, Object> ingest(TelemetryCommand command) {
        IoTMachine machine = findMachine(command.machineId());
        IoTTelemetry row = new IoTTelemetry();
        row.setMachine(machine);
        row.setRecordedAt(command.timestamp() == null ? Instant.now() : command.timestamp());
        row.setVibration(command.vibration());
        row.setTemperature(command.temperature());
        row.setCycleTime(command.cycleTime());
        row.setEnergyKwh(command.energyKwh());
        row.setProducedUnits(command.producedUnits());
        row.setStatus(command.status().trim().toUpperCase());
        row.setErrorCode(normalize(command.errorCode()));
        row.setShiftName(command.shift() == null || command.shift().isBlank()
            ? IoTAnalytics.shiftFor(row.getRecordedAt())
            : command.shift().trim().toUpperCase());
        telemetry.save(row);

        for (IoTAnalytics.AnomalyCandidate candidate : IoTAnalytics.detectAnomalies(row, machine)) {
            IoTAnomaly anomaly = new IoTAnomaly();
            anomaly.setMachine(machine);
            anomaly.setTelemetry(row);
            anomaly.setRecordedAt(row.getRecordedAt());
            anomaly.setKind(candidate.kind());
            anomaly.setSeverity(candidate.severity());
            anomaly.setValue(candidate.value());
            anomaly.setThreshold(candidate.threshold());
            anomaly.setMessage(candidate.message());
            anomaly.setOpen(true);
            anomalies.save(anomaly);

            if ("critical".equals(candidate.severity())) {
                IoTAlert alert = new IoTAlert();
                alert.setMachine(machine);
                alert.setAnomaly(anomaly);
                alert.setCreatedAt(Instant.now());
                alert.setSeverity(candidate.severity());
                alert.setTitle(machine.getCode() + ": alert telemetryczny");
                alert.setMessage(candidate.message());
                alert.setOpen(true);
                alerts.save(alert);
            }
        }

        return telemetryMap(row);
    }

    @Transactional
    public Map<String, Object> simulate(int requestedCount) {
        int count = Math.max(1, Math.min(requestedCount, 200));
        List<IoTMachine> machineList = machines.findAllByOrderByCodeAsc();
        int created = 0;

        for (int tick = 0; tick < count; tick++) {
            Instant timestamp = Instant.now();
            for (IoTMachine machine : machineList) {
                IoTSimulator.GeneratedReading reading = simulator.generate(machine, timestamp);
                ingest(new TelemetryCommand(
                    reading.machineId(),
                    reading.timestamp(),
                    reading.vibration(),
                    reading.temperature(),
                    reading.cycleTime(),
                    reading.energyKwh(),
                    reading.producedUnits(),
                    reading.status(),
                    reading.errorCode(),
                    reading.shift()
                ));
                created++;
            }
        }

        return Map.of("created", created);
    }

    public List<Map<String, Object>> machineTelemetry(Integer machineId, int requestedLimit) {
        findMachine(machineId);
        int limit = Math.max(1, Math.min(requestedLimit, 500));
        return telemetry.findByMachine_IdOrderByRecordedAtDesc(machineId, PageRequest.of(0, limit))
            .stream()
            .map(this::telemetryMap)
            .toList();
    }

    public List<Map<String, Object>> machineAnomalies(Integer machineId, boolean openOnly) {
        findMachine(machineId);
        var page = PageRequest.of(0, 100);
        List<IoTAnomaly> rows = openOnly
            ? anomalies.findByMachine_IdAndOpenTrueOrderByRecordedAtDesc(machineId, page)
            : anomalies.findByMachine_IdOrderByRecordedAtDesc(machineId, page);
        return rows.stream().map(this::anomalyMap).toList();
    }

    public List<Map<String, Object>> openAlerts() {
        return alerts.findTop100ByOpenTrueOrderByCreatedAtDesc()
            .stream()
            .map(this::alertMap)
            .toList();
    }

    public Map<String, Object> dailyOee(LocalDate requestedDay) {
        LocalDate day = requestedDay == null ? LocalDate.now(ZoneOffset.UTC) : requestedDay;
        Instant start = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", day.toString());
        result.putAll(IoTAnalytics.dailyOee(telemetry.findByRecordedAtBetweenOrderByRecordedAtAsc(start, end)));
        return result;
    }

    public Map<String, Object> dashboardSummary() {
        return Map.of(
            "oee", dailyOee(null),
            "latestTelemetry", telemetry.findTop20ByOrderByRecordedAtDesc().stream().map(this::telemetryMap).toList(),
            "recentAnomalies", anomalies.findTop10ByOrderByRecordedAtDesc().stream().map(this::anomalyMap).toList(),
            "openAlerts", openAlerts()
        );
    }

    public String powerBiCsv() {
        StringBuilder csv = new StringBuilder(
            "timestamp,machine_code,line,vibration,temperature,cycle_time,energy_kwh,produced_units,status,error_code,shift\n");

        telemetry.findTop5000ByOrderByRecordedAtDesc().forEach(row -> csv
            .append(row.getRecordedAt()).append(',')
            .append(csv(row.getMachine().getCode())).append(',')
            .append(csv(row.getMachine().getLine())).append(',')
            .append(row.getVibration()).append(',')
            .append(row.getTemperature()).append(',')
            .append(row.getCycleTime()).append(',')
            .append(row.getEnergyKwh()).append(',')
            .append(row.getProducedUnits()).append(',')
            .append(csv(row.getStatus())).append(',')
            .append(csv(row.getErrorCode())).append(',')
            .append(csv(row.getShiftName()))
            .append('\n'));

        return csv.toString();
    }

    private IoTMachine findMachine(Integer id) {
        return machines.findById(id)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Nie znaleziono maszyny IoT."));
    }

    private Map<String, Object> machineMap(IoTMachine machine) {
        return map(
            "id", machine.getId(),
            "code", machine.getCode(),
            "name", machine.getName(),
            "line", machine.getLine(),
            "target_cycle_time", machine.getTargetCycleTime(),
            "ideal_energy_per_unit", machine.getIdealEnergyPerUnit()
        );
    }

    private Map<String, Object> telemetryMap(IoTTelemetry row) {
        return map(
            "id", row.getId(),
            "machine_id", row.getMachine().getId(),
            "machine_code", row.getMachine().getCode(),
            "timestamp", row.getRecordedAt(),
            "vibration", row.getVibration(),
            "temperature", row.getTemperature(),
            "cycle_time", row.getCycleTime(),
            "energy_kwh", row.getEnergyKwh(),
            "produced_units", row.getProducedUnits(),
            "status", row.getStatus(),
            "error_code", row.getErrorCode(),
            "shift", row.getShiftName()
        );
    }

    private Map<String, Object> anomalyMap(IoTAnomaly row) {
        return map(
            "id", row.getId(),
            "machine_id", row.getMachine().getId(),
            "telemetry_id", row.getTelemetry().getId(),
            "timestamp", row.getRecordedAt(),
            "kind", row.getKind(),
            "severity", row.getSeverity(),
            "value", row.getValue(),
            "threshold", row.getThreshold(),
            "message", row.getMessage(),
            "open", row.isOpen()
        );
    }

    private Map<String, Object> alertMap(IoTAlert row) {
        return map(
            "id", row.getId(),
            "machine_id", row.getMachine().getId(),
            "anomaly_id", row.getAnomaly() == null ? null : row.getAnomaly().getId(),
            "created_at", row.getCreatedAt(),
            "severity", row.getSeverity(),
            "title", row.getTitle(),
            "message", row.getMessage(),
            "open", row.isOpen()
        );
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")
            ? "\"" + escaped + "\""
            : escaped;
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(values[index].toString(), values[index + 1]);
        }
        return result;
    }

    public record TelemetryCommand(
        Integer machineId,
        Instant timestamp,
        double vibration,
        double temperature,
        double cycleTime,
        double energyKwh,
        int producedUnits,
        String status,
        String errorCode,
        String shift
    ) {
    }
}
