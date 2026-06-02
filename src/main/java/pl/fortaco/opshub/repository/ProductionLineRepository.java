package pl.fortaco.opshub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.fortaco.opshub.model.ProductionLine;

public interface ProductionLineRepository extends JpaRepository<ProductionLine, Integer> {
    boolean existsByCode(String code);
}
