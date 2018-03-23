package com.wawa.common.util;

import com.wawa.common.util.http.HttpEntityHandler;
import groovy.transform.CompileStatic;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DecompressingHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CompileStatic
public abstract class HttpsClientUtils {

    static final Logger log = LoggerFactory.getLogger(HttpsClientUtils.class);

    public static final Charset UTF8 = Charset.forName("UTF-8");

    public static final Charset GB18030 =  Charset.forName("GB18030");

    static final int  TIME_OUT  = Integer.getInteger("http.timeout", 5000);

    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.4 (KHTML, like Gecko) Safari/537.4";

    public static HttpClient HTTP_CLIENT = bulidHttpClient();
    static HttpClient  bulidHttpClient(){
        PoolingClientConnectionManager cm = new PoolingClientConnectionManager(createSchemeRegistry());
        cm.setMaxTotal(800);
        cm.setDefaultMaxPerRoute(200);

        /*cm.setMaxPerRoute(new HttpRoute(new HttpHost("localhost")),500)
        cm.setMaxPerRoute(new HttpRoute(new HttpHost("127.0.0.1")),500)
        cm.setMaxPerRoute(new HttpRoute(new HttpHost("aiapi.memeyule.com")),500)
        cm.setMaxPerRoute(new HttpRoute(new HttpHost("aiuser.memeyule.com")),500)*/
        HttpParams defaultParams = new BasicHttpParams();

        defaultParams.setLongParameter(ClientPNames.CONN_MANAGER_TIMEOUT, TIME_OUT);
        defaultParams.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, TIME_OUT);//连接超时
        defaultParams.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, TIME_OUT);//读取超时

        defaultParams.setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.IGNORE_COOKIES);
        defaultParams.setParameter(CoreProtocolPNames.HTTP_CONTENT_CHARSET, UTF8.name());
        defaultParams.setParameter(CoreProtocolPNames.USER_AGENT, USER_AGENT);

        HttpClient client = new DefaultHttpClient(cm, defaultParams);
        return new DecompressingHttpClient(client);
    }

    private static SchemeRegistry createSchemeRegistry() {
        SchemeRegistry sr = new SchemeRegistry();
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            X509TrustManager tm = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }
                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            };

            ctx.init(null, new TrustManager[]{tm}, null);
            SSLSocketFactory ssf = new SSLSocketFactory(ctx, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            sr.register(new Scheme("https", 443, ssf));
        } catch (Exception ex) {

        }
        return sr;
    }

    public static String get(String url, Map<String,String> HEADERS)throws IOException {
        HttpGet get = new HttpGet(url);
        return execute(get,HEADERS,null);
    }


    public static String get(String url, Map<String,String> HEADERS, Charset forceCharset)throws IOException {
        HttpGet get = new HttpGet(url);
        return execute(get,HEADERS,forceCharset);
    }

    public static String get(String url, Map<String,String> HEADERS, Charset forceCharset, HttpEntityHandler<String> httpEntityHandler)throws IOException {
        HttpGet get = new HttpGet(url);
        return execute(get,HEADERS, forceCharset, httpEntityHandler);
    }

    public static String post(String url, Map<String,String> params, Map<String,String> headers) throws IOException {
        HttpPost post = new HttpPost(url);
        post.setEntity(new UrlEncodedFormEntity(buildParams(params), UTF8.name()));
        return execute(post,headers,null);
    }

    public static String postJson(String url, String body) throws IOException {
        Map<String, String> headers = new HashMap<>();
        return postJson(url, body, headers);
    }

    public static String postJson(String url, String body, Map<String, String> headers) throws IOException {
        return postJson(url, body, headers, null);
    }

    public static String post(String url, Map<String,String> params, Map<String,String> headers, HttpEntityHandler<String> httpEntityHandler) throws IOException {
        HttpPost post = new HttpPost(url);
        post.setEntity(new UrlEncodedFormEntity(buildParams(params), UTF8.name()));
        return execute(post,headers,null, httpEntityHandler);
    }

    public static String postJson(String url, String body, Map<String, String> headers, HttpEntityHandler<String> httpEntityHandler) throws IOException {
        HttpPost post = new HttpPost(url);
        post.setEntity(new StringEntity(body, UTF8.name()));
        headers.put("Content-Type", "application/json");
        return execute(post, headers, null, httpEntityHandler);
    }

    public static String put(String url, Map<String,String> params, Map<String,String> headers) throws IOException {
        return put(url, params, headers, null);
    }

    public static String put(String url, Map<String,String> params, Map<String,String> headers, HttpEntityHandler<String> httpEntityHandler) throws IOException {
        HttpPut put = new HttpPut(url);
        put.setEntity(new UrlEncodedFormEntity(buildParams(params), UTF8.name()));
        return execute(put,headers,null, httpEntityHandler);
    }

    private static List<NameValuePair> buildParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return new ArrayList<>();
        }
        List<NameValuePair> ps = new ArrayList<NameValuePair>(params.size());
        for (Map.Entry<String,String> kv : params.entrySet()){
            ps.add(new BasicNameValuePair(kv.getKey(), kv.getValue()));
        }
        return ps;
    }

    public static <T> T  http(HttpClient  client, HttpRequestBase request, Map<String,String> headers, HttpEntityHandler<T> handler)
            throws IOException {
        return HttpClientUtils.http(client, request, headers, handler);
    }


    private static String execute(final HttpRequestBase request, Map<String,String> headers, final Charset forceCharset)throws IOException {
        return execute(request,headers,forceCharset, null);
    }

    private static String execute(final HttpRequestBase request, Map<String,String> headers, final Charset forceCharset, HttpEntityHandler<String> httpEntityHandler)throws IOException {
        return http(HTTP_CLIENT,request,headers,httpEntityHandler != null ? httpEntityHandler : new HttpClientUtils.StringHttpEntityHandler(request.getMethod(), forceCharset));
    }

    public static void main(String[] args) throws Exception {


        Map map = new HashMap();
        map.put("Cookie","BDUSS=JTbUhoeWhST3V5TTVoMXlvZXcyeUUwNHI1eS1Xc3BvNnFnU340MjhlMTE3TDVSQVFBQUFBJCQAAAAAAAAAAApBLRAPsZEvAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACAYIArMAAAALD2RHMAAAAA6p5DAAAAAAAxMC4zOC4yOHWe0VB1ntFQN3");

        System.out.println(HttpsClientUtils.get("http://music.baidu.com/song/13859395/download", map));


    }


}
