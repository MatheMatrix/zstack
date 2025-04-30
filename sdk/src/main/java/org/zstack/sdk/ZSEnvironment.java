package org.zstack.sdk;

/**
 * Represents a single ZStack environment configuration
 */
public class ZSEnvironment {
    private String name;
    private String hostname = "localhost";
    private int port = 8080;
    private String webHook;
    private Long readTimeout;
    private Long writeTimeout;
    private String contextPath;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getWebHook() {
        return webHook;
    }

    public void setWebHook(String webHook) {
        this.webHook = webHook;
    }

    public Long getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Long readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Long getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(Long writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public static class Builder {
        private ZSEnvironment env = new ZSEnvironment();

        public Builder setName(String name) {
            env.name = name;
            return this;
        }

        public Builder setHostname(String hostname) {
            env.hostname = hostname;
            return this;
        }

        public Builder setWebHook(String webHook) {
            env.webHook = webHook;
            return this;
        }

        public Builder setPort(int port) {
            env.port = port;
            return this;
        }

        public Builder setReadTimeout(long timeout) {
            env.readTimeout = timeout;
            return this;
        }

        public Builder setWriteTimeout(long timeout) {
            env.writeTimeout = timeout;
            return this;
        }

        public Builder setContextPath(String path) {
            env.contextPath = path;
            return this;
        }

        public ZSEnvironment build() {
            return env;
        }
    }
}
