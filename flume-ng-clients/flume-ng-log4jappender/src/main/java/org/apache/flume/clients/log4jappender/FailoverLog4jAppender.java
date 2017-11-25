/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.flume.clients.log4jappender;

import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.apache.flume.FlumeException;
import org.apache.flume.api.RpcClientConfigurationConstants;
import org.apache.flume.api.RpcClientFactory;
import org.apache.flume.api.RpcClientFactory.ClientType;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;

/**
 *
 * Appends Log4j Events to an external Flume client which is decribed by the
 * Log4j configuration file. The appender takes the following required
 * parameters:
 * <p>
 * <strong>Hosts</strong> : A space separated list of host:port of the first hop
 * at which Flume (through an AvroSource) is listening for events.
 * </p>
 * The appender also takes the following optional parameters:
 * <p>
 * <strong>MaxAttempts</strong> : A value representing the maximum attempt times
 * of flume agent to connect/request before the failover enabled client switchs 
 * to other ones.
 * </p>
 * A sample log4j properties file which appends to a source would look like:
 *
 * <pre>
 * <p>
 * log4j.appender.out2 = org.apache.flume.clients.log4jappender.FailoverLog4jAppender
 * log4j.appender.out2.Hosts = fooflumesource.com:25430 barflumesource.com:25430
 * log4j.appender.out2.MaxAttempts = 3
 * log4j.logger.org.apache.flume.clients.log4jappender = DEBUG,out2</p>
 * </pre>
 * <p>
 * <i>Note: Change the last line to the package of the class(es), that will do
 * the appending.For example if classes from the package com.bar.foo are
 * appending, the last line would be:</i>
 * </p>
 *
 * <pre>
 * <p>log4j.logger.com.bar.foo = DEBUG,out2</p>
 * </pre>
 *
 *
 */
public class FailoverLog4jAppender extends Log4jAppender {

    private String hosts;
    private String maxAttempts;
    private boolean configured = false;

    public void setHosts(String hostNames) {
        this.hosts = hostNames;
    }

    public void setMaxAttempts(String maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    @Override
    public synchronized void append(LoggingEvent event) {
        if (!configured) {
            String errorMsg = "Flume Log4jAppender not configured correctly! Cannot" +
                    " send events to Flume.";
            LogLog.error(errorMsg);
            if (getUnsafeMode()) {
                return;
            }
            throw new FlumeException(errorMsg);
        }
        super.append(event);
    }

    /**
     * Activate the options set using <tt>setHosts()</tt>
     *
     * @throws FlumeException if the LoadBalancingRpcClient cannot be instantiated.
     */
    @Override
    public void activateOptions() throws FlumeException {
        try {
            final Properties properties = getProperties(hosts, maxAttempts, getTimeout());
            rpcClient = RpcClientFactory.getInstance(properties);
            if (layout != null) {
                layout.activateOptions();
            }
            configured = true;
        } catch (Exception e) {
            String errormsg = "RPC client creation failed! " + e.getMessage();
            LogLog.error(errormsg);
            if (getUnsafeMode()) {
                return;
            }
            throw new FlumeException(e);
        }

    }

    private Properties getProperties(String hosts, String maxAttempts,
            long timeout) throws FlumeException {
        if (StringUtils.isEmpty(hosts)) {
            throw new FlumeException("hosts must not be null");
        }

        Properties props = new Properties();

        String[] hostsAndPorts = hosts.split("\\s+");
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < hostsAndPorts.length; i++) {
            String hostAndPort = hostsAndPorts[i];
            String name = "h" + i;
            props.setProperty(RpcClientConfigurationConstants.CONFIG_HOSTS_PREFIX + name,
                    hostAndPort);
            names.append(name).append(" ");
        }
        props.put(RpcClientConfigurationConstants.CONFIG_HOSTS, names.toString());
        props.put(RpcClientConfigurationConstants.CONFIG_CLIENT_TYPE,
                ClientType.DEFAULT_FAILOVER.toString());

        props.setProperty(RpcClientConfigurationConstants.CONFIG_MAX_ATTEMPTS, maxAttempts);

        props.setProperty(RpcClientConfigurationConstants.CONFIG_CONNECT_TIMEOUT, 
            String.valueOf(timeout));
        props.setProperty(RpcClientConfigurationConstants.CONFIG_REQUEST_TIMEOUT, 
            String.valueOf(timeout));

        return props;
    }

}
