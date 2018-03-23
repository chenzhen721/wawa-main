package com.wawa.api;

import com.wawa.AppProperties;
import com.wawa.common.util.JSONUtil;
import com.wawa.common.util.http.HttpClientUtil;
import groovy.transform.CompileStatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

@CompileStatic
public abstract class WebApi {

    final static Logger logger = LoggerFactory.getLogger(WebApi.class);

    public static final String API_DOMAIN = AppProperties.get("api.domain", "http://api.lezhuale.com/");
    static final Charset UTF8 = Charset.forName("utf8");

    public static Object api(String url) throws IOException {
        Object obj = null;
        try {

            String sUrl = API_DOMAIN + url;
            String json = HttpClientUtil.get(sUrl, null, UTF8);
            obj = JSONUtil.jsonToMap(json).get("data");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return obj;
    }

    public static Object api(String url, Boolean bFlag) throws IOException {
        Map<String, Object> map = new HashMap<String, Object>();
        String json = HttpClientUtil.get(API_DOMAIN + url, null, UTF8);
        Map content = JSONUtil.jsonToMap(json);
        map.put("data", content.get("data"));
        if (bFlag) {
            map.put("count", content.get("count"));
        }
        return map;
    }
}
