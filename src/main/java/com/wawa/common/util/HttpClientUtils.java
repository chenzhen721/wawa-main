package com.wawa.common.util;

import com.wawa.common.util.http.HttpEntityHandler;
import groovy.transform.CompileStatic;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DecompressingHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@CompileStatic
public abstract class HttpClientUtils {

    static final Logger log = LoggerFactory.getLogger(HttpClientUtils.class);

    public static final Charset UTF8 = Charset.forName("UTF-8");

    public static final Charset GB18030 =  Charset.forName("GB18030");

    static final int  TIME_OUT  = Integer.getInteger("http.timeout", 5000);

    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.4 (KHTML, like Gecko) Safari/537.4";

    public static HttpClient HTTP_CLIENT = bulidHttpClient();
    static HttpClient  bulidHttpClient(){
//        SchemeRegistry registry = new SchemeRegistry();
//        registry.register(new Scheme("http",  80, PlainSocketFactory.getSocketFactory()));
//        registry.register(new Scheme("https",  443, SSLSocketFactory.getSocketFactory()));
        PoolingClientConnectionManager cm = new PoolingClientConnectionManager();
        cm.setMaxTotal(800);
        cm.setDefaultMaxPerRoute(200);

        cm.setMaxPerRoute(new HttpRoute(new HttpHost("localhost")),500);
        cm.setMaxPerRoute(new HttpRoute(new HttpHost("127.0.0.1")),500);
        cm.setMaxPerRoute(new HttpRoute(new HttpHost("api.lezhuale.com")),500);
        cm.setMaxPerRoute(new HttpRoute(new HttpHost("user.lezhuale.com")),500);
        HttpParams defaultParams = new BasicHttpParams();

        defaultParams.setLongParameter(ClientPNames.CONN_MANAGER_TIMEOUT, TIME_OUT);
        defaultParams.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, TIME_OUT);//连接超时
        defaultParams.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, TIME_OUT);//读取超时

        defaultParams.setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.IGNORE_COOKIES);
        defaultParams.setParameter(CoreProtocolPNames.HTTP_CONTENT_CHARSET,UTF8.name());
        //defaultParams.setParameter(CoreProtocolPNames.PROTOCOL_VERSION,"HTTP/1.1");
        defaultParams.setParameter(CoreProtocolPNames.USER_AGENT,USER_AGENT);


//        CacheConfig cacheConfig = new CacheConfig();
//        cacheConfig.setMaxCacheEntries(5000);
//        cacheConfig.setMaxObjectSize(8192 * 4);

        HttpClient client = new DefaultHttpClient(cm,defaultParams);
        // 500 错误 重试一次 bw, also retry by seeds..
//        client = new AutoRetryHttpClient(client,new ServiceUnavailableRetryStrategy() {
//            @Override
//            public boolean retryRequest(HttpResponse response, int executionCount, HttpContext context) {
//                return executionCount <= 2 &&
//                        response.getStatusLine().getStatusCode() >= HttpStatus.SC_INTERNAL_SERVER_ERROR;
//            }
//            @Override
//            public long getRetryInterval() {
//                return 1500;
//            }
//        });

        client = new DecompressingHttpClient(client);

        return client;
        //return new CachingHttpClient(client, cacheConfig);
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
        return execute(get,HEADERS,forceCharset,httpEntityHandler);
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
        if(headers !=null &&  ! headers.isEmpty()){
            for (Map.Entry<String,String> kv : headers.entrySet()){
                request.addHeader(kv.getKey(),kv.getValue());
            }
        }
        long begin = System.currentTimeMillis();
        try{
            return client.execute(request,handler,null);
//            entity = response.getEntity();
//            int code = response.getStatusLine().getStatusCode();
//            if(code != HttpStatus.SC_OK){
//                throw new HttpStatusException(code,request.getURI().toString());
//            }
//
//
//            return callBack.handle(entity);
        }catch (ConnectTimeoutException e){
            log.error(" catch ConnectTimeoutException ,closeExpiredConnections &  closeIdleConnections for 30 s. ");
            client.getConnectionManager().closeExpiredConnections();
            client.getConnectionManager().closeIdleConnections(30, TimeUnit.SECONDS);
            throw  e;
        }finally {
            // netstat -n | awk '/^tcp/ {++S[$NF]} END {for(a in S) print a, S[a]}'
            // CLOSE_WAIT =  DefaultMaxPerRoute
            // HttpClient4使用 InputStream.close() 来确认连接关闭
            // CLOST_WAIT 僵死连接数 （占用一个路由的连接）
            //EntityUtils.consumeQuietly(entity);
            // 被动关闭连接 (目标服务器发生异常主动关闭了链接) 之后自己并没有释放连接，那就会造成CLOSE_WAIT的状态
            log.info(handler.getName() + "  {},cost {} ms",request.getURI(), System.currentTimeMillis() - begin);
        }
    }


    private static String execute(final HttpRequestBase request, Map<String,String> headers, final Charset forceCharset)throws IOException {
        return execute(request,headers,forceCharset, null);
    }

    private static String execute(final HttpRequestBase request, Map<String,String> headers, final Charset forceCharset, HttpEntityHandler<String> httpEntityHandler)throws IOException {
        return http(HTTP_CLIENT,request,headers,httpEntityHandler != null ? httpEntityHandler : new StringHttpEntityHandler(request.getMethod(), forceCharset));
    }


    private static Charset checkMetaCharset(String html, Charset use){
        String magic ="charset=";
        int index = html.indexOf(magic);
        if(index >0 && index < 1000){
            index+=magic.length();
            int end = html.indexOf('"',index);
            if(end > index){
                try{

                    String charSetString = html.substring(index,end).toLowerCase();

                    if(charSetString.length() > 10){
                        return null;
                    }
                    //GBK GB2312 --> GB18030
                    if(charSetString.startsWith("gb")){
                        return GB18030.equals(use) ? null : GB18030;
                    }
                    Charset curr = Charset.forName(charSetString);
                    if(!curr.equals(use)){
                        return curr;
                    }
                }catch (Exception e){
                    log.error("Get MetaCharset error",e);
                }
            }
        }

        return null;
    }

    public static class StringHttpEntityHandler extends HttpEntityHandler<String>{

        private String name;
        private Charset charset;

        public StringHttpEntityHandler (String name, Charset charset) {
            this.name = name;
            this.charset = charset;
        }

        @Override
        public String handle(HttpEntity entity) throws IOException {
            if (entity == null) {
                return null;
            }
            byte[] content = EntityUtils.toByteArray(entity);
            if(getCharset() != null){
                return new String(content,getCharset()) ;
            }
            String html;
            Charset charset =null;
            ContentType contentType = ContentType.get(entity);
            if(contentType !=null){
                charset = contentType.getCharset();
            }
            if(charset ==null){
                charset =GB18030;
            }
            html = new String(content,charset) ;
            charset = checkMetaCharset(html,charset);
            if(charset!=null){
                html = new String(content,charset);
            }
            return html;
        }
        public Charset getCharset() {
            return this.charset;
        }

        @Override
        public String getName() {
            return this.name;
        }

    }

    public static void main(String[] args) throws Exception {


        Map map = new HashMap();
        map.put("Cookie","BDUSS=JTbUhoeWhST3V5TTVoMXlvZXcyeUUwNHI1eS1Xc3BvNnFnU340MjhlMTE3TDVSQVFBQUFBJCQAAAAAAAAAAApBLRAPsZEvAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACAYIArMAAAALD2RHMAAAAA6p5DAAAAAAAxMC4zOC4yOHWe0VB1ntFQN3");

        System.out.println(HttpClientUtils.get("http://music.baidu.com/song/13859395/download", map));


    }


}
