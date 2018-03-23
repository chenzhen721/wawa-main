/**
 * Alipay.com Inc.
 * Copyright (c) 2005-2008 All Rights Reserved.
 */
package com.wawa.service.alipay.client.trade;

import com.wawa.common.doc.IMessageCode;
import com.wawa.common.util.MsgDigestUtil;
import com.wawa.service.alipay.AliMD5Sign;
import com.wawa.service.alipay.client.base.ParameterUtil;
import com.wawa.service.alipay.client.base.PartnerConfig;
import com.wawa.service.alipay.client.base.ResponseResult;
import groovy.util.XmlSlurper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/**
 * 
 * 调用支付宝的开放平台创建、支付交易步骤
 * 
 * 1.将业务参数：外部交易号、商品名称、商品总价、卖家帐户、卖家帐户、notify_url这些东西按照xml 的格式放入<req_data></req_data>中
 * 2.将通用参数也放入请求参数中 3.对以上的参数进行签名，签名结果也放入请求参数中
 * 4.请求支付宝开放平台的alipay.wap.trade.create.direct服务
 * 5.从开放平台返回的内容中取出request_token（对返回的内容要先用私钥解密，再用支付宝的公钥验签名）
 * 6.使用拿到的request_token组装alipay.wap.auth.authAndExecute服务的跳转url
 * 7.根据组装出来的url跳转到支付宝的开放平台页面，交易创建和支付在支付宝的页面上完成
 * 
 * 
 * @author 3y
 * @version $Id: Trade.java, v 0.1 2011-08-22 下午17:52:02 3y Exp $
 */
public class WapTrade {

	private static final long serialVersionUID = -3035307235076650766L;
	private static final String SEC_ID="MD5";
    public static final String WAP_REQ_URL = "https://wappaygw.alipay.com/service/rest.htm";
    static final Logger logger = LoggerFactory.getLogger(WapTrade.class);

    public static Object buildRequet(String subject, String total_fee, String outTradeNo)
            throws IOException, ParserConfigurationException, SAXException {
	  //得到应用服务器地址
		Map<String, String> reqParams = prepareTradeRequestParamsMap(subject, total_fee, outTradeNo);
		//签名类型
		String signAlgo = SEC_ID;
		String reqUrl = WAP_REQ_URL;
		
		//获取商户MD5 key
		String key = PartnerConfig.KEY;
		String sign = AliMD5Sign.buildMysign(reqParams, key);
        /*sign = MsgDigestUtil.MD5.digest2HEX(AliMD5Sign.createLinkString_noKey(reqParams));*/
		reqParams.put("sign", sign);
		
		ResponseResult resResult = new ResponseResult();
		String businessResult = "";
		try {
			resResult = send(reqParams,reqUrl,signAlgo);
		} catch (Exception e1) {
			e1.printStackTrace();
		}
        boolean bFlag = resResult.isSuccess() ;
		if (bFlag) {
			businessResult = resResult.getBusinessResult();
		} else {
			return IMessageCode.CODE0;
		}
        logger.info("bFlag---->:{}",bFlag);
        logger.info("businessResult---->:{}",businessResult);
		// 开放平台返回的内容中取出 request_token
		String requestToken = new XmlSlurper().parseText(businessResult).getProperty("request_token").toString();
		Map<String, String> authParams = prepareAuthParamsMap(requestToken);
		//对调用授权请求数据签名
		String authSign = AliMD5Sign.buildMysign(authParams, key);
		authParams.put("sign", authSign);

        authParams.put("action",reqUrl);
		Map result = new HashMap();
        result.put("code",1);
        result.put("data",authParams);
        return result;
	}
	
	
	
	/**
	 * 准备alipay.wap.trade.create.direct服务的参数
	 * 
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	private static Map<String, String> prepareTradeRequestParamsMap(String subject, String total_fee, String outTradeNo) {
		Map<String, String> requestParams = new HashMap<>();
		// 卖家帐号
		String sellerAccountName =PartnerConfig.SELLER;

		// req_data的内容
		String reqData = "<direct_trade_create_req>" + "<subject>" + subject
				+ "</subject><out_trade_no>" + outTradeNo
				+ "</out_trade_no><total_fee>" + total_fee
				+ "</total_fee><seller_account_name>" + sellerAccountName
				+ "</seller_account_name><notify_url>" + PartnerConfig.notifyUrl
				+ "</notify_url><call_back_url>" + PartnerConfig.merchantUrl+ "</call_back_url>";
		        reqData = reqData + "</direct_trade_create_req>";
		requestParams.put("req_data", reqData);
		requestParams.put("req_id", outTradeNo);
        fillCommonParams(requestParams);
		return requestParams;
	}

	/**
	 * 准备alipay.wap.auth.authAndExecute服务的参数
	 * 
	 * @return
	 */
	private static Map<String, String> prepareAuthParamsMap(String requestToken) {
		Map<String, String> requestParams = new HashMap<>();
		String reqData = "<auth_and_execute_req><request_token>" + requestToken
				+ "</request_token></auth_and_execute_req>";
		requestParams.put("req_data", reqData);
        fillCommonParams(requestParams);
//		requestParams.putAll(prepareCommonParams());
		//requestParams.put("call_back_url", PartnerConfig.call_back_url);
		requestParams.put("service", "alipay.wap.auth.authAndExecute");
		return requestParams;
	}



	/**
	 * 准备通用参数
	 * @return
	 */
	private  static void fillCommonParams(Map<String, String> commonParams) {
		commonParams.put("service", "alipay.wap.trade.create.direct");
		commonParams.put("sec_id", SEC_ID);
		commonParams.put("partner", PartnerConfig.PARTNER);
		//commonParams.put("call_back_url", PartnerConfig.call_back_url);
		commonParams.put("format", "xml");
		commonParams.put("v", "2.0");
	}

	/**
	 * 调用支付宝开放平台的服务
	 * 
	 * @param reqParams
	 *            请求参数
	 * @return
	 * @throws Exception
	 */
	private  static ResponseResult send(Map<String, String> reqParams, String reqUrl, String secId) throws Exception {
		String response = "";
		String invokeUrl = reqUrl  + "?";
		URL serverUrl = new URL(invokeUrl);
		HttpURLConnection conn = (HttpURLConnection) serverUrl.openConnection();

		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		conn.connect();
		String params = ParameterUtil.mapToUrl(reqParams);
		conn.getOutputStream().write(params.getBytes());

		InputStream is = conn.getInputStream();

		BufferedReader in = new BufferedReader(new InputStreamReader(is));
		StringBuffer buffer = new StringBuffer();
		String line = "";
		while ((line = in.readLine()) != null) {
			buffer.append(line);
		}
		response = URLDecoder.decode(buffer.toString(), "utf-8");
		conn.disconnect();
		return praseResult(response,secId);
	}

	/**
	 * 解析支付宝返回的结果
	 * 
	 * @param response
	 * @return
	 * @throws Exception
	 */
	private static ResponseResult praseResult(String response, String secId) throws Exception {
		// 调用成功
		HashMap<String, String> resMap = new HashMap<>();
		String v = ParameterUtil.getParameter(response, "v");
		String service = ParameterUtil.getParameter(response, "service");
		String partner = ParameterUtil.getParameter(response, "partner");
		String sign = ParameterUtil.getParameter(response, "sign");
		String reqId = ParameterUtil.getParameter(response, "req_id");
		resMap.put("v", v);
		resMap.put("service", service);
		resMap.put("partner", partner);
		resMap.put("sec_id", secId);
		resMap.put("req_id", reqId);
		String businessResult = "";
		ResponseResult result = new ResponseResult();
		if (response.contains("<err>")) {
			
			result.setSuccess(false);
//			businessResult = ParameterUtil.getParameter(response, "res_error");

//			// 转换错误信息
//			XMapUtil.register(ErrorCode.class);
//			ErrorCode errorCode = (ErrorCode) XMapUtil
//					.load(new ByteArrayInputStream(businessResult
//							.getBytes("UTF-8")));
//			result.setErrorMessage(errorCode);

			resMap.put("res_error", ParameterUtil.getParameter(response,
					"res_error"));
		} else {
		    businessResult = ParameterUtil.getParameter(response, "res_data");
            result.setSuccess(true);
            result.setBusinessResult(businessResult);
            resMap.put("res_data", businessResult);
		}
		//获取待签名数据
//		String verifyData = ParameterUtil.getSignData(resMap);
		//对待签名数据使用支付宝公钥验签名
		boolean verified = sign.equals(AliMD5Sign.buildMysign(resMap, PartnerConfig.KEY));
                //MD5Signature.verify(verifyData,sign,PartnerConfig.KEY);
		
		if (!verified) {
			throw new Exception("验证签名失败");
		}
		return result;
	}



    public static boolean verifyOrder(HttpServletRequest req, String notify_data){
        String sign = req.getParameter("sign");
        if( null == sign || notify_data == null){
            return false;
        }
        String service = req.getParameter("service");
        String v = req.getParameter("v");
        String sec_id = req.getParameter("sec_id");
        String verify_data = "service=" + service + "&v=" + v + "&sec_id=" + sec_id + "&notify_data="
                + notify_data;
        return sign.equals(MsgDigestUtil.MD5.digest2HEX(verify_data + PartnerConfig.KEY));
    }
}
