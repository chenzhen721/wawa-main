package com.wawa.service.weixin;


import com.wawa.service.weixin.util.WXUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Enumeration;
import java.util.Map;

/**
 * @author: zhen.chen@2339.com
 * Date: 2015/6/26 18:05
 */
public class WebResponseHandler extends ResponseHandler {

    public WebResponseHandler(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }


    public void initXmlParameters() throws Exception {
        Enumeration enumeration = this.getHttpServletRequest().getParameterNames();
        String content = "";
        if(enumeration != null){
            Object ele = enumeration.nextElement();
            if(ele != null){
                content = (String) ele;
            }
        }
        if (!content.startsWith("<xml>")) return;
        Map<String, String> map = WXUtil.parseXml(content);
        if (map == null) return;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            this.setParameter(entry.getKey(), entry.getValue());
        }
    }

    public void initParametersFromNotifyXml() throws Exception {
        /*String wxNotifyXml = "";
        wxNotifyXml = this.getHttpServletRequest().getReader().();
        Map<String, String> map = WXUtil.parseXml(wxNotifyXml);
        if (map == null) return;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            this.setParameter(entry.getKey(), entry.getValue());
        }
*/
    }

    public void initParametersFromMap(Map<String, String> map) throws Exception {
        if (map == null) return;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            this.setParameter(entry.getKey(), entry.getValue());
        }
    }
}
