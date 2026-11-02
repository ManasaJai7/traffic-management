# Smart Traffic Management & Emergency Vehicle Routing

Java Swing desktop application using **MySQL 8.0.16+** through JDBC.

## 1. Requirements
- JDK 11 or newer
- MySQL Server 8.0.16 or newer (CHECK constraints are enforced from 8.0.16)
- MySQL Connector/J 8.x or 9.x

## 2. Create the database
Start MySQL, then run this SQL in MySQL Workbench or the MySQL command line:

```sql
CREATE DATABASE IF NOT EXISTS smart_traffic
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

The application creates its tables and inserts demo data automatically on first start.

## 3. Add the JDBC driver
Download **MySQL Connector/J** from the official MySQL site:
https://dev.mysql.com/downloads/connector/j/

Copy the downloaded `mysql-connector-j-*.jar` file into this project's `lib/` folder.
The old `sqlite-jdbc-*.jar` is no longer used and can be removed.

## 4. Configure login
Defaults: host `localhost`, port `3306`, database `smart_traffic`, user `root`, blank password.

Set these environment variables before running if your MySQL settings differ:
- `TRAFFIC_DB_HOST`
- `TRAFFIC_DB_PORT`
- `TRAFFIC_DB_NAME`
- `TRAFFIC_DB_USER`
- `TRAFFIC_DB_PASSWORD`

You can also override them with JVM properties:
`-Dtraffic.db.host=localhost -Dtraffic.db.port=3306 -Dtraffic.db.name=smart_traffic -Dtraffic.db.user=root -Dtraffic.db.password=your_password`

## 5. Run
Windows: double-click `run.bat`.
Mac/Linux: `./run.sh`.
Tests: `run-tests.bat` (or compile and run `CoreSmokeTest`).

The demo city contains 12 junctions, 34 directed roads, 4 vehicles and 12 signals.
Use **Reset demo data** in the sidebar to restore the demo city.

## Screens and tables
| Screen | What you can do | Tables |
|---|---|---|
| Dashboard | live counts, city map, latest routes and events | all |
| Routing Console | queue requests, queue traffic events, process, see routes on the map, move a vehicle | RoutingRequest, TrafficEvent, RouteHistory, Road, Vehicle |
| Junctions | add / edit / delete, click-to-place on a mini map | Junction |
| Roads | add (optionally both directions) / edit / delete | Road |
| Vehicles | add / edit / delete | Vehicle |
| Traffic Signals | add / edit / delete / cycle colour (shown on the map) | TrafficSignal |
| History & Reports | search, export CSV, clear | RouteHistory, TrafficEvent |

## Database design (MySQL / InnoDB)
`Junction` is the parent for roads, vehicles, signals and routing requests.
`Road` has a composite primary key `(from_junction, to_junction)`.
`TrafficEvent` references a directed road. `RouteHistory` deliberately has no foreign keys so the audit trail survives deletions.

## How it works (DSA + OOP)
- Dijkstra + min-heap (`RoutePlanner`) finds the fastest route and skips closed roads.
- `PriorityQueue` orders pending requests: CRITICAL > HIGH > NORMAL, ties by `request_id`.
- FIFO queue (`ArrayDeque`) applies traffic events in arrival order.
- Polymorphism: `TrafficEvent` -> `ClosureEvent`, `ReopenEvent`, `CongestionEvent`.
- Processing runs in one SQL transaction.
- User input is passed through JDBC `PreparedStatement` parameters.
