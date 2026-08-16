package io.jenkins.plugins.dorametrics.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class DoraDashboardResourceTest {

    @Test
    public void dateRangeRefreshesOverviewTrendsRankingsAndExport() throws IOException {
        String script = Files.readString(Path.of("src", "main", "webapp", "js", "dora-dashboard.js"),
                StandardCharsets.UTF_8);
        assertTrue(script.contains("loadOverview(base, days);"));
        assertTrue(script.contains("loadPipelineRankings(base, days);"));
        assertTrue(script.contains("updateExportUrl(base, days);"));
        assertTrue(script.contains("'/dora-api/overview?days=' + days"));
        assertTrue(script.contains("'/dora-api/trends?days=' + days"));
        assertTrue(script.contains("'/dora-api/pipelines?days=' + days + '&limit=' + limit"));
        assertTrue(script.contains("'/dora-api/export?days=' + days + '&format=csv'"));
    }

    @Test
    public void dashboardExposesDynamicUpdateTargets() throws IOException {
        String dashboard = resource("/io/jenkins/plugins/dorametrics/ui/DoraDashboardAction/index.jelly");
        for (String metric : new String[] {"df", "lt", "mttr", "cfr"}) {
            assertTrue(dashboard.contains("id=\"dora-value-" + metric + "\""));
            assertTrue(dashboard.contains("id=\"dora-band-" + metric + "\""));
        }
        for (String ranking : new String[] {"slowest", "most-failing", "most-improved", "flakiest"}) {
            assertTrue(dashboard.contains("id=\"dora-rank-" + ranking + "\""));
        }
        assertTrue(dashboard.contains("id=\"dora-pipeline-rankings\""));
        assertTrue(dashboard.contains("id=\"dora-export-csv\""));
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
