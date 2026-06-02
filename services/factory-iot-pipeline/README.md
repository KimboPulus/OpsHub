# Factory IoT Sensor Pipeline

A small Python/FastAPI service designed to simulate telemetry data from production machinery.

This sub-project is separated from the main OpsHub system because it serves a different purpose: it mimics the IoT layer, collects machine readings, detects simple anomalies, and exposes data that the operational application can display to the shift supervisor.

## Core Features

* **Machine Data Generation:** Simulates temperature, vibrations, cycle time, energy consumption, status, and error codes.
* **Time-Series Storage:** Saves telemetry data sequentially over time.
* **Rule-Based Anomaly Detection:** Identifies simple irregularities automatically based on predefined thresholds.
* **Daily KPIs:** Tracks OEE (Overall Equipment Effectiveness), availability, production output per shift, and energy consumption per unit.
* **Power BI Export:** Generates CSV exports tailored for Power BI reporting.
* **OpsHub Integration:** Connects to the main system via a lightweight Spring Boot gateway.

## Why a Separate Service?

**OpsHub** serves as the operational application for the factory floor—handling tickets, downtime, work orders, comments, and reports. 

The **Factory IoT Sensor Pipeline**, on the other hand, acts strictly as the machine data layer. This separation allows Python to handle the heavy lifting for IoT and data analysis, while Java remains the central hub where the user interacts with the final results and makes decisions.

This project is intentionally kept lightweight. It doesn't attempt to mimic a massive industrial platform; instead, it demonstrates the core pipeline: a machine transmits data, the backend stores it and flags potential risks, and the dashboard displays meaningful KPIs.

## Main Endpoints

* `GET /machines`
* `POST /telemetry`
* `POST /simulator/tick`
* `GET /machines/{id}/telemetry`
* `GET /machines/{id}/anomalies`
* `GET /oee/daily`
* `GET /alerts/open`
* `GET /dashboard/summary`
* `GET /exports/powerbi.csv`
