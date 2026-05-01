# Pipeline DORA Metrics

A Jenkins plugin that tracks all four DORA metrics, pipeline analytics, rankings, and performance trends. No external infrastructure required.

![Dashboard Overview](docs/dora-metrics-dashboard.png)

## Features

**DORA Metrics (All 4)**
- Deployment Frequency with DORA band rating (Elite/High/Medium/Low)
- Lead Time for Changes (commit to deploy)
- Mean Time to Restore (failure to recovery)
- Change Failure Rate (% failed deployments)

**Pipeline Rankings**

![Pipeline Rankings](docs/Pipeline-rankings.png)

- Slowest pipelines by average duration
- Most failing pipelines by failure rate
- Most improved (month-over-month duration change)
- Flakiest pipelines (pass-fail-pass pattern detection)

**Stage-Level Insights**

![Stage Analytics](docs/stage-analysis.png)

- Slowest stages across all pipelines
- Most failing stages by failure rate

**Dashboard**
- Interactive Chart.js trend charts (build volume, duration over time)
- Sparkline trends on each DORA metric card
- Date range picker (7d / 30d / 90d / 180d / 1y / custom date range)
- Collapsible sections
- Job drill-down links (click any pipeline to see its per-job metrics)
- Per-job DORA Metrics tab on every job page
- CSV and JSON export

**Per-Job Metrics**

![Per-Job Metrics](docs/per-job-dora.png)

**Configuration**

![Configuration](docs/manage-jenkins-config.png)

- Production job pattern (regex)
- Excluded job pattern (regex)
- Folder-based job selection
- Branch filtering (main/master or custom)
- Customizable DORA band thresholds
- Configurable data retention period
- External storage export settings (S3, GCS, Azure, HTTP)

**REST API**
```
GET /dora-api/overview?days=30          All 4 DORA metrics
GET /dora-api/pipelines?days=30&limit=10   Pipeline rankings
GET /dora-api/trends?days=90&job=my-pipeline   Time-series trend data
GET /dora-api/export?days=90&format=csv    CSV/JSON bulk export
```

## Requirements

- **Jenkins:** 2.541.3 LTS or later
- **Java:** 21 or later

## Jenkins Version Compatibility

| Jenkins Version | Compatible | Notes |
|----------------|-----------|-------|
| < 2.440 | No | Requires Java 21 and Stapler2 APIs |
| 2.440 - 2.540 | Untested | May work but not officially supported |
| **2.541.x LTS** | Yes | Built and tested against this version |
| 2.555.x LTS | Yes | Forward compatible |
| Future LTS releases | Expected | Jenkins maintains backward compatibility |

The plugin is built against the Jenkins LTS baseline `2.541.3` and uses the Jenkins BOM `bom-2.541.x`. It requires Java 21 (the Jenkins standard since 2.440+). The Stapler2 request/response APIs used by this plugin were introduced in recent Jenkins versions, so older Jenkins installations (pre-2.440) are not supported.

## Installation

**From Jenkins Plugin Manager (after publishing):**
1. Go to Manage Jenkins > Plugins > Available plugins
2. Search for "Pipeline DORA Metrics"
3. Check the box and click Install
4. Restart Jenkins when prompted

**Manual install:**
1. Download the `.hpi` file from the [Releases](https://github.com/Bisman-Singh/pipeline-dora-metrics/releases) page
2. Go to Manage Jenkins > Plugins > Advanced > Deploy Plugin
3. Upload the `.hpi` file
4. Restart Jenkins

A restart is required after installation. Once restarted, the plugin begins working immediately with zero configuration.

**Important:** The plugin only tracks builds that complete **after** installation. Existing build history is not retroactively imported. The dashboard will show N/A until the first build runs with the plugin active.

## Build from Source

```bash
# Requires Java 21 and Maven 3.9+
mvn package -DskipTests
```

The plugin file will be at `target/pipeline-dora-metrics.hpi`.

## How It Works

The plugin uses a `RunListener` to automatically capture build data after every pipeline completes. Data is stored in an embedded SQLite database at `JENKINS_HOME/pipeline-dora-metrics/metrics.db`. No external database setup required.

**Data captured per build:**
- Job name, build number, timestamp, duration, result
- Trigger type (user, SCM, timer, upstream)
- Branch name (from environment variables)
- Stage-level duration and result for each pipeline stage
- SCM commit data for lead time calculation

**Storage:** ~200 bytes per build with stages. 100 builds/day for a year is approximately 7MB.

| Scale | Builds/day | Storage/year |
|-------|-----------|--------------|
| Small team (10 jobs) | 50 | ~3.5MB |
| Medium (50 jobs) | 200 | ~14MB |
| Large (200 jobs) | 1000 | ~70MB |
| Enterprise (1000 jobs) | 5000 | ~350MB |

## Configuration

Navigate to **Manage Jenkins > System** and scroll to the **Pipeline DORA Metrics** section.

**Job Filtering:**
- **Production Job Pattern:** Regex to match production jobs (e.g., `production/.*` or `.*-prod.*`). Default: `.*` (all jobs)
- **Excluded Job Pattern:** Regex to exclude jobs (e.g., `.*-test.*|.*sandbox.*`)
- **Production Folders:** Comma-separated Jenkins folder paths (e.g., `production,deploy/prod`)
- **Production Branch Pattern:** Regex for branches that count as production (e.g., `main|master|release/.*`)

**DORA Thresholds:** Customize the Elite/High/Medium/Low band boundaries for each metric to match your team's standards.

## Architecture

```
io.jenkins.plugins.dorametrics/
├── collectors/
│   └── BuildDataCollector      # RunListener - captures builds, stages, commits
├── dora/
│   └── DoraCalculator          # Computes all 4 DORA metrics (SQL-optimized)
├── rankings/
│   └── PipelineRanker          # Pipeline and stage rankings (SQL aggregates)
├── store/
│   └── MetricsStore            # SQLite database
├── ui/
│   ├── DoraApiAction           # REST API at /dora-api/ (auth-protected)
│   ├── DoraDashboardAction     # Dashboard UI at /dora-metrics/
│   ├── DoraDashboardLink       # Manage Jenkins sidebar link
│   └── JobMetricsAction        # Per-job metrics tab
├── util/
│   └── DurationFormatter       # Shared duration formatting
└── DoraGlobalConfiguration     # Plugin settings (secrets encrypted)
```

## Security

- All API endpoints require Jenkins READ permission
- Export secret keys are stored using Jenkins `Secret` class (encrypted on disk)
- SQL queries use parameterized statements (no SQL injection)
- CSV export protects against CSV injection attacks
- SQL ORDER BY clauses are whitelisted (not user-controlled)

## Roadmap

**v1.1 (Planned)**
- External storage export (AWS S3, GCS, Azure Blob, Backblaze B2) with proper SDK auth and IAM role support
- Historical build import (backfill metrics from existing Jenkins build history)
- Grafana dashboard template (JSON) that consumes the REST API

**v1.2 (Planned)**
- Month-over-month comparison view (side-by-side metrics)
- DORA band progression chart (track your team's improvement over time)
- Stage failure heatmap visualization
- Webhook notifications when DORA bands change (Slack, Teams, email)

**v2.0 (Future)**
- Multi-controller aggregation (combine metrics across Jenkins instances)
- Team/group-level DORA metrics (assign jobs to teams)
- GitHub Actions and GitLab CI support (beyond Jenkins)
- Connection pooling with HikariCP for enterprise scale

## Contributing

1. Fork the repository
2. Create a feature branch
3. Write tests for new functionality
4. Run `mvn test` (all 65 tests must pass)
5. Submit a pull request

## License

MIT
