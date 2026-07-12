package pl.fortaco.opshub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.fortaco.opshub.model.IssueActivityType;
import pl.fortaco.opshub.model.IssueActivity;
import pl.fortaco.opshub.model.IssueSeverity;

import java.time.Instant;
import java.util.List;

public interface IssueActivityRepository extends JpaRepository<IssueActivity, Integer> {
    List<IssueActivity> findAllByOrderByCreatedAtDesc();

    @Query("""
        select activity from IssueActivity activity
        join activity.productionIssue issue
        left join issue.machine machine
        where (:issueId is null or issue.id = :issueId)
          and (:type is null or activity.type = :type)
          and (:severity is null or issue.severity = :severity)
          and (:actor is null or lower(activity.createdBy) like lower(concat('%', :actor, '%')))
          and (:machine is null or lower(machine.code) like lower(concat('%', :machine, '%')))
          and (:from is null or activity.createdAt >= :from)
          and (:to is null or activity.createdAt <= :to)
          and (
            :text is null
            or lower(activity.message) like lower(concat('%', :text, '%'))
            or lower(issue.title) like lower(concat('%', :text, '%'))
            or lower(issue.description) like lower(concat('%', :text, '%'))
          )
        order by activity.createdAt desc
        """)
    List<IssueActivity> searchAuditEvents(
        @Param("issueId") Integer issueId,
        @Param("type") IssueActivityType type,
        @Param("severity") IssueSeverity severity,
        @Param("actor") String actor,
        @Param("machine") String machine,
        @Param("text") String text,
        @Param("from") Instant from,
        @Param("to") Instant to);
}
