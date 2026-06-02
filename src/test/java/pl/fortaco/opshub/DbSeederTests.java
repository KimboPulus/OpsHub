package pl.fortaco.opshub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pl.fortaco.opshub.model.IssueCategory;
import pl.fortaco.opshub.model.IssueSeverity;
import pl.fortaco.opshub.model.IssueStatus;
import pl.fortaco.opshub.model.ProductionIssue;
import pl.fortaco.opshub.repository.MachineRepository;
import pl.fortaco.opshub.repository.ProductionIssueRepository;
import pl.fortaco.opshub.repository.ProductionLineRepository;
import pl.fortaco.opshub.repository.WorkOrderRepository;
import pl.fortaco.opshub.service.DbSeeder;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:seeder-tests;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DbSeederTests {
    @Autowired
    DbSeeder seeder;

    @Autowired
    ProductionLineRepository lines;

    @Autowired
    MachineRepository machines;

    @Autowired
    WorkOrderRepository workOrders;

    @Autowired
    ProductionIssueRepository issues;

    @Test
    void createsInitialProductionLines() {
        assertEquals(2, lines.count());
    }

    @Test
    void createsInitialMachines() {
        assertEquals(3, machines.count());
    }

    @Test
    void createsInitialWorkOrders() {
        assertEquals(2, workOrders.count());
    }

    @Test
    void createsInitialProductionIssues() {
        assertEquals(3, issues.count());
    }

    @Test
    void secondSeederRunDoesNotDuplicateDemoData() throws Exception {
        seeder.run();

        assertEquals(2, lines.count());
        assertEquals(3, machines.count());
        assertEquals(2, workOrders.count());
        assertEquals(3, issues.count());
    }

    @Test
    void seededIssuesHaveFactoryFloorDefaults() {
        assertTrue(issues.findAll().stream().allMatch(issue ->
            !issue.getAssignedTeam().isBlank()
                && !issue.getSource().isBlank()
                && !issue.getNotificationChannel().isBlank()));
    }

    @Test
    @Transactional
    void seededIssuesKeepMachineAndWorkOrderRelationships() {
        ProductionIssue issue = issues.findAllByOrderByCreatedAtDesc().stream()
            .filter(row -> row.getTitle().contains("Laser"))
            .findFirst()
            .orElseThrow();

        assertEquals("LASER-01", issue.getMachine().getCode());
        assertEquals("45001234", issue.getWorkOrder().getSapOrderNumber());
    }

    @Test
    void laterSeederRunRemovesScratchDemoIssues() throws Exception {
        ProductionIssue scratch = new ProductionIssue();
        scratch.setTitle("asdad");
        scratch.setDescription("Tymczasowy wpis z ręcznego klikania demo.");
        scratch.setCategory(IssueCategory.OTHER);
        scratch.setSeverity(IssueSeverity.LOW);
        scratch.setStatus(IssueStatus.NEW);
        scratch.setCreatedAt(Instant.now());
        issues.save(scratch);

        seeder.run();

        assertFalse(issues.findAll().stream().anyMatch(issue -> issue.getTitle().equalsIgnoreCase("asdad")));
    }
}
