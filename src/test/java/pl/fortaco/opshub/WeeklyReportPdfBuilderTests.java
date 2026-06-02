package pl.fortaco.opshub;

import org.junit.jupiter.api.Test;
import pl.fortaco.opshub.model.IssueCategory;
import pl.fortaco.opshub.model.Machine;
import pl.fortaco.opshub.model.ProductionIssue;
import pl.fortaco.opshub.service.WeeklyReportPdfBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WeeklyReportPdfBuilderTests {
    @Test
    void buildReturnsPdfHeader() {
        byte[] bytes = WeeklyReportPdfBuilder.build(List.of(issue("Laser stop", 40)), Instant.parse("2026-05-24T08:00:00Z"));

        assertTrue(new String(bytes, 0, 4, StandardCharsets.ISO_8859_1).startsWith("%PDF"));
    }

    @Test
    void buildIncludesReportTitle() {
        String text = pdfText(List.of(issue("Laser stop", 40)));

        assertTrue(text.contains("Fortaco Ops Hub - raport tygodniowy"));
    }

    @Test
    void buildIncludesIssueTitles() {
        String text = pdfText(List.of(issue("Brak materialu", 15)));

        assertTrue(text.contains("Brak materialu"));
    }

    @Test
    void buildIncludesTotalDowntime() {
        String text = pdfText(List.of(issue("Laser stop", 40), issue("Brak materialu", 15)));

        assertTrue(text.contains("Laczny przestoj: 55 min"));
    }

    @Test
    void buildHandlesEmptyReport() {
        String text = pdfText(List.of());

        assertTrue(text.contains("Liczba zgloszen: 0"));
        assertTrue(text.contains("Laczny przestoj: 0 min"));
    }

    private static ProductionIssue issue(String title, int downtime) {
        Machine machine = new Machine();
        machine.setCode("LASER-01");
        machine.setName("Stanowisko ciecia laserowego");

        ProductionIssue issue = new ProductionIssue();
        issue.setTitle(title);
        issue.setCategory(IssueCategory.MACHINE_FAILURE);
        issue.setDowntimeMinutes(downtime);
        issue.setMachine(machine);
        return issue;
    }

    private static String pdfText(List<ProductionIssue> issues) {
        return new String(WeeklyReportPdfBuilder.build(issues, Instant.parse("2026-05-24T08:00:00Z")), StandardCharsets.ISO_8859_1);
    }
}
