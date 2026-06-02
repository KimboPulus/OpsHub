# Factory IoT Sensor Pipeline

Mały serwis Python/FastAPI do symulowania telemetryki z maszyn produkcyjnych.

Ten podprojekt jest oddzielony od głównego OpsHub, bo robi inną rzecz: udaje warstwę IoT, zbiera odczyty z maszyn, wykrywa proste anomalie i wystawia dane, które aplikacja operacyjna może pokazać liderowi zmiany.

## Co pokazuje

- generowanie danych z maszyn: temperatura, drgania, czas cyklu, zużycie energii, status, kody błędów
- zapis danych telemetrycznych jako time-series
- proste wykrywanie anomalii regułami
- dzienne KPI: OEE, dostępność, produkcja według zmiany, energia na sztukę
- eksport CSV pod Power BI
- integrację z OpsHub przez mały gateway w Spring Boot

## Dlaczego osobny serwis

OpsHub jest aplikacją operacyjną dla produkcji: zgłoszenia, przestoje, zlecenia, komentarze, raporty.

Factory IoT Sensor Pipeline jest warstwą danych maszynowych. Dzięki temu Python odpowiada za IoT/analitykę, a Java zostaje miejscem, w którym użytkownik widzi efekt i podejmuje decyzje.

To jest celowo mały projekt. Nie udaje pełnej platformy przemysłowej, tylko pokazuje najważniejszy przepływ: maszyna wysyła dane, backend je zapisuje, wykrywa ryzyko, a dashboard pokazuje sensowne KPI.

## Główne endpointy

- `GET /machines`
- `POST /telemetry`
- `POST /simulator/tick`
- `GET /machines/{id}/telemetry`
- `GET /machines/{id}/anomalies`
- `GET /oee/daily`
- `GET /alerts/open`
- `GET /dashboard/summary`
- `GET /exports/powerbi.csv`

## PyCharm

Plik `run.py` jest prostym entrypointem do uruchomienia serwisu z IDE. Dzięki temu projekt można otworzyć w PyCharmie i odpalić go przyciskiem Play bez pamiętania komendy `uvicorn`.
