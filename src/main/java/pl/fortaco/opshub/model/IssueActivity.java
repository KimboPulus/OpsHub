package pl.fortaco.opshub.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Column;

import java.time.Instant;

@Entity
public class IssueActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @JsonIgnore
    @ManyToOne(optional = false)
    private ProductionIssue productionIssue;

    @Enumerated(EnumType.STRING)
    private IssueActivityType type = IssueActivityType.SYSTEM;

    @Column(length = 1200)
    private String message = "";
    private String createdBy = "System";
    private Instant createdAt = Instant.now();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public ProductionIssue getProductionIssue() {
        return productionIssue;
    }

    public void setProductionIssue(ProductionIssue productionIssue) {
        this.productionIssue = productionIssue;
    }

    public IssueActivityType getType() {
        return type;
    }

    public void setType(IssueActivityType type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
