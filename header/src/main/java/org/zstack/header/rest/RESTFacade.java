package org.zstack.header.rest;

import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.HttpClients;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.zstack.header.core.Completion;

import javax.net.ssl.SSLContext;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public interface RESTFacade {
    void asyncJsonPost(String url, Object body, Map<String, String> headers, AsyncRESTCallback callback, TimeUnit unit, long timeout);

    void asyncJsonPost(String url, Object body, AsyncRESTCallback callback, TimeUnit unit, long timeout);

    void asyncJsonPost(String url, String body, AsyncRESTCallback callback, TimeUnit unit, long timeout);

    void asyncJsonPost(String url, String body, Map<String, String> headers, AsyncRESTCallback callback, TimeUnit unit, long timeout);

    void asyncJsonPost(String url, Object body, Map<String, String> headers, AsyncRESTCallback callback);

    void asyncJsonPost(String url, Object body, AsyncRESTCallback callback);

    void asyncJsonPost(String url, String body, AsyncRESTCallback callback);

    /** P0 control-plane ping — uses dedicated isolated pool (R2). Drop-in replacement for asyncJsonPost on ping paths. */
    void asyncJsonPostForPing(String url, Object body, AsyncRESTCallback callback);

    /** P0 control-plane ping with explicit timeout — uses dedicated isolated pool (R2). */
    void asyncJsonPostForPing(String url, Object body, AsyncRESTCallback callback, TimeUnit unit, long timeout);

    void asyncJsonDelete(String url, String body, Map<String, String> headers, AsyncRESTCallback callback, TimeUnit unit, long timeout);
    void asyncJsonGet(String url, String body, Map<String, String> headers, AsyncRESTCallback callback, TimeUnit unit, long timeout);

    void asyncJson(final String url, final String body, Map<String, String> headers, HttpMethod method, final AsyncRESTCallback callback, final TimeUnit unit, final long timeout);

    <T> T syncJsonPost(String url, Object body, Class<T> returnClass);

    <T> T syncJsonPost(String url, Object body, Class<T> returnClass, TimeUnit unit, long timeout);

    <T> T syncJsonPost(String url, String body, Class<T> returnClass);

    <T> T syncJsonPost(String url, String body, Map<String, String> headers, Class<T> returnClass);

    <T> T syncJsonPost(String url, String body, Map<String, String> headers, Class<T> returnClass, TimeUnit unit, long timeout);

    /**
     * ZStack's agents only use sync/async post method
     * delete and get methods used for outsides plugins
     */
    <T> T syncJsonDelete(String url, String body, Map<String, String> headers, Class<T> returnClass);

    <T> T syncJsonDelete(String url, String body, Map<String, String> headers, Class<T> returnClass, TimeUnit unit, long timeout);

    <T> T syncJsonGet(String url, String body, Map<String, String> headers, Class<T> returnClass);

    <T> T syncJsonGet(String url, String body, Map<String, String> headers, Class<T> returnClass, TimeUnit unit, long timeout);

    <T> T syncJsonPut(String url, String body, Map<String, String> headers, Class<T> returnClass);

    <T> T syncJsonPut(String url, String body, Map<String, String> headers, Class<T> returnClass, TimeUnit unit, long timeout);

    <T> RestHttp<T> http(Class<T> returnClass);

    ResponseEntity<String> syncRawJson(String url, HttpEntity<String> req, HttpMethod method, TimeUnit unit, long timeout);

    HttpHeaders syncHead(String url);

    HttpEntity<String> httpServletRequestToHttpEntity(HttpServletRequest req);

    RestTemplate getRESTTemplate();

    void echo(String url, Completion callback);

    void echo(String url, Completion callback, long inverval, long timeout);

    Map<String, HttpCallStatistic> getStatistics();

    <T> void registerSyncHttpCallHandler(String path, Class<T> objectType, SyncHttpCallHandler<T> handler);

    String getBaseUrl();

    String getSendCommandUrl();

    String getCallbackUrl();

    String getHostName();

    int getPort();

    String makeUrl(String path);

    Runnable installBeforeAsyncJsonPostInterceptor(BeforeAsyncJsonPostInterceptor interceptor);

    static void setMessageConverter(List<HttpMessageConverter<?>> converters) {
        StringHttpMessageConverter stringHttpMessageConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        stringHttpMessageConverter.setWriteAcceptCharset(true);

        for (int i = 0; i < converters.size(); i++) {
            if (converters.get(i) instanceof StringHttpMessageConverter) {
                converters.remove(i);
                converters.add(i, stringHttpMessageConverter);
                break;
            }
        }
    }

    // timeout are in milliseconds
    static TimeoutRestTemplate createRestTemplate(int readTimeout, int connectTimeout) {
        return createRestTemplate(readTimeout, connectTimeout, 0, 0);
    }

    /**
     * Create a RestTemplate with explicit connection pool parameters.
     * Per resource-management rules (R1): every HTTP client MUST declare pool capacity explicitly.
     * When maxTotal/maxPerRoute are 0, Apache HttpClient defaults are used (backward compatible).
     */
    static TimeoutRestTemplate createRestTemplate(int readTimeout, int connectTimeout, int maxTotal, int maxPerRoute) {
        HttpComponentsClientHttpRequestFactory factory = new TimeoutHttpComponentsClientHttpRequestFactory();
        factory.setReadTimeout(readTimeout);
        factory.setConnectTimeout(connectTimeout);
        factory.setConnectionRequestTimeout(Math.min(connectTimeout * 2, 8000));

        SSLContext sslContext = DefaultSSLVerifier.getSSLContext(DefaultSSLVerifier.trustAllCerts);

        if (maxTotal > 0 && maxPerRoute > 0) {
            // R1: explicit pool with SSL baked into the CM's socket factory registry.
            // IMPORTANT: HttpClientBuilder silently ignores setSSLContext/setSSLHostnameVerifier
            // when setConnectionManager() is used — SSL must be registered into the CM instead.
            org.apache.http.conn.ssl.SSLConnectionSocketFactory sslSf = sslContext != null
                    ? new org.apache.http.conn.ssl.SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier())
                    : org.apache.http.conn.ssl.SSLConnectionSocketFactory.getSocketFactory();
            org.apache.http.config.Registry<org.apache.http.conn.socket.ConnectionSocketFactory> socketRegistry =
                    org.apache.http.config.RegistryBuilder.<org.apache.http.conn.socket.ConnectionSocketFactory>create()
                            .register("http", org.apache.http.conn.socket.PlainConnectionSocketFactory.getSocketFactory())
                            .register("https", sslSf)
                            .build();
            org.apache.http.impl.conn.PoolingHttpClientConnectionManager cm =
                    new org.apache.http.impl.conn.PoolingHttpClientConnectionManager(socketRegistry);
            cm.setMaxTotal(maxTotal);
            cm.setDefaultMaxPerRoute(maxPerRoute);
            factory.setHttpClient(HttpClients.custom().setConnectionManager(cm).build());
        } else if (sslContext != null) {
            // original behavior: only override HttpClient when SSL needed
            factory.setHttpClient(HttpClients.custom()
                    .setSSLHostnameVerifier(new NoopHostnameVerifier())
                    .setSSLContext(sslContext)
                    .build());
        }

        TimeoutRestTemplate template = new TimeoutRestTemplate(factory);
        setMessageConverter(template.getMessageConverters());

        return template;
    }
}
