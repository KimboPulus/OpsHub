package pl.fortaco.opshub.iot.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.fortaco.opshub.iot.model.IoTTelemetry;

import java.time.Instant;
import java.util.List;

public interface IoTTelemetryRepository extends JpaRepository<IoTTelemetry, Long> {
    List<IoTTelemetry> findTop20ByOrderByRecordedAtDesc();

    List<IoTTelemetry> findByMachine_IdOrderByRecordedAtDesc(Integer machineId, Pageable pageable);

    List<IoTTelemetry> findByRecordedAtBetweenOrderByRecordedAtAsc(Instant start, Instant end);

    List<IoTTelemetry> findTop5000ByOrderByRecordedAtDesc();
}
