package com.bank.financial.research.thematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bank.financial.research.ResearchReports;
import com.bank.financial.research.data.stub.StubThematicDataSource;
import com.bank.financial.research.engine.ReportBudget;
import com.bank.financial.research.engine.ReportRequest;
import com.bank.financial.research.model.ReportModel;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import org.junit.jupiter.api.Test;

/**
 * Integration layer: the thematic pipeline offline and deterministic. Asserts the
 * computed sub-sector ratings reproduce the intended sector view, the prose stays
 * consistent with them, disclosures are present, and a model outage degrades
 * gracefully (ratings, being computed, survive intact).
 */
class ThematicReportEngineTest {

    private static final long AS_OF = 1_750_000_000_000L;

    private ReportRequest req() {
        return new ReportRequest("中国TMT", "INDUSTRY", "t-test", "zh-CN", AS_OF, ReportBudget.forTest());
    }

    private ThematicReport runOffline() {
        return ResearchReports.thematicOffline(AS_OF).generate(req());
    }

    @Test
    void computesOverallAndSubSectorRatings() {
        ThematicReport r = runOffline();
        assertTrue(r.overallRating().contains("超配"), r.overallRating());
        assertEquals(6, r.subSectors().size());
        // Computed view: semis/CPO/space overweight, software underweight.
        assertTrue(ratingOf(r, "半导体/AI算力").contains("超配"));
        assertTrue(ratingOf(r, "软件/SaaS").contains("低配"));
        assertTrue(ratingOf(r, "互联网平台").contains("标配"));
    }

    @Test
    void allSectionsPresentAndConsistent() {
        ThematicReport r = runOffline();
        assertEquals(7, r.sections().size());
        r.sections().forEach(s -> assertFalse(s.body().isBlank(), "blank: " + s.id()));
        assertTrue(r.metadata().consistencyFindings().isEmpty(),
                () -> "unexpected findings: " + r.metadata().consistencyFindings());
        // Computed scores actually appear in the rendered report.
        assertTrue(r.toMarkdown().contains("0.4242"), "overall score missing");
        assertTrue(r.toMarkdown().contains("1.03"), "semis score missing");
    }

    @Test
    void disclosuresAndNoDegradationOnHealthyRun() {
        ThematicReport r = runOffline();
        assertTrue(r.metadata().complianceNotes().size() >= 5);
        assertTrue(r.metadata().complianceNotes().stream().anyMatch(n -> n.contains("监督分析师")));
        assertTrue(r.metadata().degradations().isEmpty(),
                () -> "unexpected degradations: " + r.metadata().degradations());
    }

    @Test
    void deterministic() {
        assertEquals(runOffline().toMarkdown(), runOffline().toMarkdown());
    }

    @Test
    void modelOutageDegradesButKeepsComputedRatings() {
        ReportModel throwing = new ReportModel() {
            @Override
            public String name() {
                return "throwing";
            }

            @Override
            public String generate(ModelTask task) {
                throw new RuntimeException("simulated outage");
            }
        };
        ThematicReportEngine engine = new ThematicReportEngine(
                new StubThematicDataSource(AS_OF), throwing, null, MemoryObserver.NOOP, () -> AS_OF);
        ThematicReport r = engine.generate(req());

        assertEquals(7, r.sections().size());
        r.sections().forEach(s -> assertFalse(s.body().isBlank()));
        assertTrue(r.overallRating().contains("超配"), "computed rating must survive a model outage");
        assertFalse(r.metadata().degradations().isEmpty(), "outage must be recorded");
    }

    private String ratingOf(ThematicReport r, String sub) {
        return r.subSectors().stream().filter(v -> v.name().equals(sub)).findFirst().orElseThrow().rating();
    }
}
