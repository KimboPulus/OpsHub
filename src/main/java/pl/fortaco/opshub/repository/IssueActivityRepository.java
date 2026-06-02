package pl.fortaco.opshub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.fortaco.opshub.model.IssueActivity;

import java.util.List;

public interface IssueActivityRepository extends JpaRepository<IssueActivity, Integer> {
    List<IssueActivity> findAllByOrderByCreatedAtDesc();
}
