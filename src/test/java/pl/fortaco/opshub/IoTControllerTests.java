package pl.fortaco.opshub;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pl.fortaco.opshub.iot.repository.IoTAlertRepository;
import pl.fortaco.opshub.iot.repository.IoTAnomalyRepository;
import pl.fortaco.opshub.iot.repository.IoTMachineRepository;
import pl.fortaco.opshub.iot.repository.IoTTelemetryRepository;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:iot-controller-tests;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc(addFilters = false)
class IoTControllerTests {
    @Autowired
    MockMvc mvc;

    @Autowired
    IoTMachineRepository machines;

    @Autowired
    IoTTelemetryRepository telemetry;

    @Autowired
    IoTAnomalyRepository anomalies;

    @Autowired
    IoTAlertRepository alerts;

    @BeforeEach
    void clearReadings() {
        alerts.deleteAll();
        anomalies.deleteAll();
        telemetry.deleteAll();
    }

    @Test
    void machinesEndpointReturnsSeededIoTMachines() throws Exception {
        mvc.perform(get("/api/iot/machines"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(4)))
            .andExpect(jsonPath("$[0].code").value("ASM-04"));
    }

    @Test
    void simulatorFillsDashboard() throws Exception {
        mvc.perform(post("/api/iot/simulator/tick").param("count", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.created").value(12));

        mvc.perform(get("/api/iot/dashboard/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.oee.production_count", greaterThanOrEqualTo(0)))
            .andExpect(jsonPath("$.latestTelemetry", hasSize(12)));
    }

    @Test
    void criticalTelemetryCreatesAnomalyAndAlert() throws Exception {
        Integer machineId = machines.findAllByOrderByCodeAsc().get(0).getId();

        mvc.perform(post("/api/iot/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "machine_id": %d,
                      "vibration": 8.8,
                      "temperature": 91.0,
                      "cycle_time": 60.0,
                      "energy_kwh": 8.0,
                      "produced_units": 2,
                      "status": "RUNNING"
                    }
                    """.formatted(machineId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.machine_id").value(machineId));

        mvc.perform(get("/api/iot/machines/{id}/anomalies", machineId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))));

        mvc.perform(get("/api/iot/alerts/open"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void machineTelemetryEndpointReturnsGeneratedRows() throws Exception {
        Integer machineId = machines.findAllByOrderByCodeAsc().get(0).getId();
        mvc.perform(post("/api/iot/simulator/tick")).andExpect(status().isOk());

        mvc.perform(get("/api/iot/machines/{id}/telemetry", machineId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].machine_id").value(machineId));
    }

    @Test
    void dailyOeeContainsDashboardFields() throws Exception {
        mvc.perform(post("/api/iot/simulator/tick")).andExpect(status().isOk());

        mvc.perform(get("/api/iot/oee/daily"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availability").exists())
            .andExpect(jsonPath("$.performance").exists())
            .andExpect(jsonPath("$.machine_uptime").isArray())
            .andExpect(jsonPath("$.production_by_shift").isArray());
    }

    @Test
    void powerBiExportReturnsCsv() throws Exception {
        mvc.perform(post("/api/iot/simulator/tick")).andExpect(status().isOk());

        mvc.perform(get("/api/iot/exports/powerbi.csv"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/csv"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("machine_code,line")));
    }
}
