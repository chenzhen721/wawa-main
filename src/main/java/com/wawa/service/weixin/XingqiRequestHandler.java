package com.wawa.service.weixin;




import com.wawa.common.util.MsgDigestUtil;
import com.wawa.service.weixin.util.TenpayUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * 星启天专用
 * 预下单请求处理类(合作方下单处理类)
 */
public class XingqiRequestHandler extends RequestHandler {

    private List<String> parameterKey = new ArrayList<>();

    public XingqiRequestHandler(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
        //super.setGateUrl("http://x.maimaitao.cn/gateway/weixin/weixinpay.asp");
        super.setGateUrl("http://www.zhifuka.net/gateway/weixin/weixinpay.asp");
        parameterKey.add("customerid");
        parameterKey.add("sdcustomno");
        parameterKey.add("orderAmount");
        parameterKey.add("cardno");
        parameterKey.add("noticeurl");
        parameterKey.add("backurl");
    }

    public String getRequestURL() throws UnsupportedEncodingException {

        this.createSign();

        StringBuffer sb = new StringBuffer();
        String enc = TenpayUtil.getCharacterEncoding(this.request, this.response);
        Set es = this.getAllParameters().entrySet();
        Iterator it = es.iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            String k = (String) entry.getKey();
            String v = (String) entry.getValue();
            sb.append(k + "=" + URLEncoder.encode(v,"gb2312") + "&");
        }

        //去掉最后一个&
        String reqPars = sb.substring(0, sb.lastIndexOf("&"));
        return this.getGateUrl() + "?" + reqPars;

    }

    protected void createSign() {
        StringBuffer sb = new StringBuffer();

        for (String k : parameterKey) {
            String v = this.getParameter(k);
            sb.append(k + "=" + v + "&");
        }
        String key = sb.substring(0, sb.lastIndexOf("&"))+this.getKey();
        String enc = TenpayUtil.getCharacterEncoding(this.request, this.response);
        String sign = MsgDigestUtil.MD5.digest2HEX(key).toUpperCase();

        this.setParameter("sign", sign);

        //debug信息
        this.setDebugInfo(sb.toString() + " => sign:" + sign);
    }

}