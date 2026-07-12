package pl.fortaco.opshub.service;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("opshubReadiness")
public class OpsHubReadinessHealthIndicator implements HealthIndicator {
    private final JdbcTemplate jdbc;

    public OpsHubReadinessHealthIndicator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Health health() {
        try {
            Integer issues = jdbc.queryForObject("select count(*) from production_issue", Integer.class);
            Integer activities = jdbc.queryForObject("select count(*) from issue_activity", Integer.class);
            return Health.up()
                .withDetail("productionIssues", issues)
                .withDetail("auditEvents", activities)
                .build();
        } catch (Exception ex) {
            return Health.down(ex).build();
        }
    }
}
