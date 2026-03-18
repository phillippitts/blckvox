package com.boombapcompile.blckvox.service.diagnostics;

import com.boombapcompile.blckvox.service.stt.SttEngine;
import com.boombapcompile.blckvox.service.stt.watchdog.SttEngineWatchdog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Logs startup timing and engine status when the application has fully started.
 */
@Component
public class StartupTimingLogger {

    private static final Logger LOG = LogManager.getLogger(StartupTimingLogger.class);

    private final List<SttEngine> engines;
    private final SttEngineWatchdog watchdog;

    @Autowired
    public StartupTimingLogger(List<SttEngine> engines,
                               @Autowired(required = false) SttEngineWatchdog watchdog) {
        this.engines = List.copyOf(engines);
        this.watchdog = watchdog;
    }

    @EventListener
    public void onApplicationStarted(ApplicationStartedEvent event) {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        StringBuilder sb = new StringBuilder();
        sb.append("Startup complete: JVM uptime=").append(uptimeMs).append("ms");
        sb.append(", engines=").append(engines.size());

        if (watchdog != null) {
            for (SttEngine engine : engines) {
                String name = engine.getEngineName();
                boolean enabled = watchdog.isEngineEnabled(name);
                sb.append(", ").append(name).append('=').append(enabled ? "enabled" : "disabled");
            }
        } else {
            sb.append(", watchdog=disabled");
        }

        LOG.info(sb.toString());
    }
}
