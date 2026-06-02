package pl.fortaco.opshub.service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.fortaco.opshub.model.*;
import pl.fortaco.opshub.repository.MachineRepository;
import pl.fortaco.opshub.repository.ProductionIssueRepository;
import pl.fortaco.opshub.repository.ProductionLineRepository;
import pl.fortaco.opshub.repository.WorkOrderRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Component
public class DbSeeder implements CommandLineRunner {
    private final ProductionLineRepository lines;
    private final MachineRepository machines;
    private final WorkOrderRepository workOrders;
    private final ProductionIssueRepository issues;

    public DbSeeder(
        ProductionLineRepository lines,
        MachineRepository machines,
        WorkOrderRepository workOrders,
        ProductionIssueRepository issues) {
        this.lines = lines;
        this.machines = machines;
        this.workOrders = workOrders;
        this.issues = issues;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (lines.existsByCode("WELD-A")) {
            removeScratchIssues();
            return;
        }

        ProductionLine weldingLine = line("WELD-A", "Linia spawalnicza A");
        ProductionLine assemblyLine = line("ASM-2", "Linia montażowa 2");
        lines.save(weldingLine);
        lines.save(assemblyLine);

        Machine laser = machine("LASER-01", "Stanowisko cięcia laserowego", MachineCriticality.BOTTLENECK, weldingLine);
        Machine press = machine("PRESS-02", "Prasa hydrauliczna", MachineCriticality.HIGH, assemblyLine);
        Machine weldRobot = machine("WELD-03", "Cela robota spawalniczego", MachineCriticality.MEDIUM, weldingLine);
        machines.save(laser);
        machines.save(press);
        machines.save(weldRobot);

        WorkOrder order1 = workOrder("45001234", "FRAME-AXLE-001", "Zespół ramy osi", 120, 3, WorkOrderStatus.IN_PRODUCTION);
        WorkOrder order2 = workOrder("45001235", "CAB-BRACKET-017", "Wspornik mocowania kabiny", 80, 5, WorkOrderStatus.RELEASED);
        workOrders.save(order1);
        workOrders.save(order2);

        ProductionIssue issue1 = issue(
            "Laser zatrzymał się podczas zlecenia 45001234",
            "Operator zgłosił zatrzymanie maszyny podczas cięcia. Prawdopodobny problem z czujnikiem albo ustawieniem, wymagany przegląd UR.",
            IssueCategory.MACHINE_FAILURE,
            IssueSeverity.HIGH,
            IssueStatus.IN_PROGRESS,
            37,
            "Mechanicy",
            "Dyżurny UR",
            "Operator manualny",
            "Teams: Utrzymanie ruchu",
            laser,
            order1);

        ProductionIssue issue2 = issue(
            "Brak materiału do wsporników kabiny",
            "Brakuje partii materiału do produkcji wsporników. Planowanie powinno potwierdzić kolejne okno dostawy.",
            IssueCategory.MATERIAL_SHORTAGE,
            IssueSeverity.MEDIUM,
            IssueStatus.NEW,
            15,
            "Planowanie",
            "Planista zmiany",
            "Panel produkcji",
            "Panel produkcji",
            null,
            order2);

        ProductionIssue issue3 = issue(
            "Wykryto problem jakości spoiny",
            "Kontrola jakości wykryła powtarzalną niezgodność na spoinie. Temat nie jest katastrofalny, ale wymaga sprawdzenia zanim narosną kolejne sztuki.",
            IssueCategory.QUALITY_PROBLEM,
            IssueSeverity.CRITICAL,
            IssueStatus.NEW,
            52,
            "Jakość",
            "Lider jakości",
            "QR maszyny WELD-03",
            "SMS do UR",
            weldRobot,
            order1);

        addSystemActivity(issue1, "Fortaco Ops Hub", "Zgłoszenie przekazane do: Mechanicy. Kanał alertu: Teams: Utrzymanie ruchu.", 1);
        addSystemActivity(issue2, "Fortaco Ops Hub", "Zgłoszenie przekazane do: Planowanie. Kanał alertu: Panel produkcji.", 1);
        addSystemActivity(issue3, "Fortaco Ops Hub", "Zgłoszenie przekazane do: Jakość. Kanał alertu: SMS do UR.", 1);
        addSystemActivity(issue3, "Silnik alertów", "Symulacja aktywnego powiadomienia dla krytycznej awarii.", 2);

        issues.save(issue1);
        issues.save(issue2);
        issues.save(issue3);
    }

    private void removeScratchIssues() {
        Set<String> scratchTitles = Set.of("asdad", "sdfsf", "sdfsdf", "test awarii", "test awaria", "test");

        issues.findAll().stream()
            .filter(issue -> scratchTitles.contains(issue.getTitle().trim().toLowerCase()))
            .forEach(issues::delete);
    }

    private static ProductionLine line(String code, String name) {
        ProductionLine line = new ProductionLine();
        line.setCode(code);
        line.setName(name);
        return line;
    }

    private static Machine machine(String code, String name, MachineCriticality criticality, ProductionLine line) {
        Machine machine = new Machine();
        machine.setCode(code);
        machine.setName(name);
        machine.setCriticality(criticality);
        machine.setProductionLine(line);
        return machine;
    }

    private static WorkOrder workOrder(String number, String materialCode, String description, int quantity, int dueInDays, WorkOrderStatus status) {
        WorkOrder order = new WorkOrder();
        order.setSapOrderNumber(number);
        order.setMaterialCode(materialCode);
        order.setMaterialDescription(description);
        order.setPlannedQuantity(quantity);
        order.setDueDate(Instant.now().plus(dueInDays, ChronoUnit.DAYS));
        order.setStatus(status);
        return order;
    }

    private static ProductionIssue issue(
        String title,
        String description,
        IssueCategory category,
        IssueSeverity severity,
        IssueStatus status,
        int downtime,
        String assignedTeam,
        String assignedTo,
        String source,
        String channel,
        Machine machine,
        WorkOrder order) {
        ProductionIssue issue = new ProductionIssue();
        issue.setTitle(title);
        issue.setDescription(description);
        issue.setCategory(category);
        issue.setSeverity(severity);
        issue.setStatus(status);
        issue.setDowntimeMinutes(downtime);
        issue.setAssignedTeam(assignedTeam);
        issue.setAssignedTo(assignedTo);
        issue.setSource(source);
        issue.setNotificationChannel(channel);
        issue.setMachine(machine);
        issue.setWorkOrder(order);
        issue.setCreatedAt(Instant.now().minus(downtime, ChronoUnit.MINUTES));
        return issue;
    }

    private static void addSystemActivity(ProductionIssue issue, String author, String message, int minutesAfterCreated) {
        IssueActivity activity = new IssueActivity();
        activity.setProductionIssue(issue);
        activity.setType(IssueActivityType.SYSTEM);
        activity.setCreatedBy(author);
        activity.setMessage(message);
        activity.setCreatedAt(issue.getCreatedAt().plus(minutesAfterCreated, ChronoUnit.MINUTES));
        issue.getActivities().add(activity);
    }
}
