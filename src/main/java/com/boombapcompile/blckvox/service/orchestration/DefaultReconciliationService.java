package com.boombapcompile.blckvox.service.orchestration;

import com.boombapcompile.blckvox.config.properties.ReconciliationProperties;
import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.service.reconcile.TranscriptReconciler;
import com.boombapcompile.blckvox.service.stt.EngineResult;
import com.boombapcompile.blckvox.service.stt.parallel.ParallelSttService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

/**
 * Default implementation of {@link ReconciliationService} supporting dual-engine reconciliation.
 *
 * <p>This implementation coordinates parallel transcription execution with result reconciliation
 * using the configured strategy. All operations are thread-safe.
 *
 * <p><b>Reconciliation Process:</b>
 * <ol>
 *   <li>Run both engines in parallel via {@link ParallelSttService}</li>
 *   <li>Wait for both engines to complete</li>
 *   <li>Reconcile results using {@link TranscriptReconciler}</li>
 *   <li>Return final reconciled result</li>
 * </ol>
 *
 * @since 1.1
 */
public final class DefaultReconciliationService implements ReconciliationService {

    private static final Logger LOG = LogManager.getLogger(DefaultReconciliationService.class);

    // Timeout value indicating "use service default"
    private static final long USE_DEFAULT_TIMEOUT = 0L;

    private final ParallelSttService parallel;
    private final TranscriptReconciler reconciler;
    private final ReconciliationProperties props;

    /**
     * Constructs a DefaultReconciliationService.
     *
     * @param parallel service for running both engines in parallel
     * @param reconciler strategy for merging dual-engine results
     * @param props reconciliation configuration and enablement flag
     * @throws NullPointerException if any parameter is null
     */
    public DefaultReconciliationService(ParallelSttService parallel,
                                        TranscriptReconciler reconciler,
                                        ReconciliationProperties props) {
        this.parallel = Objects.requireNonNull(parallel, "parallel must not be null");
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler must not be null");
        this.props = Objects.requireNonNull(props, "props must not be null");
    }

    @Override
    public boolean isEnabled() {
        return props.isEnabled();
    }

    @Override
    public TranscriptionResult reconcile(byte[] pcm) {
        Objects.requireNonNull(pcm, "pcm must not be null");

        if (!isEnabled()) {
            throw new IllegalStateException("Reconciliation is not enabled");
        }

        double threshold = props.getConfidenceThreshold();

        // Smart upgrade: run Vosk first, only add Whisper if confidence is low
        try {
            EngineResult voskResult = parallel.transcribeVoskOnly(pcm, USE_DEFAULT_TIMEOUT);
            if (voskResult.confidence() >= threshold) {
                LOG.info("Vosk confidence {} >= threshold {}, using single-engine result",
                        String.format("%.3f", voskResult.confidence()),
                        String.format("%.3f", threshold));
                return TranscriptionResult.of(voskResult.text(), voskResult.confidence(),
                        voskResult.engineName());
            }

            LOG.info("Vosk confidence {} < threshold {}, upgrading to dual-engine reconciliation",
                    String.format("%.3f", voskResult.confidence()),
                    String.format("%.3f", threshold));
            var pair = parallel.transcribeWhisperOnly(pcm, USE_DEFAULT_TIMEOUT, voskResult);
            return reconciler.reconcile(pair.vosk(), pair.whisper());
        } catch (Exception e) {
            // Vosk pre-check failed — fall back to full dual-engine reconciliation
            LOG.warn("Vosk pre-check failed, falling back to full dual-engine: {}", e.getMessage());
            var pair = parallel.transcribeBoth(pcm, USE_DEFAULT_TIMEOUT);
            return reconciler.reconcile(pair.vosk(), pair.whisper());
        }
    }

    @Override
    public String getStrategy() {
        if (!isEnabled()) {
            throw new IllegalStateException("Reconciliation is not enabled");
        }
        return String.valueOf(props.getStrategy());
    }

    /**
     * Creates a no-op reconciliation service for when reconciliation is disabled.
     *
     * <p>This factory method is used when any of the reconciliation dependencies
     * are null (parallel, reconciler, or props). The returned service will always
     * report {@link #isEnabled()} as false and throw IllegalStateException if
     * reconciliation is attempted.
     *
     * @return no-op reconciliation service
     */
    public static ReconciliationService disabled() {
        return DisabledReconciliationService.INSTANCE;
    }

    /**
     * Singleton no-op implementation used when reconciliation is disabled.
     */
    private static final class DisabledReconciliationService implements ReconciliationService {
        private static final DisabledReconciliationService INSTANCE = new DisabledReconciliationService();

        private DisabledReconciliationService() {
            // Private constructor for singleton
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public TranscriptionResult reconcile(byte[] pcm) {
            throw new IllegalStateException("Reconciliation is not enabled");
        }

        @Override
        public String getStrategy() {
            throw new IllegalStateException("Reconciliation is not enabled");
        }
    }
}
