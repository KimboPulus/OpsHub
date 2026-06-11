package pl.fortaco.opshub.iot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.fortaco.opshub.iot.model.IoTAlert;

import java.util.List;

public interface IoTAlertRepository extends JpaRepository<IoTAlert, Long> {
    List<IoTAlert> findTop100ByOpenTrueOrderByCreatedAtDesc();
}
