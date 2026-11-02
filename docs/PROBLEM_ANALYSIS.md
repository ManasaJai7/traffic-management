# Problem analysis
**Problem:** emergency vehicles lose time when roads close or congest. Operators need to keep the road network
data, queue urgent requests, react to traffic events and obtain the fastest route, with a permanent record.

**Inputs:** junctions, directed roads with travel times, vehicles, signals, routing requests (vehicle, destination, severity), traffic events.
**Processing:** apply events FIFO -> order requests by severity then arrival -> Dijkstra on open roads -> save results.
**Outputs:** route path and minutes, UNREACHABLE status, map highlight, saved history, CSV reports.
**Constraints:** travel time > 0; a road joins two different junctions; IDs unique; vehicles cannot be left without a junction (foreign keys).
**Why a database:** data survives restarts, relationships are enforced by constraints, history can be searched and exported.
