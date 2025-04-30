package org.zstack.sdk;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by xing5 on 2016/12/9.
 */
public class ZSConfig {
    private ConcurrentHashMap<String, ZSEnvironment> environments = new ConcurrentHashMap<>();
    private String currentEnvironment;
    private long defaultPollingTimeout = TimeUnit.HOURS.toMillis(3);
    private long defaultPollingInterval = TimeUnit.SECONDS.toMillis(1);

    public long getDefaultPollingTimeout() {
        return defaultPollingTimeout;
    }

    public long getDefaultPollingInterval() {
        return defaultPollingInterval;
    }

    public void addEnvironment(ZSEnvironment env) {
        environments.put(env.getName(), env);
        if (currentEnvironment == null) {
            currentEnvironment = env.getName();
        }
    }

    public void setCurrentEnvironment(String name) {
        if (!environments.containsKey(name)) {
            throw new IllegalArgumentException("Environment '" + name + "' not found");
        }
        currentEnvironment = name;
    }

    public ZSEnvironment getCurrentEnvironment() {
        if (currentEnvironment == null) {
            throw new IllegalStateException("No environment configured");
        }
        return environments.get(currentEnvironment);
    }

    public ZSEnvironment getEnvironment(String name) {
        return environments.get(name);
    }

    public static class Builder {
        ZSConfig config = new ZSConfig();

        public Builder setDefaultPollingTimeout(long timeout) {
            config.defaultPollingTimeout = timeout;
            return this;
        }

        public Builder setDefaultPollingInterval(long interval) {
            config.defaultPollingInterval = interval;
            return this;
        }

        public Builder addEnvironment(ZSEnvironment env) {
            config.addEnvironment(env);
            return this;
        }

        public ZSConfig build() {
            return config;
        }
    }
}
