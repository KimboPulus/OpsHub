package pl.fortaco.opshub.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Column;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
public class ProductionIssue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String title = "";
    @Column(length = 1200)
    private String description = "";

    @Enumerated(EnumType.STRING)
    private IssueCategory category = IssueCategory.MACHINE_FAILURE;

    @Enumerated(EnumType.STRING)
    private IssueSeverity severity = IssueSeverity.MEDIUM;

    @Enumerated(EnumType.STRING)
    private IssueStatus status = IssueStatus.NEW;

    private int downtimeMinutes;
    private String assignedTeam = "Nieprzypisane";
    private String assignedTo = "";
    private String source = "Operator";
    private String notificationChannel = "Panel produkcji";
    private Instant createdAt = Instant.now();
    private Instant resolvedAt;

    @ManyToOne
    private Machine machine;

    @ManyToOne
    private WorkOrder workOrder;

    @OneToMany(mappedBy = "productionIssue", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IssueActivity> activities = new ArrayList<>();

    @OneToMany(mappedBy = "productionIssue", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IssueAttachment> attachments = new ArrayList<>();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public IssueCategory getCategory() {
        return category;
    }

    public void setCategory(IssueCategory category) {
        this.category = category;
    }

    public IssueSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(IssueSeverity severity) {
        this.severity = severity;
    }

    public IssueStatus getStatus() {
        return status;
    }

    public void setStatus(IssueStatus status) {
        this.status = status;
    }

    public int getDowntimeMinutes() {
        return downtimeMinutes;
    }

    public void setDowntimeMinutes(int downtimeMinutes) {
        this.downtimeMinutes = downtimeMinutes;
    }

    public String getAssignedTeam() {
        return assignedTeam;
    }

    public void setAssignedTeam(String assignedTeam) {
        this.assignedTeam = assignedTeam;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getNotificationChannel() {
        return notificationChannel;
    }

    public void setNotificationChannel(String notificationChannel) {
        this.notificationChannel = notificationChannel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Machine getMachine() {
        return machine;
    }

    public void setMachine(Machine machine) {
        this.machine = machine;
    }

    public WorkOrder getWorkOrder() {
        return workOrder;
    }

    public void setWorkOrder(WorkOrder workOrder) {
        this.workOrder = workOrder;
    }

    public List<IssueActivity> getActivities() {
        return activities;
    }

    public void setActivities(List<IssueActivity> activities) {
        this.activities = activities;
    }

    public List<IssueAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<IssueAttachment> attachments) {
        this.attachments = attachments;
    }
}
