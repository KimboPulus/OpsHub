package pl.fortaco.opshub.iot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.fortaco.opshub.iot.model.IoTMachine;

import java.util.List;

public interface IoTMachineRepository extends JpaRepository<IoTMachine, Integer> {
    boolean existsByCode(String code);

    List<IoTMachine> findAllByOrderByCodeAsc();
}
