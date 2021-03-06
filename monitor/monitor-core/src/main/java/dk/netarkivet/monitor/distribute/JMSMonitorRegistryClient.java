/*
 * #%L
 * Netarchivesuite - monitor
 * %%
 * Copyright (C) 2005 - 2018 The Royal Danish Library, 
 *             the National Library of France and the Austrian National Library.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package dk.netarkivet.monitor.distribute;

import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.netarkivet.common.CommonSettings;
import dk.netarkivet.common.distribute.JMSConnectionFactory;
import dk.netarkivet.common.distribute.monitorregistry.MonitorRegistryClient;
import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.exceptions.NetarkivetException;
import dk.netarkivet.common.utils.CleanupHook;
import dk.netarkivet.common.utils.CleanupIF;
import dk.netarkivet.common.utils.Settings;
import dk.netarkivet.monitor.MonitorSettings;
import dk.netarkivet.monitor.registry.distribute.RegisterHostMessage;

/**
 * The monitor registry client sends messages with JMS to register the host for JMX monitoring.
 */
public final class JMSMonitorRegistryClient implements MonitorRegistryClient, CleanupIF {
    /** The singleton instance of this class. */
    private static JMSMonitorRegistryClient instance;
    /** The logger for this class. */
    private static final Logger log = LoggerFactory.getLogger(JMSMonitorRegistryClient.class);
    /** The cleanup hook that will clean up this client on VM shutdown. */
    private CleanupHook hook;
    /** The timer that sends messages. */
    private Timer registryTimer;
    /**
     * One minute in milliseconds. Used for control of timer task that sends messages.
     */
    private static final long MINUTE_IN_MILLISECONDS = 60000L;
    /**
     * Zero milliseconds from now. Used for control of timer task that sends messages.
     */
    private static final long NOW = 0L;

    /**
     * Intialises the client.
     */
    private JMSMonitorRegistryClient() {
        hook = new CleanupHook(this);
        Runtime.getRuntime().addShutdownHook(hook);
    }

    /**
     * Get the registry client singleton.
     *
     * @return The registry client.
     */
    public static synchronized JMSMonitorRegistryClient getInstance() {
        if (instance == null) {
            instance = new JMSMonitorRegistryClient();
        }
        return instance;
    }

    /**
     * Register this host for monitoring. Once this method is called it will reregister for monitoring every minute, to
     * ensure the scheduling is done. If called again, it will restart the timer that registers the host.
     *
     * @param localHostName The name of the host.
     * @param jmxPort The port for JMX connections to the host.
     * @param rmiPort The port for RMI connections for JMX communication.
     * @throws ArgumentNotValid on null or empty localHostName, or negative port numbers.
     */
    public synchronized void register(final String localHostName, final int jmxPort, final int rmiPort) {
        ArgumentNotValid.checkNotNullOrEmpty(localHostName, "String localHostName");
        ArgumentNotValid.checkNotNegative(jmxPort, "int jmxPort");
        ArgumentNotValid.checkNotNegative(rmiPort, "int rmiPort");
        if (registryTimer != null) {
            log.info("Cancelling old registryTimer instance");
            registryTimer.cancel();
        }
        registryTimer = new Timer("Monitor-registry-client", true);
        TimerTask timerTask = new TimerTask() {
            /** The action to be performed by this timer task. */
            public void run() {
                log.trace("Registering this client for monitoring, using hostname '{}' and JMX/RMI ports {}/{}",
                        localHostName, jmxPort, rmiPort);
                JMSConnectionFactory.getInstance().send(new RegisterHostMessage(localHostName, jmxPort, rmiPort));
            }
        };

        long reregisterDelay = Settings.getLong(MonitorSettings.DEFAULT_REREGISTER_DELAY);
        try {
            reregisterDelay = Long.parseLong(Settings.get(CommonSettings.MONITOR_REGISTRY_CLIENT_REREGISTERDELAY));
        } catch (NumberFormatException e1) {
            log.warn("Couldn't parse setting {}. Only numbers are allowed. Using defaultvalue {}",
                    CommonSettings.MONITOR_REGISTRY_CLIENT_REREGISTERDELAY, MonitorSettings.DEFAULT_REREGISTER_DELAY);
        } catch (NetarkivetException e2) {
            log.warn("Couldn't find setting {}. Using defaultvalue {}",
                    CommonSettings.MONITOR_REGISTRY_CLIENT_REREGISTERDELAY, MonitorSettings.DEFAULT_REREGISTER_DELAY);
        }

        log.info("Registering this client for monitoring every {} minutes, using hostname '{}' and JMX/RMI ports {}/{}",
                reregisterDelay, localHostName, jmxPort, rmiPort);
        registryTimer.scheduleAtFixedRate(timerTask, NOW, reregisterDelay * MINUTE_IN_MILLISECONDS);
    }

    /**
     * Used to clean up a class from within a shutdown hook. Must not do any logging. Program defensively, please.
     */
    public synchronized void cleanup() {
        if (registryTimer != null) {
            registryTimer.cancel();
            registryTimer = null;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException e) {
            // Okay, it just means we are already shutting down.
        }
        hook = null;
    }

}
