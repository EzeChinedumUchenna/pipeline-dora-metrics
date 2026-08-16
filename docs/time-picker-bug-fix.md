# Dashboard Time Picker Bug Fix

## Affected version

The issue was identified in Pipeline DORA Metrics plugin version `35.v22727d6ff968`, commit `22727d6ff968cbb51e2349a36b5ae5ae7a2100df`.

## Bug

The dashboard time picker updated only the trends request, such as `GET /dora-api/trends?days=7`. The DORA KPI cards and pipeline-ranking tables remained on the initial 30-day data, and CSV export ignored the selected range. The backend already returned correct range-specific overview data.

## Root cause

The date-picker JavaScript only requested `/dora-api/trends?days=<selected-days>`. The overview cards and rankings were server-rendered at page load with a 30-day period and had no browser-side refresh path. The export link was static.

## Fix

Selecting a preset or custom range now updates each dashboard area without reloading the page.

| Dashboard area | Request |
| --- | --- |
| DORA KPI overview cards | `GET /dora-api/overview?days=<selected-days>` |
| Trend charts and sparklines | `GET /dora-api/trends?days=<selected-days>` |
| Pipeline rankings | `GET /dora-api/pipelines?days=<selected-days>&limit=<dashboard-top-N>` |
| CSV export | `GET /dora-api/export?days=<selected-days>&format=csv` |

The pipeline rankings refreshed by the picker are Slowest Pipelines, Most Failing Pipelines, Most Improved, and Flakiest Pipelines. The pipelines API now returns `most_improved`, using the existing comparison of the selected period with the immediately preceding period of equal length.

## Calculations not changed

No DORA KPI formula or existing pipeline-ranking formula changed. The fix passes the selected date range to the existing backend calculations and renders the returned data in the dashboard.

## Files changed

- `src/main/webapp/js/dora-dashboard.js`: selected-range requests and browser rendering.
- `src/main/resources/io/jenkins/plugins/dorametrics/ui/DoraDashboardAction/index.jelly`: dynamic card, ranking, and export DOM targets.
- `src/main/java/io/jenkins/plugins/dorametrics/ui/DoraApiAction.java`: `most_improved` pipelines response.
- `src/main/java/io/jenkins/plugins/dorametrics/ui/DoraDashboardAction.java`: configured ranking limit exposed to the client.
- `src/test/java/io/jenkins/plugins/dorametrics/ui/DoraDashboardResourceTest.java`: regression coverage.

## Verification

Run:

```powershell
mvn -Dtest=DoraDashboardResourceTest test
mvn clean package
```

After installing `target/pipeline-dora-metrics.hpi`, use the browser Network tab while selecting 7d, 30d, or 90d. The `overview`, `trends`, `pipelines`, and CSV export requests should use the same selected `days` value.
