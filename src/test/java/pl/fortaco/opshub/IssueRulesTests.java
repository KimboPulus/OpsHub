package pl.fortaco.opshub;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.fortaco.opshub.model.IssueSeverity;
import pl.fortaco.opshub.model.IssueStatus;
import pl.fortaco.opshub.model.ProductionIssue;
import pl.fortaco.opshub.service.IssueRules;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IssueRulesTests {
    @Test
    void openIssueAlwaysStaysVisible() {
        Instant now = Instant.parse("2026-01-10T12:00:00Z");
        ProductionIssue issue = new ProductionIssue();
        issue.setStatus(IssueStatus.NEW);
        issue.setCreatedAt(now.minus(30, ChronoUnit.DAYS));

        assertTrue(IssueRules.shouldShowOnDashboard(issue, now));
    }

    @Test
    void recentlyResolvedIssueStillStaysVisible() {
        Instant now = Instant.parse("2026-01-10T12:00:00Z");
        ProductionIssue issue = new ProductionIssue();
        issue.setStatus(IssueStatus.RESOLVED);
        issue.setResolvedAt(now.minus(6, ChronoUnit.DAYS));

        assertTrue(IssueRules.shouldShowOnDashboard(issue, now));
    }

    @Test
    void oldResolvedIssueIsHiddenFromDashboard() {
        Instant now = Instant.parse("2026-01-10T12:00:00Z");
        ProductionIssue issue = new ProductionIssue();
        issue.setStatus(IssueStatus.RESOLVED);
        issue.setResolvedAt(now.minus(8, ChronoUnit.DAYS));

        assertFalse(IssueRules.shouldShowOnDashboard(issue, now));
    }

    @Test
    void closedIssueWithoutResolvedDateIsStillShown() {
        ProductionIssue issue = new ProductionIssue();
        issue.setStatus(IssueStatus.VERIFIED);

        assertTrue(IssueRules.shouldShowOnDashboard(issue, Instant.parse("2026-01-10T12:00:00Z")));
    }

    @ParameterizedTest
    @CsvSource({
        "NEW,false",
        "IN_PROGRESS,false",
        "RESOLVED,true",
        "VERIFIED,true"
    })
    void deleteIsAllowedOnlyForClosedIssues(IssueStatus status, boolean expected) {
        ProductionIssue issue = new ProductionIssue();
        issue.setStatus(status);

        assertEquals(expected, IssueRules.canDelete(issue));
    }

    @ParameterizedTest
    @CsvSource({
        "CRITICAL,NEW,true",
        "CRITICAL,IN_PROGRESS,true",
        "CRITICAL,RESOLVED,false",
        "HIGH,NEW,false"
    })
    void activeNotificationIsOnlyForOpenCriticalIssues(IssueSeverity severity, IssueStatus status, boolean expected) {
        ProductionIssue issue = new ProductionIssue();
        issue.setSeverity(severity);
        issue.setStatus(status);

        assertEquals(expected, IssueRules.shouldTriggerActiveNotification(issue));
    }
}
