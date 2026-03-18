package com.boombapcompile.blckvox.service.stt.watchdog;

import com.boombapcompile.blckvox.config.properties.SttWatchdogProperties;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks per-engine confidence scores in a sliding window and detects degradation.
 *
 * <p>Thread-safe: all access to per-engine windows is synchronized on the window itself.
 */
public class ConfidenceMonitor {

    private final double blacklistThreshold;
    private final int windowSize;
    private final int minSamples;
    private final int graceTranscriptions;

    private final ConcurrentMap<String, Deque<Double>> windows = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> graceCounters = new ConcurrentHashMap<>();

    public ConfidenceMonitor(SttWatchdogProperties props) {
        Objects.requireNonNull(props, "props");
        this.blacklistThreshold = props.confidenceBlacklistThreshold();
        this.windowSize = props.confidenceWindowSize();
        this.minSamples = props.confidenceMinSamples();
        this.graceTranscriptions = props.confidenceGraceTranscriptions();
    }

    /** Registers an engine for confidence monitoring. */
    public void register(String engine) {
        windows.put(engine, new ArrayDeque<>());
        graceCounters.put(engine, new AtomicInteger(0));
    }

    /** Returns true if the given engine is tracked by this monitor. */
    public boolean isTracked(String engine) {
        return windows.containsKey(engine);
    }

    /**
     * Records a confidence score and evaluates the trend.
     *
     * @return a {@link Evaluation} indicating whether confidence is degraded, or null if
     *         not enough samples have been collected yet
     */
    public Evaluation record(String engine, double confidence) {
        Deque<Double> window = windows.get(engine);
        if (window == null) {
            return null;
        }

        AtomicInteger grace = graceCounters.get(engine);
        if (grace != null && grace.get() > 0) {
            grace.decrementAndGet();
            return null;
        }

        synchronized (window) {
            window.addLast(confidence);
            while (window.size() > windowSize) {
                window.removeFirst();
            }

            if (window.size() < minSamples) {
                return null;
            }

            double avg = window.stream().mapToDouble(Double::doubleValue).average().orElse(1.0);
            return new Evaluation(avg, avg < blacklistThreshold);
        }
    }

    /** Clears all confidence data for the given engine and activates grace period. */
    public void clearOnRecovery(String engine) {
        Deque<Double> window = windows.get(engine);
        if (window != null) {
            synchronized (window) {
                window.clear();
            }
        }
        AtomicInteger grace = graceCounters.get(engine);
        if (grace != null) {
            grace.set(graceTranscriptions);
        }
    }

    /** Returns the average confidence for the given engine, or 0.0 if no data. */
    public double averageConfidence(String engine) {
        Deque<Double> window = windows.get(engine);
        if (window == null || window.isEmpty()) {
            return 0.0;
        }
        synchronized (window) {
            return window.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
    }

    /** Returns the current window size for the given engine (visible for tests). */
    Deque<Double> getWindow(String engine) {
        return windows.get(engine);
    }

    /** Formatted average confidence for logging. */
    public String formattedSummary(String engine) {
        Deque<Double> window = windows.get(engine);
        if (window == null) {
            return "";
        }
        synchronized (window) {
            if (window.isEmpty()) {
                return "";
            }
            double avg = window.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            return String.format("(conf=%.3f/%d)", avg, window.size());
        }
    }

    /** Result of a confidence evaluation. */
    public record Evaluation(double average, boolean degraded) { }
}
