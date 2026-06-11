package pl.fortaco.opshub.iot.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.fortaco.opshub.iot.model.IoTAnomaly;

import java.util.List;

public interface IoTAnomalyRepository extends JpaRepository<IoTAnomaly, Long> {
    List<IoTAnomaly> findTop10ByOrderByRecordedAtDesc();

    List<IoTAnomaly> findByMachine_IdOrderByRecordedAtDesc(Integer machineId, Pageable pageable);

    List<IoTAnomaly> findByMachine_IdAndOpenTrueOrderByRecordedAtDesc(Integer machineId, Pageable pageable);
}
