package com.boombapcompile.blckvox.config.reconcile;

import com.boombapcompile.blckvox.config.properties.OrchestrationProperties;
import com.boombapcompile.blckvox.config.properties.ReconciliationProperties;
import com.boombapcompile.blckvox.service.reconcile.TranscriptReconciler;
import com.boombapcompile.blckvox.service.reconcile.impl.ConfidenceReconciler;
import com.boombapcompile.blckvox.service.reconcile.impl.SimplePreferenceReconciler;
import com.boombapcompile.blckvox.service.reconcile.impl.WordOverlapReconciler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationConfigTest {

    private final ReconciliationConfig config = new ReconciliationConfig();

    @Test
    void simpleStrategyCreatesSimplePreferenceReconciler() {
        ReconciliationProperties props = new ReconciliationProperties(
                true, ReconciliationProperties.Strategy.SIMPLE, 0.6, 0.7);
        OrchestrationProperties orchProps = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200, 120);

        TranscriptReconciler reconciler = config.transcriptReconciler(props, orchProps);
        assertThat(reconciler).isInstanceOf(SimplePreferenceReconciler.class);
    }

    @Test
    void confidenceStrategyCreatesConfidenceReconciler() {
        ReconciliationProperties props = new ReconciliationProperties(
                true, ReconciliationProperties.Strategy.CONFIDENCE, 0.6, 0.7);
        OrchestrationProperties orchProps = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200, 120);

        TranscriptReconciler reconciler = config.transcriptReconciler(props, orchProps);
        assertThat(reconciler).isInstanceOf(ConfidenceReconciler.class);
    }

    @Test
    void overlapStrategyCreatesWordOverlapReconciler() {
        ReconciliationProperties props = new ReconciliationProperties(
                true, ReconciliationProperties.Strategy.OVERLAP, 0.6, 0.7);
        OrchestrationProperties orchProps = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200, 120);

        TranscriptReconciler reconciler = config.transcriptReconciler(props, orchProps);
        assertThat(reconciler).isInstanceOf(WordOverlapReconciler.class);
    }
}
