package pl.fortaco.opshub.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.fortaco.opshub.model.IssueActivity;
import pl.fortaco.opshub.model.IssueActivityType;
import pl.fortaco.opshub.model.IssueSeverity;
import pl.fortaco.opshub.model.ProductionIssue;
import pl.fortaco.opshub.repository.IssueActivityRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
public class AuditController {
    private final IssueActivityRepository activities;

    public AuditController(IssueActivityRepository activities) {
        this.activities = activities;
    }

    @GetMapping("/api/audit/events")
    public Map<String, Object> events(
        @RequestParam(required = false) Integer issueId,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String machine,
        @RequestParam(required = false) String text,
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to,
        @RequestParam(defaultValue = "100") int limit) {
        List<AuditEvent> rows = search(issueId, type, severity, actor, machine, text, from, to, limit);
        return Map.of("total", rows.size(), "items", rows);
    }

    @GetMapping("/exports/audit-events.csv")
    public ResponseEntity<byte[]> export(
        @RequestParam(required = false) Integer issueId,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String machine,
        @RequestParam(required = false) String text,
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to,
        @RequestParam(defaultValue = "500") int limit) {
        StringBuilder csv = new StringBuilder("id;issueId;issueTitle;type;actor;message;machine;severity;createdAt\n");
        search(issueId, type, severity, actor, machine, text, from, to, limit)
            .forEach(row -> csv.append(String.join(";",
                String.valueOf(row.id()),
                String.valueOf(row.issueId()),
                escape(row.issueTitle()),
                row.type(),
                escape(row.actor()),
                escape(row.message()),
                escape(row.machineCode()),
                row.issueSeverity(),
                row.createdAt().toString()
            )).append("\n"));

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("audit-events.csv").build().toString())
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(bytes);
    }

    private List<AuditEvent> search(
        Integer issueId,
        String type,
        String severity,
        String actor,
        String machine,
        String text,
        Instant from,
        Instant to,
        int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return activities.searchAuditEvents(
                issueId,
                parseEnum(type, IssueActivityType.class),
                parseEnum(severity, IssueSeverity.class),
                blankToNull(actor),
                blankToNull(machine),
                blankToNull(text),
                from,
                to)
            .stream()
            .limit(safeLimit)
            .map(AuditController::mapEvent)
            .toList();
    }

    private static AuditEvent mapEvent(IssueActivity activity) {
        ProductionIssue issue = activity.getProductionIssue();
        String machineCode = issue.getMachine() == null ? "" : issue.getMachine().getCode();
        return new AuditEvent(
            activity.getId(),
            issue.getId(),
            issue.getTitle(),
            activity.getType().name(),
            activity.getCreatedBy(),
            activity.getMessage(),
            machineCode,
            issue.getSeverity().name(),
            issue.getStatus().name(),
            activity.getCreatedAt());
    }

    private static <T extends Enum<T>> T parseEnum(String value, Class<T> type) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported filter value: " + value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String escape(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(";") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    public record AuditEvent(
        Integer id,
        Integer issueId,
        String issueTitle,
        String type,
        String actor,
        String message,
        String machineCode,
        String issueSeverity,
        String issueStatus,
        Instant createdAt) {
    }
}
