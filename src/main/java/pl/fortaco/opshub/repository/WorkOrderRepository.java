package pl.fortaco.opshub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.fortaco.opshub.model.WorkOrder;
import pl.fortaco.opshub.model.WorkOrderStatus;

import java.util.List;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Integer> {
    List<WorkOrder> findByStatusNotOrderByDueDateAscSapOrderNumberAsc(WorkOrderStatus status);

    List<WorkOrder> findAllByOrderByDueDateAsc();
}
