package com.wawa.service.weixin.util;

import org.apache.commons.lang.StringUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;

import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;

public class WXUtil {

    public static String getNonceStr() {
        Random random = new Random();
        return MD5Util.MD5Encode(String.valueOf(random.nextInt(10000)), "GBK");
    }

    public static String getTimeStamp() {
        return String.valueOf(System.currentTimeMillis() / 1000);
    }

    public static String mapToXml(Map map) {
        StringBuffer sb = new StringBuffer();
        sb.append("<xml>\r\n");
        Set es = map.entrySet();
        Iterator it = es.iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            String k = (String) entry.getKey();
            String v = (String) entry.getValue();
            if (!"appkey".equals(k)) {
                sb.append("<" + k + ">" + v + "</" + k + ">" + "\r\n");
            }
        }
        sb.append("</xml>");
        return sb.toString();
    }


    public static Map parseXml(String content) throws Exception {
        if (StringUtils.isBlank(content)) {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        SAXBuilder builder = new SAXBuilder();
        Reader in = new StringReader(content);
        Document doc = builder.build(in);
        Element root = doc.getRootElement();
        List<Element> children = root.getChildren();
        for (Element child : children) {
            result.put(child.getName(), child.getValue());
        }
        return result;
    }

    /**
     * 创建签名
     * @param parameters
     * @param key
     * @param enc
     * @return
     */
    public static String createSign(SortedMap parameters, String key, String enc){
        StringBuffer sb = new StringBuffer();
        Set es = parameters.entrySet();
        Iterator it = es.iterator();
        while(it.hasNext()) {
            Map.Entry entry = (Map.Entry)it.next();
            String k = (String)entry.getKey();
            String v = (String)entry.getValue();
            if(null != v && !"".equals(v)
                    && !"sign".equals(k) && !"key".equals(k)) {
                sb.append(k + "=" + v + "&");
            }
        }
        sb.append("key=" + key);
        System.out.println("Weixin createSign : " + sb.toString());
        String sign = MD5Util.MD5Encode(sb.toString(), enc).toUpperCase();
        return sign;
    }
}
