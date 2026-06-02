package pl.fortaco.opshub.service;

import pl.fortaco.opshub.model.IssueSeverity;
import pl.fortaco.opshub.model.IssueStatus;
import pl.fortaco.opshub.model.ProductionIssue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

public final class IssueRules {
    private static final Set<String> VALID_TEAMS = Set.of("Mechanicy", "Elektrycy", "Jakość", "Planowanie", "BHP");

    private IssueRules() {
    }

    public static boolean shouldShowOnDashboard(ProductionIssue issue, Instant utcNow) {
        boolean closed = issue.getStatus() == IssueStatus.RESOLVED || issue.getStatus() == IssueStatus.VERIFIED;

        if (!closed) {
            return true;
        }

        if (issue.getResolvedAt() == null) {
            return true;
        }

        return !issue.getResolvedAt().isBefore(utcNow.minus(7, ChronoUnit.DAYS));
    }

    public static boolean canDelete(ProductionIssue issue) {
        return issue.getStatus() == IssueStatus.RESOLVED || issue.getStatus() == IssueStatus.VERIFIED;
    }

    public static boolean shouldTriggerActiveNotification(ProductionIssue issue) {
        return issue.getSeverity() == IssueSeverity.CRITICAL
            && (issue.getStatus() == IssueStatus.NEW || issue.getStatus() == IssueStatus.IN_PROGRESS);
    }

    public static boolean isValidAssignmentTeam(String team) {
        return VALID_TEAMS.contains(team);
    }
}
