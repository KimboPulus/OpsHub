package pl.fortaco.opshub.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@RestController
@RequestMapping("/api/iot")
public class IoTGatewayController {
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();
    private final String baseUrl;

    public IoTGatewayController(@Value("${opshub.iot.base-url:http://localhost:8000}") String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @GetMapping(value = "/dashboard/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> dashboardSummary() {
        return json(get("/dashboard/summary"));
    }

    @GetMapping(value = "/oee/daily", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> dailyOee(@RequestParam(required = false) String day) {
        String path = day == null || day.isBlank() ? "/oee/daily" : "/oee/daily?day=" + day.trim();
        return json(get(path));
    }

    @GetMapping(value = "/alerts/open", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> openAlerts() {
        return json(get("/alerts/open"));
    }

    @GetMapping(value = "/exports/powerbi.csv", produces = "text/csv")
    public ResponseEntity<String> powerBiCsv() {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(get("/exports/powerbi.csv"));
    }

    @GetMapping(value = "/machines/{id}/telemetry", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> machineTelemetry(@PathVariable int id, @RequestParam(defaultValue = "100") int limit) {
        return json(get("/machines/" + id + "/telemetry?limit=" + Math.max(1, Math.min(limit, 500))));
    }

    @GetMapping(value = "/machines/{id}/anomalies", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> machineAnomalies(@PathVariable int id) {
        return json(get("/machines/" + id + "/anomalies"));
    }

    @PostMapping(value = "/simulator/tick", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> simulatorTick(@RequestParam(defaultValue = "1") int count) {
        return json(post("/simulator/tick?count=" + Math.max(1, Math.min(count, 200))));
    }

    private ResponseEntity<String> json(String body) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(body);
    }

    private String get(String path) {
        return send("GET", path);
    }

    private String post(String path) {
        return send("POST", path);
    }

    private String send(String method, String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(6))
            .method(method, method.equals("POST") ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.noBody())
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            throw new ResponseStatusException(BAD_GATEWAY, "Factory IoT service returned HTTP " + response.statusCode());
        } catch (IOException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Factory IoT service is not reachable at " + baseUrl, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(BAD_GATEWAY, "Factory IoT request was interrupted", ex);
        }
    }
}
