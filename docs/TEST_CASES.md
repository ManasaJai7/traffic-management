# Test cases
Automated: run-tests.bat (CoreSmokeTest, 19 checks). Manual cases below.

| ID | Test | Steps | Expected |
|---|---|---|---|
| TC01 | Valid route | Routing Console: AMB-01 -> J12, HIGH, add, Process | Route found, path on map, row in History |
| TC02 | Severity priority | Add NORMAL then CRITICAL, view Pending Requests | CRITICAL listed first; processed first |
| TC03 | FIFO events | Queue closure then congestion, view Queued Events | Order #1, #2; applied in that order |
| TC04 | Reroute | Close a road on the shortest path, process | Alternative path avoiding red dashed road |
| TC05 | Unreachable | Close every road out of a vehicle's junction | Status NO ROUTE, no path |
| TC06 | Persistence | Close a road, process, exit, start again | Road still closed (read from traffic.db) |
| TC07 | Add junction | Junctions: id J13, name, click map, Add | Row appears; also selectable in Roads/Vehicles |
| TC08 | Duplicate id | Add J13 again | Message "A record with this ID already exists" |
| TC09 | Delete with vehicle | Delete J1 while AMB-01 is there | Blocked with explanation |
| TC10 | Delete cascade | Delete an empty junction | Its roads and signals disappear |
| TC11 | Invalid time | Roads: travel time 0 or text | Validation message |
| TC12 | Signal cycle | Signals: select row, Cycle status | Colour changes in table and on map |
| TC13 | Export | History: Export routes (CSV) | File opens in Excel with all rows |
| TC14 | Reset | Reset demo data | 12 junctions, history empty |
