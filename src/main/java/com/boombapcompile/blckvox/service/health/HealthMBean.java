package com.boombapcompile.blckvox.service.health;

/**
 * JMX MBean interface exposing application health status.
 */
public interface HealthMBean {

    String getStatus();

    String getDetails();

    long getLastCheckEpochMs();

    long getUptimeMs();
}
