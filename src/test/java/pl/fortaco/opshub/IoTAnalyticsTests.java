package pl.fortaco.opshub;

import org.junit.jupiter.api.Test;
import pl.fortaco.opshub.iot.model.IoTMachine;
import pl.fortaco.opshub.iot.model.IoTTelemetry;
import pl.fortaco.opshub.iot.service.IoTAnalytics;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IoTAnalyticsTests {
    @Test
    void shiftForMatchesFactoryShifts() {
        assertEquals("A", IoTAnalytics.shiftFor(Instant.parse("2026-01-01T07:00:00Z")));
        assertEquals("B", IoTAnalytics.shiftFor(Instant.parse("2026-01-01T15:00:00Z")));
        assertEquals("C", IoTAnalytics.shiftFor(Instant.parse("2026-01-01T23:00:00Z")));
    }

    @Test
    void detectsTemperatureVibrationAndEnergyAnomalies() {
        IoTMachine machine = machine();
        IoTTelemetry reading = reading(machine, "RUNNING", null, 91, 8.5, 8, 2);

        var kinds = IoTAnalytics.detectAnomalies(reading, machine)
            .stream()
            .map(IoTAnalytics.AnomalyCandidate::kind)
            .toList();

        assertTrue(kinds.contains("temperature"));
        assertTrue(kinds.contains("vibration"));
        assertTrue(kinds.contains("energy"));
    }

    @Test
    void dailyOeeIncludesProductionUptimeAndDowntimeCause() {
        IoTMachine machine = machine();
        List<IoTTelemetry> rows = List.of(
            reading(machine, "RUNNING", null, 70, 4, 3, 2),
            reading(machine, "DOWN", "OVERHEAT", 90, 8.2, 1, 0)
        );

        Map<String, Object> result = IoTAnalytics.dailyOee(rows);

        assertEquals(2, result.get("production_count"));
        assertEquals("WLD-01", first(result, "machine_uptime").get("machine"));
        assertEquals("OVERHEAT", first(result, "downtime_causes").get("cause"));
    }

    private static IoTMachine machine() {
        IoTMachine machine = new IoTMachine();
        machine.setCode("WLD-01");
        machine.setTargetCycleTime(40);
        machine.setIdealEnergyPerUnit(1.2);
        return machine;
    }

    private static IoTTelemetry reading(
        IoTMachine machine,
        String status,
        String errorCode,
        double temperature,
        double vibration,
        double energy,
        int units) {
        IoTTelemetry row = new IoTTelemetry();
        row.setMachine(machine);
        row.setStatus(status);
        row.setErrorCode(errorCode);
        row.setTemperature(temperature);
        row.setVibration(vibration);
        row.setEnergyKwh(energy);
        row.setProducedUnits(units);
        row.setCycleTime(status.equals("RUNNING") ? 42 : 60);
        row.setShiftName("A");
        row.setRecordedAt(Instant.parse("2026-01-01T07:00:00Z"));
        return row;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> first(Map<String, Object> result, String key) {
        return ((List<Map<String, Object>>) result.get(key)).get(0);
    }
}
