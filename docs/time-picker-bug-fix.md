# Dashboard Time Picker Bug Fix...

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

## Excluded-job filtering for historical data

### Bug

The **Excluded Job Pattern** setting was checked only when a build completed. It correctly prevented future matching builds from being inserted into the metrics database, but records collected before the exclusion was configured remained in the database and could still appear in dashboard results.

For example, with an exclusion pattern containing `.*-pre-prod.*`, the job `data-sync-service-pre-prod` matched the exclusion rule but could still appear in a 30-day pipeline ranking when it had historical records in that period.

### Fix

The plugin now applies `DoraGlobalConfiguration.shouldTrackJob(jobName)` when it reads stored data for global dashboard and API results. This uses the same production, excluded-job, and folder rules used during build collection.

Historical records are retained in SQLite, but excluded jobs no longer affect or appear in:

- DORA KPI overview calculations
- Trends and sparklines
- Pipeline rankings and stage analytics
- CSV/JSON API exports
- Scheduled cloud-export snapshots

No build data is deleted. If an excluded job is later included again, its retained historical data becomes available according to the current configuration and selected time range.

## Calculations not changed

No DORA KPI formula or existing pipeline-ranking formula changed. The fixes change the selected date range and eligible jobs passed to existing calculations, then render the returned data in the dashboard.

## Files changed

- `src/main/webapp/js/dora-dashboard.js`: selected-range requests and browser rendering.
- `src/main/resources/io/jenkins/plugins/dorametrics/ui/DoraDashboardAction/index.jelly`: dynamic card, ranking, and export DOM targets.
- `src/main/java/io/jenkins/plugins/dorametrics/ui/DoraApiAction.java`: `most_improved` pipelines response.
- `src/main/java/io/jenkins/plugins/dorametrics/ui/DoraDashboardAction.java`: configured ranking limit and read-time job filtering for the initial dashboard render.
- `src/main/java/io/jenkins/plugins/dorametrics/dora/DoraCalculator.java`: read-time job filtering for global DORA KPI calculations.
- `src/main/java/io/jenkins/plugins/dorametrics/rankings/PipelineRanker.java`: read-time job filtering for rankings and stage analytics.
- `src/main/java/io/jenkins/plugins/dorametrics/store/MetricsExporter.java`: read-time job filtering for scheduled exports.
- `src/test/java/io/jenkins/plugins/dorametrics/ui/DoraDashboardResourceTest.java`: regression coverage.
- `src/test/java/io/jenkins/plugins/dorametrics/dora/DoraCalculatorTest.java` and `src/test/java/io/jenkins/plugins/dorametrics/rankings/PipelineRankerTest.java`: historical exclusion coverage.

## Verification

Run:

```powershell
mvn -Dtest=DoraDashboardResourceTest test
mvn '-Dtest=DoraCalculatorTest,PipelineRankerTest' test
mvn clean package
```

After installing `target/pipeline-dora-metrics.hpi`, use the browser Network tab while selecting 7d, 30d, or 90d. The `overview`, `trends`, `pipelines`, and CSV export requests should use the same selected `days` value.

To verify exclusion filtering, save an Excluded Job Pattern such as `.*-pre-prod.*`, refresh the dashboard, and confirm that matching jobs no longer appear in KPI results, trends, rankings, or exports even if they have historical build records within the selected date range.
