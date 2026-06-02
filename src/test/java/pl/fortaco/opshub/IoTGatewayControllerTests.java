package pl.fortaco.opshub;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pl.fortaco.opshub.web.IoTGatewayController;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IoTGatewayControllerTests {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void dashboardSummaryProxiesJsonFromPythonService() throws IOException {
        startServer();
        json("/dashboard/summary", """
            {"oee":{"oee":82.5},"openAlerts":[],"latestTelemetry":[],"recentAnomalies":[]}
            """);

        IoTGatewayController controller = new IoTGatewayController(baseUrl());

        var response = controller.dashboardSummary();

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("\"oee\":82.5"));
    }

    @Test
    void simulatorTickUsesPostRequest() throws IOException {
        startServer();
        server.createContext("/simulator/tick", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            byte[] body = "{\"created\":20}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        IoTGatewayController controller = new IoTGatewayController(baseUrl());

        var response = controller.simulatorTick(5);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("\"created\":20"));
    }

    @Test
    void powerBiCsvKeepsCsvBody() throws IOException {
        startServer();
        text("/exports/powerbi.csv", "machine_code,temperature\nWLD-01,72.4\n");

        IoTGatewayController controller = new IoTGatewayController(baseUrl());

        var response = controller.powerBiCsv();

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("machine_code"));
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void json(String path, String body) {
        respond(path, body, "application/json");
    }

    private void text(String path, String body) {
        respond(path, body, "text/plain");
    }

    private void respond(String path, String body, String contentType) {
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }
}
