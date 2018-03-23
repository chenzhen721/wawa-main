package com.wawa.service.weixin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

import static com.wawa.common.util.MsgDigestUtil.MD5;

/**
 * @author: zhen.chen@2339.com
 * Date: 2015/6/26 18:05
 */
public class XingqiResponseHandler extends ResponseHandler {
    //需要参加校验的参数名有序列表
    private List<String> signParamList = new ArrayList<>();
    private List<String> resignParamList = new ArrayList<>();

    public XingqiResponseHandler(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
        signParamList.add("customerid");
        signParamList.add("sd51no");
        signParamList.add("sdcustomno");
        signParamList.add("mark");
        resignParamList.add("customerid");
        resignParamList.add("ordermoney");
        resignParamList.add("sd51no");
        resignParamList.add("state");
    }

    public boolean isSign() {
        StringBuffer sb = new StringBuffer();
        for (String k : signParamList) {
            String v = this.getParameter(k);
            sb.append(k + "=" + v + "&");
        }
        sb.append("key=" + this.getKey());

        //算出摘要
        String sign = MD5.digest2HEX(sb.toString()).toUpperCase();

        String vsign = this.getParameter("sign").toUpperCase();
        //需要做二次验证
        if (sign.equals(vsign)) {
            sb = new StringBuffer();
            sb.append("sign=" + sign + "&");
            for (String k : resignParamList) {
                String v = this.getParameter(k);
                sb.append(k + "=" + v + "&");
            }
            sb.append("key=" + this.getKey());
            String resign = MD5.digest2HEX(sb.toString()).toUpperCase();
            if (resign.equals(this.getParameter("resign").toUpperCase())) {
                return true;
            }
        }
        return false;
    }

}
