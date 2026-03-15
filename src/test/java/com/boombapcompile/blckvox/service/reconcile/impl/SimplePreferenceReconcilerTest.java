package com.boombapcompile.blckvox.service.reconcile.impl;

import com.boombapcompile.blckvox.config.properties.OrchestrationProperties;
import com.boombapcompile.blckvox.service.stt.EngineResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimplePreferenceReconcilerTest {

    @Test
    void prefersVoskWhenPrimaryIsVoskAndBothNonEmpty() {
        var reconciler = new SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine.VOSK);
        var vosk = new EngineResult("vosk text", 0.9, List.of("vosk"), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 0.95, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("vosk text");
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(result.engineName()).isEqualTo("reconciled");
    }

    @Test
    void prefersWhisperWhenPrimaryIsWhisperAndBothNonEmpty() {
        var reconciler = new SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine.WHISPER);
        var vosk = new EngineResult("vosk text", 0.9, List.of("vosk"), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 0.95, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("whisper text");
        assertThat(result.confidence()).isEqualTo(0.95);
    }

    @Test
    void fallsBackToWhisperWhenVoskEmptyAndPrimaryIsVosk() {
        var reconciler = new SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine.VOSK);
        var vosk = new EngineResult("", 0.0, List.of(), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 0.95, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("whisper text");
        assertThat(result.confidence()).isEqualTo(0.95);
    }

    @Test
    void fallsBackToVoskWhenWhisperEmptyAndPrimaryIsWhisper() {
        var reconciler = new SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine.WHISPER);
        var vosk = new EngineResult("vosk text", 0.9, List.of("vosk"), 100L, "vosk", null);
        var whisper = new EngineResult("", 0.0, List.of(), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("vosk text");
        assertThat(result.confidence()).isEqualTo(0.9);
    }

    @Test
    void returnsEmptyWhenBothEmpty() {
        var reconciler = new SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine.VOSK);
        var vosk = new EngineResult("", 0.0, List.of(), 100L, "vosk", null);
        var whisper = new EngineResult("", 0.0, List.of(), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void handlesNullVosk() {
        var reconciler = new SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine.VOSK);
        var whisper = new EngineResult("whisper text", 0.95, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(null, whisper);

        assertThat(result.text()).isEqualTo("whisper text");
        assertThat(result.confidence()).isEqualTo(0.95);
    }

    @Test
    void handlesNullWhisper() {
        var reconciler = new SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine.VOSK);
        var vosk = new EngineResult("vosk text", 0.9, List.of("vosk"), 100L, "vosk", null);

        var result = reconciler.reconcile(vosk, null);

        assertThat(result.text()).isEqualTo("vosk text");
        assertThat(result.confidence()).isEqualTo(0.9);
    }

    @Test
    void handlesBothNull() {
        var reconciler = new SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine.VOSK);

        var result = reconciler.reconcile(null, null);

        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void handlesBlankText() {
        var reconciler = new SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine.VOSK);
        var vosk = new EngineResult("   ", 0.0, List.of(), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 0.95, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("whisper text");
    }

    @Test
    void blankSecondaryWithBlankPrimaryDefaultsToPrimaryWhisper() {
        var reconciler = new SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine.WHISPER);
        var vosk = new EngineResult("  ", 0.0, List.of(), 100L, "vosk", null);
        var whisper = new EngineResult("  ", 0.0, List.of(), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        // Both blank → falls through pickNonEmpty → returns first (whisper for WHISPER primary)
        assertThat(result).isNotNull();
        assertThat(result.confidence()).isEqualTo(0.0);
        assertThat(result.engineName()).isEqualTo("reconciled");
    }

    @Test
    void fallsBackToVoskWhenBothBlankAndPrimaryIsVosk() {
        var reconciler = new SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine.VOSK);
        var vosk = new EngineResult("  ", 0.5, List.of(), 100L, "vosk", null);
        var whisper = new EngineResult("  ", 0.3, List.of(), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        // Both blank → pickNonEmpty returns first (vosk, since primary=VOSK) → confidence from vosk
        assertThat(result.confidence()).isEqualTo(0.5);
    }

    @Test
    void blankPrimaryFallsBackToNonBlankSecondary() {
        var reconciler = new SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine.WHISPER);
        var vosk = new EngineResult("vosk result", 0.9, List.of("vosk"), 100L, "vosk", null);
        var whisper = new EngineResult("   ", 0.0, List.of(), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        // Primary (whisper) is blank → secondary (vosk) used
        assertThat(result.text()).isEqualTo("vosk result");
    }
}
