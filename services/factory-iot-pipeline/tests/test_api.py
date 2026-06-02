from fastapi.testclient import TestClient

from app.main import app


def test_simulator_fills_dashboard():
    with TestClient(app) as client:
        response = client.post("/simulator/tick", params={"count": 3})
        assert response.status_code == 200
        assert response.json()["created"] > 0

        dashboard = client.get("/dashboard/summary")
        assert dashboard.status_code == 200
        body = dashboard.json()
        assert "oee" in body
        assert "latestTelemetry" in body
        assert body["oee"]["production_count"] >= 0


def test_machine_telemetry_and_anomalies_endpoints():
    with TestClient(app) as client:
        client.post("/simulator/tick", params={"count": 1})
        machines = client.get("/machines").json()
        machine_id = machines[0]["id"]

        telemetry = client.get(f"/machines/{machine_id}/telemetry")
        anomalies = client.get(f"/machines/{machine_id}/anomalies")

        assert telemetry.status_code == 200
        assert anomalies.status_code == 200
        assert isinstance(telemetry.json(), list)
        assert isinstance(anomalies.json(), list)


def test_powerbi_export_is_csv():
    with TestClient(app) as client:
        client.post("/simulator/tick", params={"count": 1})
        response = client.get("/exports/powerbi.csv")

        assert response.status_code == 200
        assert "machine_code" in response.text
        assert "temperature" in response.text
