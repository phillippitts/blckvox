package com.boombapcompile.blckvox.service.health;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

/**
 * JMX MBean that exposes application health.
 * Registered at {@code com.boombapcompile.blckvox:type=Health}.
 */
@Component
public class Health implements HealthMBean {

    private static final Logger LOG = LogManager.getLogger(Health.class);
    private static final String OBJECT_NAME = "com.boombapcompile.blckvox:type=Health";

    private final ApplicationHealthService healthService;
    private ObjectName objectName;

    public Health(ApplicationHealthService healthService) {
        this.healthService = healthService;
    }

    @PostConstruct
    void register() {
        try {
            objectName = new ObjectName(OBJECT_NAME);
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            server.registerMBean(this, objectName);
            LOG.info("Registered JMX MBean: {}", OBJECT_NAME);
        } catch (Exception ex) {
            LOG.warn("Failed to register JMX MBean {}: {}", OBJECT_NAME, ex.toString());
        }
    }

    @PreDestroy
    void deregister() {
        if (objectName == null) {
            return;
        }
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            if (server.isRegistered(objectName)) {
                server.unregisterMBean(objectName);
                LOG.info("Deregistered JMX MBean: {}", OBJECT_NAME);
            }
        } catch (Exception ex) {
            LOG.warn("Failed to deregister JMX MBean {}: {}", OBJECT_NAME, ex.toString());
        }
    }

    @Override
    public String getStatus() {
        return healthService.check().status().name();
    }

    @Override
    public String getDetails() {
        return healthService.check().details().toString();
    }

    @Override
    public long getLastCheckEpochMs() {
        return healthService.check().timestamp().toEpochMilli();
    }

    @Override
    public long getUptimeMs() {
        return ManagementFactory.getRuntimeMXBean().getUptime();
    }
}
