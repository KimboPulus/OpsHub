package pl.fortaco.opshub.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pl.fortaco.opshub.model.*;
import pl.fortaco.opshub.repository.IssueActivityRepository;
import pl.fortaco.opshub.repository.MachineRepository;
import pl.fortaco.opshub.repository.ProductionIssueRepository;
import pl.fortaco.opshub.repository.WorkOrderRepository;
import pl.fortaco.opshub.service.IssueRules;
import pl.fortaco.opshub.service.SecureFileUploadPolicy;
import pl.fortaco.opshub.service.WeeklyReportPdfBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@CrossOrigin(origins = {"http://127.0.0.1:5173", "http://localhost:5173"})
public class OpsHubController {
    private static final DateTimeFormatter EXPORT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final ProductionIssueRepository issues;
    private final MachineRepository machines;
    private final WorkOrderRepository workOrders;
    private final IssueActivityRepository activities;

    public OpsHubController(
        ProductionIssueRepository issues,
        MachineRepository machines,
        WorkOrderRepository workOrders,
        IssueActivityRepository activities) {
        this.issues = issues;
        this.machines = machines;
        this.workOrders = workOrders;
        this.activities = activities;
    }

    @GetMapping("/api/state")
    public Map<String, Object> state() {
        return Map.of(
            "issues", visibleIssues(),
            "machines", machines.findAllByOrderByCodeAsc(),
            "workOrders", workOrders.findAllByOrderByDueDateAsc(),
            "activities", activities.findAllByOrderByCreatedAtDesc()
        );
    }

    @GetMapping("/api/issues")
    public List<ProductionIssue> listIssues() {
        return visibleIssues();
    }

    @GetMapping("/api/issues/{id}")
    public ProductionIssue issue(@PathVariable Integer id) {
        return findIssue(id);
    }

    @GetMapping("/api/issues/{id}/similar")
    public Map<String, Object> similar(@PathVariable Integer id, Integer page, Integer pageSize) {
        ProductionIssue issue = findIssue(id);
        int safePage = Math.max(page == null ? 1 : page, 1);
        int safeSize = Math.max(pageSize == null ? 3 : pageSize, 1);

        Integer machineId = issue.getMachine() == null ? null : issue.getMachine().getId();
        Integer workOrderId = issue.getWorkOrder() == null ? null : issue.getWorkOrder().getId();
        List<ProductionIssue> all = issues.findSimilar(id, machineId, workOrderId);

        int from = Math.min((safePage - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());

        return Map.of(
            "total", all.size(),
            "page", safePage,
            "pageSize", safeSize,
            "items", all.subList(from, to)
        );
    }

    @PostMapping("/api/issues")
    public ProductionIssue createIssue(@Valid @RequestBody IssueRequest request) {
        ProductionIssue issue = new ProductionIssue();
        applyRequest(issue, request);
        issue.setCreatedAt(Instant.now());

        if (issue.getStatus() == IssueStatus.RESOLVED || issue.getStatus() == IssueStatus.VERIFIED) {
            issue.setResolvedAt(Instant.now());
        }

        addActivity(issue, IssueActivityType.SYSTEM, "Fortaco Ops Hub",
            "Zgłoszenie przekazane do: " + issue.getAssignedTeam() + ". Kanał alertu: " + issue.getNotificationChannel() + ".");

        if (IssueRules.shouldTriggerActiveNotification(issue)) {
            addActivity(issue, IssueActivityType.SYSTEM, "Silnik alertów",
                "Symulacja aktywnego powiadomienia: " + issue.getNotificationChannel() + " dla krytycznej awarii.");
        }

        return issues.save(issue);
    }

    @PatchMapping("/api/issues/{id}/status")
    public ProductionIssue updateStatus(@PathVariable Integer id, @RequestBody StatusRequest request) {
        ProductionIssue issue = findIssue(id);
        IssueStatus status = request.status();

        if (status == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Status jest wymagany.");
        }

        issue.setStatus(status);

        if (status == IssueStatus.NEW || status == IssueStatus.IN_PROGRESS) {
            issue.setResolvedAt(null);
        }

        if ((status == IssueStatus.RESOLVED || status == IssueStatus.VERIFIED) && issue.getResolvedAt() == null) {
            issue.setResolvedAt(Instant.now());
        }

        addActivity(issue, IssueActivityType.STATUS_CHANGE, "System", "Status zmieniony na: " + translateStatus(status));
        return issues.save(issue);
    }

    @PostMapping("/api/issues/{id}/comments")
    public ProductionIssue addComment(@PathVariable Integer id, @Valid @RequestBody CommentRequest request) {
        ProductionIssue issue = findIssue(id);
        String author = request.createdBy() == null || request.createdBy().isBlank()
            ? "Operator"
            : request.createdBy().trim();

        addActivity(issue, IssueActivityType.COMMENT, author, request.message().trim());
        return issues.save(issue);
    }

    @PostMapping(value = "/api/issues/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductionIssue addAttachment(@PathVariable Integer id, @RequestParam("file") MultipartFile file) throws IOException {
        ProductionIssue issue = findIssue(id);

        try {
            SecureFileUploadPolicy.validate(file);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }

        Path uploadRoot = Path.of("uploads", "issues");
        Files.createDirectories(uploadRoot);

        String storedFileName = SecureFileUploadPolicy.storedFileName(file.getOriginalFilename());
        Path target = uploadRoot.resolve(storedFileName).normalize();
        file.transferTo(target);

        IssueAttachment attachment = new IssueAttachment();
        attachment.setProductionIssue(issue);
        attachment.setFileName(file.getOriginalFilename() == null ? storedFileName : file.getOriginalFilename());
        attachment.setStoredFileName(storedFileName);
        attachment.setContentType(file.getContentType());
        attachment.setRelativePath("/uploads/issues/" + storedFileName);
        attachment.setUploadedAt(Instant.now());
        issue.getAttachments().add(attachment);

        return issues.save(issue);
    }

    @DeleteMapping("/api/issues/{id}")
    public void deleteIssue(@PathVariable Integer id) {
        ProductionIssue issue = findIssue(id);

        if (!IssueRules.canDelete(issue)) {
            throw new ResponseStatusException(BAD_REQUEST, "Usuwanie jest dostępne dopiero po zamknięciu zgłoszenia.");
        }

        issues.delete(issue);
    }

    @GetMapping("/api/machines")
    public List<Machine> listMachines() {
        return machines.findAllByOrderByCodeAsc();
    }

    @GetMapping("/api/work-orders")
    public List<WorkOrder> listWorkOrders() {
        return workOrders.findAllByOrderByDueDateAsc();
    }

    @GetMapping("/api/erp/daily-schedule")
    public Map<String, Object> dailySchedule() {
        List<Map<String, Object>> schedule = workOrders
            .findByStatusNotOrderByDueDateAscSapOrderNumberAsc(WorkOrderStatus.COMPLETED)
            .stream()
            .map(order -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sapOrderNumber", order.getSapOrderNumber());
                row.put("materialCode", order.getMaterialCode());
                row.put("materialDescription", order.getMaterialDescription());
                row.put("plannedQuantity", order.getPlannedQuantity());
                row.put("dueDate", order.getDueDate().atZone(ZoneId.systemDefault()).toLocalDate().toString());
                row.put("status", translateWorkOrderStatus(order.getStatus()));
                return row;
            })
            .toList();

        return Map.of("source", "Symulowany most ERP", "pulledAt", Instant.now(), "schedule", schedule);
    }

    @PostMapping("/api/issues/{id}/downtime-sync")
    public Map<String, Object> downtimeSync(@PathVariable Integer id) {
        ProductionIssue issue = findIssue(id);
        String order = issue.getWorkOrder() == null ? "Brak zlecenia" : issue.getWorkOrder().getSapOrderNumber();

        return Map.of(
            "target", "Symulowane potwierdzenie przestoju do ERP",
            "issueId", issue.getId(),
            "workOrder", order,
            "downtimeMinutes", issue.getDowntimeMinutes(),
            "syncedAt", Instant.now()
        );
    }

    @GetMapping("/uploads/issues/{fileName}")
    public ResponseEntity<Resource> uploadedIssueImage(@PathVariable String fileName) {
        Path uploadRoot = Path.of("uploads", "issues").toAbsolutePath().normalize();
        Path file = uploadRoot.resolve(fileName).normalize();

        if (!file.startsWith(uploadRoot) || !Files.exists(file)) {
            throw new ResponseStatusException(NOT_FOUND, "Nie znaleziono pliku.");
        }

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new FileSystemResource(file));
    }

    @GetMapping("/exports/production-issues.csv")
    public ResponseEntity<byte[]> productionIssuesCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append("Id;Tytuł;Opis;Kod maszyny;Nazwa maszyny;Zlecenie SAP;Kod materiału;Kategoria;Priorytet;Status;Przestój (min);Utworzono;Rozwiązano\n");

        issues.findAllByOrderByCreatedAtDesc().forEach(issue -> csv.append(String.join(";",
            String.valueOf(issue.getId()),
            escapeCsv(issue.getTitle()),
            escapeCsv(issue.getDescription()),
            escapeCsv(issue.getMachine() == null ? "Brak maszyny" : issue.getMachine().getCode()),
            escapeCsv(issue.getMachine() == null ? "" : issue.getMachine().getName()),
            escapeCsv(issue.getWorkOrder() == null ? "Brak zlecenia" : issue.getWorkOrder().getSapOrderNumber()),
            escapeCsv(issue.getWorkOrder() == null ? "" : issue.getWorkOrder().getMaterialCode()),
            escapeCsv(translateCategory(issue.getCategory())),
            escapeCsv(translateSeverity(issue.getSeverity())),
            escapeCsv(translateStatus(issue.getStatus())),
            String.valueOf(issue.getDowntimeMinutes()),
            EXPORT_DATE.format(issue.getCreatedAt()),
            issue.getResolvedAt() == null ? "" : EXPORT_DATE.format(issue.getResolvedAt())
        )).append("\n"));

        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, bytes, 0, bom.length);
        System.arraycopy(body, 0, bytes, bom.length, body.length);

        return attachment(bytes, "text/csv", "production-issues.csv");
    }

    @GetMapping("/exports/weekly-summary.pdf")
    public ResponseEntity<byte[]> weeklySummaryPdf() {
        Instant weekStart = Instant.now().minusSeconds(7 * 24 * 60 * 60);
        List<ProductionIssue> weeklyIssues = issues.findAllByOrderByCreatedAtDesc()
            .stream()
            .filter(issue -> !issue.getCreatedAt().isBefore(weekStart))
            .sorted(Comparator.comparingInt(ProductionIssue::getDowntimeMinutes).reversed())
            .toList();

        byte[] bytes = WeeklyReportPdfBuilder.build(weeklyIssues, Instant.now());
        String fileName = "fortaco-weekly-summary-" + DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.systemDefault()).format(Instant.now()) + ".pdf";
        return attachment(bytes, MediaType.APPLICATION_PDF_VALUE, fileName);
    }

    private List<ProductionIssue> visibleIssues() {
        Instant now = Instant.now();
        return issues.findAllByOrderByCreatedAtDesc()
            .stream()
            .filter(issue -> IssueRules.shouldShowOnDashboard(issue, now))
            .toList();
    }

    private ProductionIssue findIssue(Integer id) {
        return issues.findDetailedById(id)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Nie znaleziono zgłoszenia."));
    }

    private void applyRequest(ProductionIssue issue, IssueRequest request) {
        issue.setTitle(normalizeTitle(request, issue));
        issue.setDescription(request.description().trim());
        issue.setCategory(request.category());
        issue.setSeverity(request.severity());
        issue.setStatus(request.status());
        issue.setDowntimeMinutes(request.downtimeMinutes());
        issue.setAssignedTeam(IssueRules.isValidAssignmentTeam(request.assignedTeam()) ? request.assignedTeam() : "Mechanicy");
        issue.setAssignedTo(request.assignedTo() == null ? "" : request.assignedTo().trim());
        issue.setNotificationChannel(request.notificationChannel() == null || request.notificationChannel().isBlank()
            ? "Teams: Utrzymanie ruchu"
            : request.notificationChannel().trim());

        Machine machine = request.machineId() == null
            ? null
            : machines.findById(request.machineId()).orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Nie znaleziono maszyny."));
        WorkOrder order = request.workOrderId() == null
            ? null
            : workOrders.findById(request.workOrderId()).orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Nie znaleziono zlecenia."));

        issue.setMachine(machine);
        issue.setWorkOrder(order);
        issue.setSource(machine == null ? "Operator manualny" : "QR maszyny " + machine.getCode());
    }

    private String normalizeTitle(IssueRequest request, ProductionIssue issue) {
        if (request.title() != null && !request.title().isBlank()) {
            return request.title().trim();
        }

        if (request.workOrderId() != null) {
            return workOrders.findById(request.workOrderId())
                .map(order -> "Problem na zleceniu " + order.getSapOrderNumber())
                .orElse("Nowe zgłoszenie: " + translateCategory(request.category()));
        }

        return "Nowe zgłoszenie: " + translateCategory(request.category());
    }

    private static void addActivity(ProductionIssue issue, IssueActivityType type, String createdBy, String message) {
        IssueActivity activity = new IssueActivity();
        activity.setProductionIssue(issue);
        activity.setType(type);
        activity.setCreatedBy(createdBy);
        activity.setMessage(message);
        activity.setCreatedAt(Instant.now());
        issue.getActivities().add(activity);
    }

    private static ResponseEntity<byte[]> attachment(byte[] bytes, String contentType, String fileName) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
            .contentType(MediaType.parseMediaType(contentType))
            .body(bytes);
    }

    private static String translateCategory(IssueCategory category) {
        return switch (category) {
            case MACHINE_FAILURE -> "Awaria maszyny";
            case QUALITY_PROBLEM -> "Problem jakościowy";
            case MATERIAL_SHORTAGE -> "Brak materiału";
            case SAFETY -> "Bezpieczeństwo";
            case PLANNING -> "Planowanie";
            case OTHER -> "Inne";
        };
    }

    private static String translateSeverity(IssueSeverity severity) {
        return switch (severity) {
            case LOW -> "Niski";
            case MEDIUM -> "Średni";
            case HIGH -> "Wysoki";
            case CRITICAL -> "Krytyczny";
        };
    }

    private static String translateStatus(IssueStatus status) {
        return switch (status) {
            case NEW -> "Nowe";
            case IN_PROGRESS -> "W toku";
            case RESOLVED -> "Rozwiązane";
            case VERIFIED -> "Zweryfikowane";
        };
    }

    private static String translateWorkOrderStatus(WorkOrderStatus status) {
        return switch (status) {
            case PLANNED -> "Planowane";
            case RELEASED -> "Zwolnione";
            case IN_PRODUCTION -> "W produkcji";
            case COMPLETED -> "Zamknięte";
            case DELAYED -> "Opóźnione";
        };
    }

    private static String escapeCsv(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String escaped = value.replace("\"", "\"\"");

        if (escaped.contains(";") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }

        return escaped;
    }

    public record IssueRequest(
        @Size(max = 120) String title,
        @NotBlank @Size(max = 1000) String description,
        IssueCategory category,
        IssueSeverity severity,
        IssueStatus status,
        @Min(0) @Max(10080) int downtimeMinutes,
        String assignedTeam,
        String assignedTo,
        String notificationChannel,
        Integer machineId,
        Integer workOrderId
    ) {
        public IssueRequest {
            category = category == null ? IssueCategory.MACHINE_FAILURE : category;
            severity = severity == null ? IssueSeverity.MEDIUM : severity;
            status = status == null ? IssueStatus.NEW : status;
            assignedTeam = assignedTeam == null || assignedTeam.isBlank() ? "Mechanicy" : assignedTeam.trim();
        }
    }

    public record StatusRequest(IssueStatus status) {
    }

    public record CommentRequest(
        @NotBlank @Size(max = 1000) String message,
        String createdBy
    ) {
    }
}
