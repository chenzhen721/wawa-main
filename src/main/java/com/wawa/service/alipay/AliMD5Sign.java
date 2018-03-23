package com.wawa.service.alipay;

import com.wawa.common.util.MsgDigestUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* *
 *类名：AlipayFunction
 *功能：支付宝接口公用函数类
 *详细：该类是请求、通知返回两个文件所调用的公用函数核心处理文件，不需要修改
 *版本：3.2
 *日期：2011-03-17
 *说明：
 *以下代码只是为了方便商户测试而提供的样例代码，商户可以根据自己网站的需要，按照技术文档编写,并非一定要使用该代码。
 *该代码仅供学习和研究支付宝接口使用，只是提供一个参考。
 */

public class AliMD5Sign {

    /**
     * 生成签名结果
     * @param sArray 要签名的数组
     * @return 签名结果字符串
     */
    public static String buildMysign(Map<String, String> sArray, String key) {//把拼接后的字符串再与安全校验码直接连接起来
        //String prestr =  //把数组所有元素，按照“参数=参数值”的模式用“&”字符拼接成字符串
//        String str = ;
//        System.out.println(
//                "GET Sign Query: "+str
//        );
        return MsgDigestUtil.MD5.digest2HEX(createLinkString(sArray, key));
    }

    /** 
     * 除去数组中的空值和签名参数
     * @param sArray 签名参数组
     * @return 去掉空值与签名参数后的新签名参数组
     */
    public static Map<String, String> paraFilter(Map<String, String> sArray) {

        Map<String, String> result = new HashMap<String, String>();

        if (sArray == null || sArray.size() <= 0) {
            return result;
        }

        for (String key : sArray.keySet()) {
            String value = sArray.get(key);
            if (value == null || value.equals("") || key.equalsIgnoreCase("sign")
                || key.equalsIgnoreCase("sign_type")) {
                continue;
            }
            result.put(key, value);
        }

        return result;
    }
   static final  Logger logger = LoggerFactory.getLogger(AliMD5Sign.class);
    /** 
     * 把数组所有元素排序，并按照“参数=参数值”的模式用“&”字符拼接成字符串
     * @param params 需要排序并参与字符拼接的参数组
     * @return 拼接后字符串
     */
    public static String createLinkString(Map<String, String> params, String ali_key) {
        List<String> keys = new ArrayList<String>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder(500);
        for (String key : keys ) {
            sb.append(key).append('=').append(params.get(key)).append('&');
        }
        sb.deleteCharAt(sb.length() - 1).append(ali_key);
         String sSb = sb.toString();

        return sSb;
    }

    public static String createLinkString_noKey(Map<String, String> params) {
        List<String> keys = new ArrayList<String>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder(500);
        for (String key : keys ) {
            sb.append(key).append('=').append(params.get(key)).append('&');
        }
        sb.deleteCharAt(sb.length() - 1);
        String sSb = sb.toString();
        logger.info("sSb--->:{}",sSb);

        return sSb;
    }

}
