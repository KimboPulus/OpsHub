package pl.fortaco.opshub.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Machine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String code = "";
    private String name = "";

    @Enumerated(EnumType.STRING)
    private MachineCriticality criticality = MachineCriticality.LOW;

    @ManyToOne(optional = false)
    private ProductionLine productionLine;

    @JsonIgnore
    @OneToMany(mappedBy = "machine")
    private List<ProductionIssue> issues = new ArrayList<>();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public MachineCriticality getCriticality() {
        return criticality;
    }

    public void setCriticality(MachineCriticality criticality) {
        this.criticality = criticality;
    }

    public ProductionLine getProductionLine() {
        return productionLine;
    }

    public void setProductionLine(ProductionLine productionLine) {
        this.productionLine = productionLine;
    }

    public List<ProductionIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<ProductionIssue> issues) {
        this.issues = issues;
    }
}
