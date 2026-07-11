package pl.fortaco.opshub.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.fortaco.opshub.model.ProductionIssue;

import java.util.List;
import java.util.Optional;

public interface ProductionIssueRepository extends JpaRepository<ProductionIssue, Integer> {
    @EntityGraph(attributePaths = {"machine", "machine.productionLine", "workOrder"}, type = EntityGraph.EntityGraphType.LOAD)
    List<ProductionIssue> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"machine", "machine.productionLine", "workOrder"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<ProductionIssue> findDetailedById(Integer id);

    @EntityGraph(attributePaths = {"machine", "workOrder", "activities"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("""
        select issue from ProductionIssue issue
        where issue.id <> :id
          and (
            (:machineId is not null and issue.machine.id = :machineId)
            or issue.category = (select source.category from ProductionIssue source where source.id = :id)
            or (:workOrderId is not null and issue.workOrder.id = :workOrderId)
          )
        order by coalesce(issue.resolvedAt, issue.createdAt) desc
        """)
    List<ProductionIssue> findSimilar(Integer id, Integer machineId, Integer workOrderId);
}
