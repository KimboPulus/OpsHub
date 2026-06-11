package pl.fortaco.opshub.iot.service;

import pl.fortaco.opshub.iot.model.IoTMachine;
import pl.fortaco.opshub.iot.model.IoTTelemetry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class IoTAnalytics {
    static final double TEMP_WARNING = 78.0;
    static final double TEMP_CRITICAL = 88.0;
    static final double VIBRATION_WARNING = 6.5;
    static final double VIBRATION_CRITICAL = 8.0;
    static final double ENERGY_FACTOR_WARNING = 1.65;

    private IoTAnalytics() {
    }

    public static String shiftFor(Instant timestamp) {
        int hour = timestamp.atZone(ZoneOffset.UTC).getHour();
        if (hour >= 6 && hour < 14) {
            return "A";
        }
        if (hour >= 14 && hour < 22) {
            return "B";
        }
        return "C";
    }

    public static List<AnomalyCandidate> detectAnomalies(IoTTelemetry reading, IoTMachine machine) {
        java.util.ArrayList<AnomalyCandidate> anomalies = new java.util.ArrayList<>();

        if (reading.getTemperature() >= TEMP_CRITICAL) {
            anomalies.add(candidate(
                "temperature",
                "critical",
                reading.getTemperature(),
                TEMP_CRITICAL,
                "%s: temperatura jest krytycznie wysoka (%.1f C).".formatted(machine.getCode(), reading.getTemperature())));
        } else if (reading.getTemperature() >= TEMP_WARNING) {
            anomalies.add(candidate(
                "temperature",
                "warning",
                reading.getTemperature(),
                TEMP_WARNING,
                "%s: temperatura jest powyżej normy (%.1f C).".formatted(machine.getCode(), reading.getTemperature())));
        }

        if (reading.getVibration() >= VIBRATION_CRITICAL) {
            anomalies.add(candidate(
                "vibration",
                "critical",
                reading.getVibration(),
                VIBRATION_CRITICAL,
                "%s: drgania wyglądają niebezpiecznie (%.2f mm/s).".formatted(machine.getCode(), reading.getVibration())));
        } else if (reading.getVibration() >= VIBRATION_WARNING) {
            anomalies.add(candidate(
                "vibration",
                "warning",
                reading.getVibration(),
                VIBRATION_WARNING,
                "%s: drgania rosną ponad normalny poziom (%.2f mm/s).".formatted(machine.getCode(), reading.getVibration())));
        }

        if (reading.getProducedUnits() > 0) {
            double energyPerUnit = reading.getEnergyKwh() / reading.getProducedUnits();
            double threshold = machine.getIdealEnergyPerUnit() * ENERGY_FACTOR_WARNING;
            if (energyPerUnit >= threshold) {
                anomalies.add(candidate(
                    "energy",
                    "warning",
                    round(energyPerUnit, 3),
                    round(threshold, 3),
                    "%s: zużycie energii na sztukę jest wysokie (%.2f kWh/szt.).".formatted(machine.getCode(), energyPerUnit)));
            }
        }

        if ("DOWN".equals(reading.getStatus()) || hasText(reading.getErrorCode())) {
            String suffix = hasText(reading.getErrorCode())
                ? " i błąd " + reading.getErrorCode() + "."
                : ".";
            anomalies.add(candidate(
                "machine_status",
                "DOWN".equals(reading.getStatus()) ? "critical" : "warning",
                null,
                null,
                machine.getCode() + ": maszyna zgłosiła status " + reading.getStatus() + suffix));
        }

        return anomalies;
    }

    public static Map<String, Object> dailyOee(List<IoTTelemetry> rows) {
        if (rows.isEmpty()) {
            return emptyOee();
        }

        long running = rows.stream()
            .filter(row -> "RUNNING".equals(row.getStatus()))
            .count();
        double availability = (double) running / rows.size();
        int produced = rows.stream().mapToInt(IoTTelemetry::getProducedUnits).sum();
        double averageTarget = rows.stream()
            .mapToDouble(row -> row.getMachine().getTargetCycleTime())
            .average()
            .orElse(0);
        double averageCycle = rows.stream()
            .filter(row -> row.getProducedUnits() > 0)
            .mapToDouble(IoTTelemetry::getCycleTime)
            .average()
            .orElse(averageTarget);
        double performance = Math.min(1.0, averageTarget / Math.max(averageCycle, 1));
        long errorRows = rows.stream().filter(row -> hasText(row.getErrorCode())).count();
        double quality = Math.max(0, 1 - ((double) errorRows / rows.size()) * 0.5);
        double energyPerUnit = produced == 0
            ? 0
            : rows.stream().mapToDouble(IoTTelemetry::getEnergyKwh).sum() / produced;

        Map<String, List<IoTTelemetry>> byMachine = rows.stream()
            .collect(Collectors.groupingBy(row -> row.getMachine().getCode()));
        List<Map<String, Object>> machineUptime = byMachine.entrySet().stream()
            .map(entry -> {
                double uptime = entry.getValue().stream()
                    .filter(row -> "RUNNING".equals(row.getStatus()))
                    .count() * 100.0 / entry.getValue().size();
                return map("machine", entry.getKey(), "uptime", round(uptime, 1));
            })
            .sorted(Comparator.comparingDouble(row -> -((Number) row.get("uptime")).doubleValue()))
            .toList();

        Map<String, Integer> shiftTotals = rows.stream()
            .collect(Collectors.groupingBy(
                IoTTelemetry::getShiftName,
                Collectors.summingInt(IoTTelemetry::getProducedUnits)));
        List<Map<String, Object>> productionByShift = shiftTotals.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> map("shift", entry.getKey(), "produced_units", entry.getValue()))
            .toList();

        Map<String, Long> downtimeCounts = rows.stream()
            .filter(row -> !"RUNNING".equals(row.getStatus()))
            .collect(Collectors.groupingBy(
                row -> hasText(row.getErrorCode()) ? row.getErrorCode() : "no_error",
                Collectors.counting()));
        List<Map<String, Object>> downtimeCauses = downtimeCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .map(entry -> map("cause", entry.getKey(), "count", entry.getValue().intValue()))
            .toList();

        return map(
            "availability", round(availability * 100, 1),
            "performance", round(performance * 100, 1),
            "quality", round(quality * 100, 1),
            "oee", round(availability * performance * quality * 100, 1),
            "production_count", produced,
            "energy_per_unit", round(energyPerUnit, 3),
            "machine_uptime", machineUptime,
            "production_by_shift", productionByShift,
            "downtime_causes", downtimeCauses
        );
    }

    private static Map<String, Object> emptyOee() {
        return map(
            "availability", 0,
            "performance", 0,
            "quality", 100,
            "oee", 0,
            "production_count", 0,
            "energy_per_unit", 0,
            "machine_uptime", List.of(),
            "production_by_shift", List.of(),
            "downtime_causes", List.of()
        );
    }

    private static AnomalyCandidate candidate(String kind, String severity, Double value, Double threshold, String message) {
        return new AnomalyCandidate(kind, severity, value, threshold, message);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value)
            .setScale(scale, RoundingMode.HALF_UP)
            .doubleValue();
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(values[index].toString(), values[index + 1]);
        }
        return result;
    }

    public record AnomalyCandidate(
        String kind,
        String severity,
        Double value,
        Double threshold,
        String message
    ) {
    }
}
