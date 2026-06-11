package pl.fortaco.opshub.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.fortaco.opshub.iot.service.IoTDataService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/iot")
public class IoTController {
    private final IoTDataService iot;

    public IoTController(IoTDataService iot) {
        this.iot = iot;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "opshub-iot");
    }

    @GetMapping("/machines")
    public List<Map<String, Object>> machines() {
        return iot.machines();
    }

    @PostMapping("/telemetry")
    public Map<String, Object> ingestTelemetry(@Valid @RequestBody TelemetryRequest request) {
        return iot.ingest(new IoTDataService.TelemetryCommand(
            request.machineId(),
            request.timestamp(),
            request.vibration(),
            request.temperature(),
            request.cycleTime(),
            request.energyKwh(),
            request.producedUnits(),
            request.status(),
            request.errorCode(),
            request.shift()
        ));
    }

    @PostMapping("/simulator/tick")
    public Map<String, Object> simulatorTick(@RequestParam(defaultValue = "1") int count) {
        return iot.simulate(count);
    }

    @GetMapping("/machines/{id}/telemetry")
    public List<Map<String, Object>> machineTelemetry(
        @PathVariable Integer id,
        @RequestParam(defaultValue = "100") int limit) {
        return iot.machineTelemetry(id, limit);
    }

    @GetMapping("/machines/{id}/anomalies")
    public List<Map<String, Object>> machineAnomalies(
        @PathVariable Integer id,
        @RequestParam(name = "open_only", defaultValue = "false") boolean openOnly) {
        return iot.machineAnomalies(id, openOnly);
    }

    @GetMapping("/alerts/open")
    public List<Map<String, Object>> openAlerts() {
        return iot.openAlerts();
    }

    @GetMapping("/oee/daily")
    public Map<String, Object> dailyOee(@RequestParam(required = false) LocalDate day) {
        return iot.dailyOee(day);
    }

    @GetMapping("/dashboard/summary")
    public Map<String, Object> dashboardSummary() {
        return iot.dashboardSummary();
    }

    @GetMapping(value = "/exports/powerbi.csv", produces = "text/csv")
    public ResponseEntity<String> powerBiCsv() {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"factory-iot-powerbi.csv\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(iot.powerBiCsv());
    }

    public record TelemetryRequest(
        @JsonProperty("machine_id") @NotNull Integer machineId,
        Instant timestamp,
        @PositiveOrZero double vibration,
        double temperature,
        @JsonProperty("cycle_time") @Positive double cycleTime,
        @JsonProperty("energy_kwh") @PositiveOrZero double energyKwh,
        @JsonProperty("produced_units") @PositiveOrZero int producedUnits,
        @NotBlank String status,
        @JsonProperty("error_code") String errorCode,
        String shift
    ) {
    }
}
