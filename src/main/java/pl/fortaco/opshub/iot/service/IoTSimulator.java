package pl.fortaco.opshub.iot.service;

import org.springframework.stereotype.Component;
import pl.fortaco.opshub.iot.model.IoTMachine;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class IoTSimulator {
    private static final List<String> ERROR_CODES = List.of(
        "E_STOP",
        "OVERHEAT",
        "TOOL_WEAR",
        "PART_JAM",
        "SENSOR_LOSS"
    );

    public GeneratedReading generate(IoTMachine machine, Instant timestamp) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean anomaly = random.nextDouble() < 0.09;
        boolean down = random.nextDouble() < 0.04;
        boolean idle = !down && random.nextDouble() < 0.08;

        String status = down ? "DOWN" : idle ? "IDLE" : "RUNNING";
        int producedUnits = "RUNNING".equals(status) ? random.nextInt(1, 5) : 0;
        String errorCode = down || (anomaly && random.nextDouble() < 0.35)
            ? ERROR_CODES.get(random.nextInt(ERROR_CODES.size()))
            : null;

        return new GeneratedReading(
            machine.getId(),
            timestamp,
            round(random.nextDouble(2.2, 5.8) + (anomaly ? random.nextDouble(2.5, 4.5) : 0), 2),
            round(random.nextDouble(48, 74) + (anomaly ? random.nextDouble(12, 28) : 0), 1),
            round(random.nextDouble(30, 58) + (idle || anomaly ? random.nextDouble(10, 25) : 0), 1),
            round(random.nextDouble(1.2, 7.5) + (anomaly ? random.nextDouble(2.5, 4.0) : 0), 2),
            producedUnits,
            status,
            errorCode,
            IoTAnalytics.shiftFor(timestamp)
        );
    }

    private static double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    public record GeneratedReading(
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
