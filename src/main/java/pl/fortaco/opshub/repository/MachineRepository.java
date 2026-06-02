package pl.fortaco.opshub.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.fortaco.opshub.model.Machine;

import java.util.List;
import java.util.Optional;

public interface MachineRepository extends JpaRepository<Machine, Integer> {
    @EntityGraph(attributePaths = "productionLine")
    List<Machine> findAllByOrderByCodeAsc();

    @EntityGraph(attributePaths = "productionLine")
    Optional<Machine> findByCodeIgnoreCase(String code);
}
