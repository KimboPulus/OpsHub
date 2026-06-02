package pl.fortaco.opshub.service;

import pl.fortaco.opshub.model.ProductionIssue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class WeeklyReportPdfBuilder {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private WeeklyReportPdfBuilder() {
    }

    public static byte[] build(List<ProductionIssue> issues, Instant generatedAt) {
        List<String> lines = new ArrayList<>();
        lines.add("Fortaco Ops Hub - raport tygodniowy");
        lines.add("Wygenerowano: " + DATE_TIME.format(generatedAt));
        lines.add("Liczba zgłoszeń: " + issues.size());
        lines.add("");

        int totalDowntime = issues.stream().mapToInt(ProductionIssue::getDowntimeMinutes).sum();
        lines.add("Łączny przestój: " + totalDowntime + " min");
        lines.add("");

        issues.stream()
            .limit(12)
            .forEach(issue -> lines.add("- " + clean(issue.getTitle()) + " | " + issue.getDowntimeMinutes() + " min"));

        return simplePdf(lines);
    }

    private static byte[] simplePdf(List<String> lines) {
        StringBuilder content = new StringBuilder("BT\n/F1 12 Tf\n50 790 Td\n14 TL\n");

        for (String line : lines) {
            content.append("(").append(escape(line)).append(") Tj\nT*\n");
        }

        content.append("ET\n");

        byte[] stream = content.toString().getBytes(StandardCharsets.ISO_8859_1);
        List<String> objects = List.of(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            "<< /Length " + stream.length + " >>\nstream\n" + content + "endstream"
        );

        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();

        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }

        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n");
        pdf.append("0000000000 65535 f \n");

        for (Integer offset : offsets) {
            pdf.append(String.format("%010d 00000 n \n", offset));
        }

        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n");
        pdf.append("startxref\n").append(xref).append("\n%%EOF");

        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String clean(String value) {
        return value
            .replace("ą", "a").replace("ć", "c").replace("ę", "e").replace("ł", "l")
            .replace("ń", "n").replace("ó", "o").replace("ś", "s").replace("ż", "z")
            .replace("ź", "z").replace("Ą", "A").replace("Ć", "C").replace("Ę", "E")
            .replace("Ł", "L").replace("Ń", "N").replace("Ó", "O").replace("Ś", "S")
            .replace("Ż", "Z").replace("Ź", "Z");
    }

    private static String escape(String value) {
        return clean(value).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}
