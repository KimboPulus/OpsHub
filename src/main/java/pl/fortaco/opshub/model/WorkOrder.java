package pl.fortaco.opshub.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
public class WorkOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String sapOrderNumber = "";
    private String materialCode = "";
    private String materialDescription = "";
    private int plannedQuantity;
    private Instant dueDate;

    @Enumerated(EnumType.STRING)
    private WorkOrderStatus status = WorkOrderStatus.PLANNED;

    @JsonIgnore
    @OneToMany(mappedBy = "workOrder")
    private List<ProductionIssue> issues = new ArrayList<>();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSapOrderNumber() {
        return sapOrderNumber;
    }

    public void setSapOrderNumber(String sapOrderNumber) {
        this.sapOrderNumber = sapOrderNumber;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public void setMaterialCode(String materialCode) {
        this.materialCode = materialCode;
    }

    public String getMaterialDescription() {
        return materialDescription;
    }

    public void setMaterialDescription(String materialDescription) {
        this.materialDescription = materialDescription;
    }

    public int getPlannedQuantity() {
        return plannedQuantity;
    }

    public void setPlannedQuantity(int plannedQuantity) {
        this.plannedQuantity = plannedQuantity;
    }

    public Instant getDueDate() {
        return dueDate;
    }

    public void setDueDate(Instant dueDate) {
        this.dueDate = dueDate;
    }

    public WorkOrderStatus getStatus() {
        return status;
    }

    public void setStatus(WorkOrderStatus status) {
        this.status = status;
    }

    public List<ProductionIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<ProductionIssue> issues) {
        this.issues = issues;
    }
}
