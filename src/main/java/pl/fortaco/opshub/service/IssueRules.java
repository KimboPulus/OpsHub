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

    public static boolean isClosed(ProductionIssue issue) {
        return issue.getStatus() == IssueStatus.RESOLVED || issue.getStatus() == IssueStatus.VERIFIED;
    }

    public static boolean canTransition(IssueStatus from, IssueStatus to, boolean leader) {
        if (to == null) {
            return false;
        }

        IssueStatus safeFrom = from == null ? IssueStatus.NEW : from;

        if (safeFrom == to) {
            return true;
        }

        if (!leader) {
            return safeFrom == IssueStatus.NEW && to == IssueStatus.IN_PROGRESS;
        }

        return switch (safeFrom) {
            case NEW -> to == IssueStatus.IN_PROGRESS || to == IssueStatus.RESOLVED;
            case IN_PROGRESS -> to == IssueStatus.NEW || to == IssueStatus.RESOLVED;
            case RESOLVED -> to == IssueStatus.IN_PROGRESS || to == IssueStatus.VERIFIED;
            case VERIFIED -> to == IssueStatus.IN_PROGRESS;
        };
    }

    public static int responseTargetMinutes(IssueSeverity severity) {
        return switch (severity) {
            case CRITICAL -> 15;
            case HIGH -> 60;
            case MEDIUM -> 240;
            case LOW -> 1440;
        };
    }

    public static int resolutionTargetMinutes(IssueSeverity severity) {
        return switch (severity) {
            case CRITICAL -> 120;
            case HIGH -> 480;
            case MEDIUM -> 1440;
            case LOW -> 4320;
        };
    }

    public static void assignLifecycleTargets(ProductionIssue issue, Instant createdAt) {
        issue.setCreatedAt(createdAt);
        issue.setUpdatedAt(createdAt);
        issue.setResponseDueAt(createdAt.plus(responseTargetMinutes(issue.getSeverity()), ChronoUnit.MINUTES));
        issue.setResolutionDueAt(createdAt.plus(resolutionTargetMinutes(issue.getSeverity()), ChronoUnit.MINUTES));
    }

    public static boolean responseBreached(ProductionIssue issue, Instant utcNow) {
        return !isClosed(issue)
            && issue.getAcknowledgedAt() == null
            && issue.getResponseDueAt() != null
            && issue.getResponseDueAt().isBefore(utcNow);
    }

    public static boolean resolutionBreached(ProductionIssue issue, Instant utcNow) {
        return !isClosed(issue)
            && issue.getResolutionDueAt() != null
            && issue.getResolutionDueAt().isBefore(utcNow);
    }

    public static boolean shouldEscalate(ProductionIssue issue, Instant utcNow) {
        return issue.getEscalatedAt() == null && resolutionBreached(issue, utcNow);
    }

    public static boolean shouldTriggerActiveNotification(ProductionIssue issue) {
        return issue.getSeverity() == IssueSeverity.CRITICAL
            && (issue.getStatus() == IssueStatus.NEW || issue.getStatus() == IssueStatus.IN_PROGRESS);
    }

    public static boolean isValidAssignmentTeam(String team) {
        return VALID_TEAMS.contains(team);
    }
}
