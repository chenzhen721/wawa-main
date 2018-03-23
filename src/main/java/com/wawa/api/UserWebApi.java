package com.wawa.api;

import com.wawa.AppProperties;
import com.wawa.common.util.JSONUtil;
import com.wawa.common.util.http.HttpClientUtil;
import groovy.transform.CompileStatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.wawa.common.util.MsgDigestUtil.MD5;


@CompileStatic
public abstract class UserWebApi {

    final static Logger logger = LoggerFactory.getLogger(Web.class);

    public static final String USER_DOMAIN = AppProperties.get("user.domain");
    //private static final String USER_DOMAIN = "http://localhost:8082";

    private static final String USER_INFO_URL = USER_DOMAIN + "show";

    private static final String GET_TOKEN_URL = USER_DOMAIN + "pwd/token_by_id";

    private static final String CHANGE_PWD_URL = USER_DOMAIN + "pwd/change";

    private static final String SYNNO_URL = USER_DOMAIN + "info/synNo";

    private static final String BIND_MOBILE_URL = USER_DOMAIN + "info/bindMobile";

    private static final String UNBIND_MOBILE_URL = USER_DOMAIN + "info/unbindMobile";

    private static final String BIND_USERNAME_URL = USER_DOMAIN + "info/bindUserName";

    private static final String BIND_EMAIL_URL = USER_DOMAIN + "info/bindUserEmail";

    private static final String BIND_UNION_USERNAME_URL = USER_DOMAIN + "register/union_user";

    private static final String FIND_PWD_URL = USER_DOMAIN + "pwd/find";

    private static final String VERIFY_CODE_URL = USER_DOMAIN + "info/verify_code";

    private static final String WEIXIN_CODE_URL = USER_DOMAIN + "thirdlogin/weixin_gz";

    private static final String WEIXIN_OPENID_URL = USER_DOMAIN + "thirdlogin/get_weixin_id";

    private final static String PRIV_KEY = "meme#*&07071zhibo";

    public static Map fetchUser(String access_token) {
        Map _jsonMap = null;
        try {
            String json = HttpClientUtil.get(USER_INFO_URL + "?access_token=" + access_token, null, HttpClientUtil.UTF8);
            _jsonMap = JSONUtil.jsonToMap(json);
        } catch (Exception e) {
            logger.error("TUID Error..fetchUser...", e);
        }
        return _jsonMap;
    }

    /**
     * 同步么么号
     *
     * @param tuid
     * @param mm_no
     * @return
     */
    public static Boolean synNo(Object tuid, Object mm_no) {
        try {
            String sign = MD5.digest2HEX(PRIV_KEY + tuid + mm_no);
            String json = HttpClientUtil.get(SYNNO_URL + "?tuid=" + tuid + "&mm_no=" + mm_no + "&sign=" + sign, null, HttpClientUtil.UTF8);
            Map _jsonMap = JSONUtil.jsonToMap(json);
            if (((Number) _jsonMap.get("code")).intValue() != 1) {
                return false;//同步失败
            }
        } catch (Exception e) {
            logger.error("Error..synMemeNo...", e);
        }
        return true;
    }

    public static Map bindMobile(String tuid, String access_token, String mobile, String sms_code, String pwd) {
        Map _jsonMap = null;
        try {
            Map<String, String> form = new HashMap<>();
            form.put("access_token", access_token);
            form.put("mobile", mobile);
            form.put("tuid", tuid);
            form.put("sms_code", sms_code);
            form.put("pwd", pwd);
            String json = HttpClientUtil.post(BIND_MOBILE_URL, form, null);
            _jsonMap = JSONUtil.jsonToMap(json);
        } catch (Exception e) {
            logger.error("Error..bindMobile...", e);
        }
        return _jsonMap;
    }


    public static Map unbindMobile(String tuid, String access_token, String sms_code) {
        Map _jsonMap = null;
        try {
            Map<String, String> form = new HashMap<>();
            form.put("tuid", tuid);
            form.put("access_token", access_token);
            form.put("sms_code", sms_code);
            String json = HttpClientUtil.post(UNBIND_MOBILE_URL, form, null);
            _jsonMap = JSONUtil.jsonToMap(json);
        } catch (Exception e) {
            logger.error("Error..bindMobile...", e);
        }
        return _jsonMap;
    }

    public static Map bindUserName(String access_token, String username, String pwd) {
        Map _jsonMap = null;
        try {
            Map<String, String> form = new HashMap<>();
            form.put("access_token", access_token);
            form.put("username", username);
            form.put("pwd", pwd);
            String json = HttpClientUtil.post(BIND_USERNAME_URL, form, null);
            _jsonMap = JSONUtil.jsonToMap(json);
        } catch (Exception e) {
            logger.error("Error..bindUserName...", e);
        }
        return _jsonMap;
    }

    public static Map bindEmail(String access_token, String email, String pwd) {
        Map _jsonMap = null;
        try {
            Map<String, String> form = new HashMap<>();
            form.put("access_token", access_token);
            form.put("email", email);
            form.put("pwd", pwd);
            String json = HttpClientUtil.post(BIND_EMAIL_URL, form, null);
            _jsonMap = JSONUtil.jsonToMap(json);
        } catch (Exception e) {
            logger.error("Error..bindUserName...", e);
        }
        return _jsonMap;
    }

    public static Map bindUnionUserName(String u_id, String via, String username, String pwd, String nickname, String pic, String mm_no) {
        Map _jsonMap = null;
        try {
            Map<String, String> form = new HashMap<>();
            form.put("u_id", u_id);
            form.put("username", username);
            form.put("pwd", pwd);
            form.put("nickname", nickname);
            form.put("pic", pic);
            form.put("mm_no", mm_no);
            form.put("via", via);
            String json = HttpClientUtil.post(BIND_UNION_USERNAME_URL, form, null);
            _jsonMap = JSONUtil.jsonToMap(json);
        } catch (Exception e) {
            logger.error("Error..bindUnionUserName...", e);
        }
        return _jsonMap;
    }

    public static Map changePwd(String access_token, String old_pwd, String new_pwd) {
        Map _jsonMap = null;
        try {
            Map<String, String> form = new HashMap<>();
            form.put("access_token", access_token);
            form.put("oldpwd", old_pwd);
            form.put("newpwd", new_pwd);
            String json = HttpClientUtil.post(CHANGE_PWD_URL, form, null);
            _jsonMap = JSONUtil.jsonToMap(json);
        } catch (Exception e) {
            logger.error("Error..changePwd...", e);
        }
        return _jsonMap;
    }

    public static Map findPwd(String mobile, String sms_code, String new_pwd) {
        Map _jsonMap = null;
        try {
            Map<String, String> form = new HashMap<>();
            form.put("mobile", mobile);
            form.put("sms_code", sms_code);
            form.put("pwd", new_pwd);
            String json = HttpClientUtil.post(FIND_PWD_URL, form, null);
            _jsonMap = JSONUtil.jsonToMap(json);
        } catch (Exception e) {
            logger.error("Error..findPwd...", e);
        }
        return _jsonMap;
    }

    /**
     * 验证用户手机验证码(兑换柠檬)
     *
     * @param tuid
     * @param access_token
     * @param sms_code
     * @return
     */
    public static Map verifyAuthCode(String tuid, String access_token, String sms_code) {
        Map _jsonMap = null;
        try {
            Map<String, String> form = new HashMap<>();
            form.put("access_token", access_token);
            form.put("tuid", tuid);
            form.put("sms_code", sms_code);
            form.put("type", "3");
            String json = HttpClientUtil.post(VERIFY_CODE_URL, form, null);
            _jsonMap = JSONUtil.jsonToMap(json);
        } catch (Exception e) {
            logger.error("Error..findPwd...", e);
        }
        return _jsonMap;
    }

    /**
     * 通过ID获得token
     *
     * @param tuid
     * @return
     */
    public static String getToken(Object tuid) {
        String token = null;
        try {
            String sign = MD5.digest2HEX(PRIV_KEY + "&userId=" + tuid.toString());
            String json = HttpClientUtil.get(GET_TOKEN_URL + "?_id=" + tuid + "&sign=" + sign, null, HttpClientUtil.UTF8);
            Map _jsonMap = JSONUtil.jsonToMap(json);

            if (((Number) _jsonMap.get("code")).intValue() != 1) {
                return null;
            }
            final Map data = (Map) _jsonMap.get("data");
            return (String) data.get("token");
        } catch (Exception e) {
            logger.error("Error..getToken...", e);
        }
        return null;
    }

    /**
     * 通过微信code注册用户并获取access_token
     *
     * @param wxCode
     * @return
     */
    public static Map getTokenByWeixinCode(String wxCode, String appId, String appSecret) {
        Map _jsonMap = null;
        try {
            Map<String, String> form = new HashMap<>();
            form.put("code", wxCode);
            form.put("app_id", appId);
            form.put("secret", appSecret);
            String json = HttpClientUtil.post(WEIXIN_CODE_URL, form, null);
            _jsonMap = JSONUtil.jsonToMap(json);
        } catch (Exception e) {
            logger.error("Error..findPwd...", e);
        }
        return _jsonMap;
    }

    public static String getOpenidForWeixin(Object tuid, Object app_id) {
        try {
            String json = HttpClientUtil.get(WEIXIN_OPENID_URL + "?_id=" + tuid + "&app_id=" + app_id, null);
            Map _jsonMap = JSONUtil.jsonToMap(json);
            if (((Number) _jsonMap.get("code")).intValue() != 1) {
                return null;
            }
            final Map data = (Map) _jsonMap.get("data");
            return (String) data.get("openid");
        } catch (Exception e) {
            logger.error("Error..getCode...", e);
        }
        return null;
    }
}
