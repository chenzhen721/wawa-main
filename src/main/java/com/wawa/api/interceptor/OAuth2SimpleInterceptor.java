package com.wawa.api.interceptor;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoException;
import com.mongodb.WriteConcern;
import com.wawa.api.UserWebApi;
import com.wawa.api.Web;
import com.wawa.api.event.BuildUserObserver;
import com.wawa.base.data.SimpleJsonView;
import com.wawa.base.persistent.KGS;
import com.wawa.common.doc.MongoKey;
import com.wawa.common.doc.Param;
import com.wawa.common.doc.ParamKey;
import com.wawa.common.util.AuthCode;
import com.wawa.common.util.KeyUtils;
import com.wawa.common.util.RandomExtUtils;
import com.wawa.model.Finance;
import com.wawa.model.User;
import com.wawa.model.UserType;
import groovy.transform.CompileStatic;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import org.springframework.web.util.HtmlUtils;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.wawa.common.doc.MongoKey.$setOnInsert;
import static com.wawa.common.doc.MongoKey._id;

/**
 * Oauth2 认证登录
 * <p/>
 * date: 12-8-24 下午1:52
 */
@CompileStatic
public class OAuth2SimpleInterceptor extends HandlerInterceptorAdapter {

    public void setMainMongo(MongoTemplate mainMongo) {
        this.mainMongo = mainMongo;
    }

    public void setMainRedis(StringRedisTemplate mainRedis) {
        this.mainRedis = mainRedis;
    }

    public void setChatRedis(StringRedisTemplate chatRedis) {
        this.chatRedis = chatRedis;
    }

    public void setUserKGS(KGS userKGS) {
        this.userKGS = userKGS;
    }

    @Resource
    MongoTemplate mainMongo;
    @Resource
    MongoTemplate adminMongo;
    @Resource
    MongoTemplate logMongo;
    @Resource
    StringRedisTemplate mainRedis;
    @Resource
    StringRedisTemplate userRedis;
    @Resource
    StringRedisTemplate chatRedis;
    @Resource
    KGS userKGS;

    @Resource
    WriteConcern writeConcern;

    static final Logger log = LoggerFactory.getLogger(OAuth2SimpleInterceptor.class);

    private static final ThreadLocal<Map<String, Object>> sessionHolder = new ThreadLocal<Map<String, Object>>();

    public static void setSession(Map<String, String> session)
    {
        sessionHolder.set((Map)session);
    }

    public static Map<String, Object> getSession()
    {
        Map<String, Object> map =  sessionHolder.get() ;

        return map ;
    }

    public void postHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView)
            throws Exception {
        sessionHolder.remove();
    }

    static final Integer ROBOT_MAX = 1023956;

    public final boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws ServletException, IOException {
        //request_info(request);

        String tokenValue = parseToken(request);
        if (StringUtils.isNotBlank(tokenValue)) {
            String key = KeyUtils.accessToken(tokenValue);
            String clientId = Web.getClientId(request);
            //log.debug("qd : {} ip:{}  token:{}   path:{}", request.getParameter("qd"), clientId, tokenValue, request.getServletPath());
            /*if (Web.isBanned(clientId)) { // 封掉客户端 uid
                log.info("client is banned ip:{}  token:{}", clientId, tokenValue);
                handleNotAuthorized(request, response, banned);
                userRedis.delete(key);
                return false;
            }*/
            Map obj = userRedis.opsForHash().entries(key);
            if (!isUserInfoValid(obj) || userRedis.getExpire(key) <= 0){
                try{
                    // 尝试从用户系统同步
                    obj = fetchFromUserSystem(tokenValue, clientId, request);
                    if(obj.isEmpty()){
                        log.error("fetchFromUserSystem:tokenValue------------------>:"+tokenValue);
                        handleNotAuthorized(request, response,notAuthorized_null);
                        return false;
                    }
                    String userId = (String) obj.get(MongoKey._id);
                    if (userId != null) {
                        Integer cid = Integer.valueOf(userId);
                        //check_status(new BasicDBObject("status",obj.get("status")));
                        //if (cid > ROBOT_MAX)
                            //Web.day_login(request, cid);
                    }
                } catch (IllegalStateException e) {
                    handleNotAuthorized(request, response, e.getMessage());
                    return false;
                } catch (Exception e) {
                    log.error("获取 TTUS session ERROR :", e);
                    e.printStackTrace();
                }
            }
            if (!obj.isEmpty()) {
                sessionHolder.set(obj);
                return true;
            }
        }
        handleNotAuthorized(request, response, notAuthorized);
        return false;

    }
    // final String nullToken = "{\"code\":30406,\"msg\":\"ACCESS_TOKEN为NULL\"}";
    final String notAuthorized = "{\"code\":30405,\"msg\":\"ACCESS_TOKEN无效\"}";
    final String notAllowed = "{\"code\":30418,\"msg\":\"账户已禁用\" ,\"unfreeze_time\":{unfreeze_time}}";
    final String regTooOften = "{\"code\":30423,\"msg\":\"注册太频繁\"}";
    final String banned = "{\"code\":30421,\"msg\":\"恶意访问，ip，设备被禁\"}";
    final String notAuthorized_null = "{\"code\":30460,\"msg\":\"ACCESS_TOKEN获取用户系统信息为Null\"}";

    //检查session 数据有效性
    static Boolean isUserInfoValid(Map obj){
        if(!obj.isEmpty()){
            return obj.get("_id") != null;
        }
        return Boolean.FALSE;
    }

    protected void handleNotAuthorized(HttpServletRequest request, HttpServletResponse response, String json)
            throws ServletException, IOException {
        String callback = request.getParameter(ParamKey.In.callback);
        if (StringUtils.isNotBlank(callback)) {
            json = callback + '(' + json + ')';
        }
        SimpleJsonView.rennderJson(json, response);
    }

    public static String parseToken(HttpServletRequest request) {
        String token = request.getParameter(ACCESS_TOKEN);
        if (token == null) {
            token = parseHeaderToken(request);
        }

        return token;
    }

    static final String ACCESS_TOKEN = "access_token";

    static final String BEARER_TYPE = "bearer";

    static final String EXPIRES_IN = "expires_in";

    /**
     * Parse the OAuth header parameters. The parameters will be oauth-decoded.
     *
     * @param request The request.
     * @return The parsed parameters, or null if no OAuth authorization header was supplied.
     */
    static String parseHeaderToken(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        Enumeration<String> headers = request.getHeaders("Authorization");
        while (headers.hasMoreElements()) { // typically there is only one (most servers enforce that)
            String value = headers.nextElement();
            if ((value.toLowerCase().startsWith(BEARER_TYPE))) {
                String authHeaderValue = value.substring(BEARER_TYPE.length()).trim();
                int commaIndex = authHeaderValue.indexOf(',');
                if (commaIndex > 0) {
                    authHeaderValue = authHeaderValue.substring(0, commaIndex);
                }
                return authHeaderValue;
            } else {
                // support additional authorization schemes for different token types, e.g. "MAC" specified by
                // http://tools.ietf.org/html/draft-hammer-oauth-v2-mac-token
            }
        }

        return null;
    }


    static final DBObject REG_LIMIT = new BasicDBObject();
    static final String DEFAULT_QD = "wawa_default";
    public static final Long THREE_DAY_SECONDS = 3 * 24 * 3600L;

    Map fetchFromUserSystem(final String access_token, final String clientId, HttpServletRequest req) throws IOException {

        String sQd = req.getParameter("qd");
        String app_ver = req.getParameter("version"); //客户端版本号
        if(StringUtils.isEmpty(sQd)) {
            sQd = DEFAULT_QD ;
        }
        final String qd = sQd ;
        final String child_qd = req.getParameter("child_qd");
        _ThirdUser data = null;
        try{
            if (access_token.charAt(2) == UserFrom.FLAG){
                try{
                    //第三方合作的
                    log.debug("access_token : " + access_token);
                    ThirdUserBuilder builder = UserFrom.from(access_token.substring(0, 3));
                    data = builder.build(access_token);   // ignore nullpoint No enum constant
                    log.debug("data : " + data);
                }
                catch(Exception ex){
                    log.error("access_token:union data------------------>:"+access_token);
                    log.error("Exception : {}", ex);
                }
            }else{
                data = UserFrom.来吼.build(access_token);
            }
        }
        catch(Exception ex)
        {
            log.error("access_token: data------------------>:"+access_token);
            ex.printStackTrace();
        }
        DBObject user = buildShowUser(qd, clientId, data,child_qd,app_ver);
        if (user == REG_LIMIT) {
            throw new IllegalStateException(regTooOften);
        }
        check_status(user);
        Integer userId = (Integer) user.get("_id");
        Map<String, String> hashResult = Web.refreshUserInfoOfSession(req, userId, user);
        if (Boolean.TRUE.equals(user.get("is_register"))) {
            //Web.day_register(req, user);
        }
        userRedis.opsForValue().set(KeyUtils.USER.token(userId), access_token, THREE_DAY_SECONDS, TimeUnit.SECONDS);
        return hashResult;
    }

    static final String[] needFields = {"user_name", "via", "sex", "pic","mobile_bind", "uname_bind", "email", "weixin_focus", "third_token", "third"};

    static final Long REG_LIMIT_SECONDS = 600L;
    static final String TOTAL_REG_PER_IP = "10";

    private final static String NICK_NAME_PREFIX = "萌新";

    DBObject buildShowUser(String qd, final String clientId, final _ThirdUser basicInfoWithTuid,final String child_qd,final String app_ver) {
        final Object tuid =  basicInfoWithTuid != null ? basicInfoWithTuid.tuid : null ;

        if (null == tuid) {
            throw new IllegalStateException(notAuthorized);
        }
        DBCollection users = mainMongo.getCollection("users");
        BasicDBObject query_tuid = new BasicDBObject("tuid", tuid);
        DBObject user = users.findOne(query_tuid);
        if (user != null) {
            return user;
        }

        user = new BasicDBObject(_id, userKGS.nextId());
        //生成么么号
        user.put("mm_no",  user.get(_id));

        String nick_name = basicInfoWithTuid.nick_name;
        //生成nickname
        if (StringUtils.isEmpty(nick_name)) {
            nick_name = NICK_NAME_PREFIX + RandomExtUtils.random(6);
        }
        for (String field : needFields) {
            Object value = basicInfoWithTuid.get(field);
            if (null != value) {
                user.put(field, value);
            }
        }

        user.put("nick_name", HtmlUtils.htmlEscape(nick_name));
        user.put("tuid", tuid);
        user.put("priv", UserType.普通用户.ordinal());
        user.put("status", Boolean.TRUE);
        user.put(User.Exp, 0l);
        user.put(User.Level, 1);

        //完成任务
//        DBObject complete_mission = new BasicDBObject();
//        complete_mission.put(Mission.注册.id, Mission.Status.完成未领取奖金.ordinal());
//        //手机注册用户
//        if(basicInfoWithTuid.get(Mission.绑定手机.id) != null
//                && Boolean.valueOf(basicInfoWithTuid.get(Mission.绑定手机.id).toString())){
//            //log.info("buildShowUser mobile_bind: {}:{}", tuid,basicInfoWithTuid.get(Mission.绑定手机.id));
//            complete_mission.put(Mission.绑定手机.id, Mission.Status.完成未领取奖金.ordinal());
//        }

//        user.put("mission", complete_mission);
        user.put(Param.timestamp, System.currentTimeMillis());
        DBObject finance = new BasicDBObject();
        finance.put(Finance.coin_count, 0l);
        Integer diamond = awardDiamond();
        finance.put(Finance.diamond_count, diamond);
        user.put("finance", finance);
        if (StringUtils.isNotBlank(qd)) {
            //判断渠道ID是否存在
            if(!qd.equals(DEFAULT_QD) &&
                    adminMongo.getCollection("channels").count(new BasicDBObject(_id, qd)) == 0){
                user.put("origin_qd", qd); //原始问题渠道ID
                qd = DEFAULT_QD;
            }
            user.put("qd", qd);
            /*if (StringUtils.isNotBlank(child_qd)){
                String child_id = qd+"_"+child_qd ;
                user.put("qd", child_id);
                user.put("parent_qd", qd);
                Web.saveChannel(qd,child_id);
            }*/
        }
        if (StringUtils.isNotBlank(app_ver)) {
            user.put("app_ver", app_ver);
        }

        try {
            //beforeBuildShowUser(user);
            DBObject newUser = users.findAndModify(query_tuid.append(_id, user.removeField(_id)), null,
                    null, false,
                    new BasicDBObject($setOnInsert, user), true, true);
            //同步么么号
            if(newUser != null ){
                if( basicInfoWithTuid.local_user){
                    UserWebApi.synNo(tuid, newUser.get("mm_no"));
                }
                /*Map<String, Integer> award = new HashMap<>();
                award.put("diamond", diamond);
                Web.saveDiamondLog(Integer.valueOf(newUser.get(_id).toString()), UserAwardType.新用户注册, award);
                newUser.put("is_register", Boolean.TRUE);*/
            }
            BuildUserObserver.fireAfterBuildUserEvent(newUser);
            return newUser;
        } catch (MongoException e) {
            log.error("TUID Error..upsert...", e);
            query_tuid.remove(_id);
            return users.findOne(query_tuid);
        }
    }

    final static Integer DIAMOND_MIN = 58;
    //final static Integer CASH_MAX = 200;

    //随机获得现金及奖励
    private Integer awardDiamond(){
        return DIAMOND_MIN;
        //return RandomExtUtils.randomBetweenMinAndMax(CASH_MIN, CASH_MAX);
    }

    private void check_status(DBObject user){
        String status = "true" ;
        if(null != user.get("status"))
            status = user.get("status").toString() ;
        if (Boolean.TRUE != Boolean.parseBoolean(status)){
            String notAllowedWithTime = StringUtils.replace(notAllowed, "{unfreeze_time}", String.valueOf(user.get("unfreeze_time")));
            throw new IllegalStateException(notAllowedWithTime);
        }
    }

}
