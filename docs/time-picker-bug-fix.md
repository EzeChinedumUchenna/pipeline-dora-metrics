# Dashboard Time Picker Bug Fix

## Affected version

The issue was identified in Pipeline DORA Metrics plugin version `35.v22727d6ff968`, commit `22727d6ff968cbb51e2349a36b5ae5ae7a2100df`.

## Bug

The dashboard time picker correctly updated the trends request. For example, selecting **7d** sent:

```text
GET /dora-api/trends?days=7
```

However, the DORA KPI overview cards still displayed values calculated for the default 30-day period. Pipeline-ranking tables also continued to display their initially rendered 30-day data. In addition, CSV export did not use the selected date range.

The backend endpoints already supported the `days` parameter and returned different, correct values for the requested periods:

```text
GET /dora-api/overview?days=7
GET /dora-api/overview?days=30
GET /dora-api/overview?days=90
```

## Root cause

The dashboard JavaScript function responsible for date-range changes only fetched trend data:

```text
/dora-api/trends?days=<selected-days>
```

The overview cards and rankings were rendered only when Jenkins first loaded the dashboard page. Their server-side data methods used the default 30-day window, and no browser-side request replaced that initial data after the picker changed. The export link was also static.

## Fix implemented

The selected number of days is now used for every date-sensitive dashboard element. When a user selects a preset range or applies a custom date range, the dashboard updates without a page reload.

| Dashboard element | Request after a range change |
| --- | --- |
| DORA KPI overview cards | `GET /dora-api/overview?days=<selected-days>` |
| Trend charts and card sparklines | `GET /dora-api/trends?days=<selected-days>` |
| Pipeline rankings | `GET /dora-api/pipelines?days=<selected-days>&limit=<dashboard-top-N>` |
| CSV export | `GET /dora-api/export?days=<selected-days>&format=csv` |

The overview card value, DORA band, and band colour are updated from the overview API response. The ranking table bodies are rebuilt from the pipelines API response while keeping each pipeline name as a link to its job-level DORA metrics page.

## Pipeline rankings

The time picker now refreshes all four ranking tables:

- Slowest Pipelines
- Most Failing Pipelines
- Most Improved
- Flakiest Pipelines

The `GET /dora-api/pipelines` response was extended with `most_improved` because the dashboard needed that data to refresh the corresponding table. For a selected period of `N` days, this ranking compares that period with the immediately preceding `N`-day period. This uses the existing `PipelineRanker.mostImproved` behavior and matches the previous fixed 30-day dashboard comparison.

## Calculations not changed

No DORA KPI formula was changed. No existing pipeline-ranking formula was changed. The fix only passes the selected date range to existing backend calculations and renders their responses in the dashboard.

## Files changed

- `src/main/webapp/js/dora-dashboard.js` — requests and renders selected-range overview data, rankings, and export URL.
- `src/main/resources/io/jenkins/plugins/dorametrics/ui/DoraDashboardAction/index.jelly` — adds DOM targets for overview cards, ranking table bodies, and CSV export.
- `src/main/java/io/jenkins/plugins/dorametrics/ui/DoraApiAction.java` — returns `most_improved` from the pipelines API.
- `src/main/java/io/jenkins/plugins/dorametrics/ui/DoraDashboardAction.java` — exposes the configured ranking-row limit to the dashboard.
- `src/test/java/io/jenkins/plugins/dorametrics/ui/DoraDashboardResourceTest.java` — regression coverage for selected-range request paths and dynamic dashboard targets.

## Verification

Build and run the focused regression test:

```powershell
mvn -Dtest=DoraDashboardResourceTest test
mvn clean package
```

After installing `target/pipeline-dora-metrics.hpi`, open the DORA dashboard and use the browser Network tab to verify that selecting 7d, 30d, or 90d updates the `overview`, `trends`, and `pipelines` requests to the same `days` value. Click **Export CSV** and verify that its request uses that selected value too.
