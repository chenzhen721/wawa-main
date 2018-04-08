package com.wawa.web.mobile

import com.wawa.api.UserWebApi
import com.wawa.base.BaseController
import com.wawa.base.anno.Rest
import com.wawa.common.util.JSONUtil
import com.wawa.common.util.http.HttpClientUtil
import com.wawa.common.doc.Result
import com.wawa.common.util.HttpClientUtils
import groovy.json.JsonSlurper
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.concurrent.TimeUnit

/**
 *手机客户端模板接口
 */
@Rest
class WeixinJSController extends BaseController{

    static final  Logger logger = LoggerFactory.getLogger(WeixinJSController.class)

    private static final String APPID = isTest ? "wx87f81569b7e4b5f6" :"wxf64f0972d4922815"
    private static final String SECRET = isTest ? "8421fd4781b1c29077c2e82e71ce3d2a" : "fbf4fd32c00a82d5cbe5161c5e699a0e" //公众号秘钥

    private static final String WEIXIN_URL = "https://api.weixin.qq.com/cgi-bin/";
    private static final String jsapi_ticket_key = "weixin:js:ticket";


    static String getAccessRedisKey(String appId){
        return  "weixin:${appId}:token".toString()
    }

    /**
     * 获取token 和 js ticket
     * @param req
     * @return
     */
    def auth(HttpServletRequest req){
        def url = req.getParameter('url') as String
        if(StringUtils.isEmpty(url)){
            return Result.丢失必需参数;
        }
        String jsapi_ticket = ticket()
        if (StringUtils.isBlank(jsapi_ticket)) {
            return Result.error
        }
        return  [code : 1, data:sign(jsapi_ticket, url)]
    }

    /**
     * 是否提示关注公众号
     * @param req
     */
    def openid(Integer userId) {
        //获取对应公众号的openid，查询是否有关注记录
        return UserWebApi.getOpenidForWeixin(userId, APPID)
    }

    def weixin_user_info(String openId) {
        String access_token= access_token()
        if (StringUtils.isBlank(access_token)) {
            return null
        }
        def info_url = WEIXIN_URL + "user/info?access_token=${access_token}&openid=${openId}&lang=zh_CN"
        try {
            String info = HttpClientUtils.get(info_url, null, HttpClientUtils.UTF8)
            if (StringUtils.isNotBlank(info)) {
                def infoMap = new JsonSlurper().parseText(info) as Map
                if (infoMap != null) {
                    if (infoMap.containsKey('errcode')) {
                        logger.info('Get ' + info_url + 'error:' + info)
                        return null
                    }
                    return infoMap
                }
            }
        } catch (Exception e) {
            logger.error('Get ' + info_url + ' error.' + e)
        }
        return null
    }

    def weixin_set_remark(String openid, String remark) {
        String access_token= access_token()
        if (StringUtils.isBlank(access_token)) {
            return Result.error
        }
        def remark_url = WEIXIN_URL + "user/info/updateremark?access_token=${access_token}"
        def map = [openid: openid, remark: remark]
        String remark_resp = HttpClientUtils.postJson(remark_url, JSONUtil.beanToJson(map))
        if (StringUtils.isBlank(remark_resp)) {
            return Result.error
        }
        def result = new JsonSlurper().parseText(remark_resp)
        if (result == null || 0 != result['errcode']) {
            logger.info('Post ' + remark_url + 'error.' + remark_resp)
            return Result.error
        }
        return Result.success
    }

    private String access_token() {
        String access_token= mainRedis.opsForValue().get(getAccessRedisKey(APPID))
        if(StringUtils.isEmpty(access_token)){
            def token_url = WEIXIN_URL + "token?grant_type=client_credential&appid=${APPID}&secret=${SECRET}".toString()
            try {
                String token_resp = HttpClientUtil.get(token_url, null, HttpClientUtil.UTF8)
                if (StringUtils.isBlank(token_resp)) {
                    logger.error('Get ' + token_url + ' error.' + token_resp)
                    return null
                }
                Map respMap = JSONUtil.jsonToMap(token_resp)
                access_token = respMap['access_token']
                if (StringUtils.isBlank(access_token)) {
                    logger.error('Get ' + token_url + ' error.' + token_resp)
                    return null
                }
                Integer expires = respMap['expires_in'] as Integer
                mainRedis.opsForValue().set(getAccessRedisKey(APPID), access_token, expires, TimeUnit.SECONDS)
                return access_token
            } catch (Exception e) {
                logger.error('Get ' + token_url + ' error.' + e)
            }
        }
        return access_token
    }

    private String ticket() {
        String jsapi_ticket = mainRedis.opsForValue().get(jsapi_ticket_key);
        if(StringUtils.isEmpty(jsapi_ticket)){
            String access_token= access_token()
            if (StringUtils.isBlank(access_token)) {
                return null
            }
            def jsapi_ticket_url = WEIXIN_URL + "ticket/getticket?access_token=${access_token}&type=jsapi".toString()
            try {
                String jsapi_ticket_resp = HttpClientUtil.get(jsapi_ticket_url, null, HttpClientUtil.UTF8)
                if (StringUtils.isBlank(jsapi_ticket_resp)) {
                    logger.error('Get ' + jsapi_ticket_url + ' error.' + jsapi_ticket_resp)
                    return null
                }
                Map respMap = JSONUtil.jsonToMap(jsapi_ticket_resp)
                jsapi_ticket = respMap['ticket']
                if (StringUtils.isBlank(jsapi_ticket)) {
                    logger.error('Get ' + jsapi_ticket_url + ' error.' + jsapi_ticket_resp)
                    return null
                }
                Integer expires = respMap['expires_in'] as Integer
                mainRedis.opsForValue().set(jsapi_ticket_key, jsapi_ticket, expires, TimeUnit.SECONDS)
            } catch (Exception e) {
                logger.error('Get ' + jsapi_ticket_url + ' error.' + e)
            }
        }
        return jsapi_ticket
    }

    private String ticket_with_no_cache() {
        String access_token= access_token()
        if (StringUtils.isBlank(access_token)) {
            return null
        }
        def jsapi_ticket_url = WEIXIN_URL + "ticket/getticket?access_token=${access_token}&type=jsapi".toString()
        try {
            String jsapi_ticket_resp = HttpClientUtil.get(jsapi_ticket_url, null, HttpClientUtil.UTF8)
            if (StringUtils.isBlank(jsapi_ticket_resp)) {
                logger.error('Get ' + jsapi_ticket_url + ' error.' + jsapi_ticket_resp)
                return null
            }
            Map respMap = JSONUtil.jsonToMap(jsapi_ticket_resp)
            String jsapi_ticket = respMap['ticket']
            if (StringUtils.isBlank(jsapi_ticket)) {
                logger.error('Get ' + jsapi_ticket_url + ' error.' + jsapi_ticket_resp)
                return null
            }
            return jsapi_ticket
        } catch (Exception e) {
            logger.error('Get ' + jsapi_ticket_url + ' error.' + e)
        }
        return null
    }

    private static Map<String, String> sign(String jsapi_ticket, String url) {
        Map<String, String> ret = new HashMap<String, String>();
        String nonce_str = create_nonce_str();
        String timestamp = create_timestamp();
        String url_str;
        String signature = "";
        //注意这里参数名必须全部小写，且必须有序
        url_str = "jsapi_ticket=" + jsapi_ticket +"&noncestr=" + nonce_str +"&timestamp=" + timestamp +"&url=" + url;

        logger.debug("url_str : {}",url_str)
        try{
            MessageDigest crypt = MessageDigest.getInstance("SHA-1");
            crypt.reset();
            crypt.update(url_str.getBytes("UTF-8"));
            signature = byteToHex(crypt.digest());
        }
        catch (NoSuchAlgorithmException e){
            e.printStackTrace();
        }
        catch (UnsupportedEncodingException e){
            e.printStackTrace();
        }

        ret.put("url", url);
        ret.put("jsapi_ticket", jsapi_ticket);
        ret.put("nonceStr", nonce_str);
        ret.put("timestamp", timestamp);
        ret.put("signature", signature);
        return ret;
    }

    private static String byteToHex(final byte[] hash) {
        Formatter formatter = new Formatter();
        for (byte b : hash)
        {
            formatter.format("%02x", b);
        }
        String result = formatter.toString();
        formatter.close();
        return result;
    }

    private static String create_nonce_str() {
        return UUID.randomUUID().toString();
    }

    private static String create_timestamp() {
        return '' + (System.currentTimeMillis() / 1000).longValue()
    }
}
