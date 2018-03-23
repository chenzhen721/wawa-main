package com.wawa.api;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;
import com.mongodb.WriteConcern;
import com.wawa.AppProperties;
import com.wawa.api.interceptor.OAuth2SimpleInterceptor;
import com.wawa.base.StaticSpring;
import com.wawa.base.ext.RestExtension;
import com.wawa.common.doc.Level;
import com.wawa.common.doc.MongoKey;
import com.wawa.common.doc.Param;
import com.wawa.common.util.KeyUtils;
import com.wawa.common.util.WebUtils;
import com.wawa.model.BlackListType;
import com.wawa.model.PlatformType;
import com.wawa.model.StatusType;
import com.wawa.model.User;
import com.wawa.model.UserAwardType;
import com.wawa.model.UserType;
import groovy.transform.CompileStatic;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.SortParameters;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.ServletRequestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.wawa.common.doc.MongoKey.$setOnInsert;
import static com.wawa.common.doc.MongoKey._id;
import static com.wawa.common.doc.MongoKey.timestamp;


@CompileStatic
public abstract class Web  extends WebUtils {

    @Resource
    public static WriteConcern writeConcern;

    public static final StringRedisTemplate liveRedis = (StringRedisTemplate) StaticSpring.get("liveRedis");
    public static final StringRedisTemplate mainRedis = (StringRedisTemplate) StaticSpring.get("mainRedis");
    public static final StringRedisTemplate userRedis = (StringRedisTemplate) StaticSpring.get("userRedis");
    public static final StringRedisTemplate chatRedis = (StringRedisTemplate) StaticSpring.get("chatRedis");
    public static final StringRedisTemplate imRedis = (StringRedisTemplate) StaticSpring.get("imRedis");
    public static final MongoTemplate mainMongo = (MongoTemplate) StaticSpring.get("mainMongo");
    public static final MongoTemplate logMongo = (MongoTemplate) StaticSpring.get("logMongo");
    public static final MongoTemplate adminMongo = (MongoTemplate) StaticSpring.get("adminMongo");
    public static final MongoTemplate familyMongo = (MongoTemplate) StaticSpring.get("familyMongo");
    public static final MongoTemplate unionMongo = (MongoTemplate) StaticSpring.get("unionMongo");

    public static final boolean isTest = AppProperties.get("api.domain").contains("test-");

    /**
     * 配合 nginx URL 重写
     * rewrite      /([a-z]+/[a-z_]+)/([a-z0-9\-]+)/(\d+)/?([\d_]+)?\??(.*)
     * @return
     */
    public static Integer roomId(HttpServletRequest request){
        return firstNumber(request);
    }

    public static Integer userId(HttpServletRequest request){
        return secondNumber(request);
    }

    public static Integer secondNumber(HttpServletRequest request){
        Integer secondNumber = 0 ;
        try{
            String id2 = request.getParameter(Param.second);
            if(StringUtils.isNotBlank(id2))
                secondNumber = Integer.valueOf(id2);
        }catch(Exception ex){
            logger.error("secondNumber String cast Integer Exception");
        }
        return  secondNumber ;
    }

    public static Integer firstNumber(HttpServletRequest request){
        Integer firstNumber = 0 ;
        try{
            String id1 = request.getParameter(Param.first) ;
            if(StringUtils.isNotBlank(id1))
                firstNumber = Integer.valueOf(id1);
        }catch(Exception ex){
            logger.error("firstNumber String cast Integer Exception, {}", ex);
        }
        return  firstNumber ;
    }

    public static String firstParam(HttpServletRequest request){
        return request.getParameter(Param.first);
    }

    public static String secondParam(HttpServletRequest request){
        return request.getParameter(Param.second);
    }

    public static List<Integer> getFollowing(Integer userId){
        Set<String> sets =  mainRedis.opsForSet().members(KeyUtils.USER.following(userId));
        if(sets==null){
            return null;
        }
        List<Integer> list = new ArrayList<Integer>(sets.size());
        for(String id : sets){
            list.add(Integer.valueOf(id));
        }
        return list;
    }

    /**
     * 获得用户昵称
     * @param userId
     * @return
     */
    public static String getUserNickNameById(Integer userId){
        DBObject user = getUserInfo(userId);
        if(user == null){
            return null;
        }
        return (String)user.get("nick_name");
    }

    public final static DBObject user_field = $$("nick_name",1)
            .append("pic", 1)
            .append("priv", 1)
            .append("level", 1)
            .append("family", 1)
            .append("vip_level", 1)
            .append("exp",1);
    /**
     * 获得用户前端展示信息
     * @param userId
     * @return
     */
    public static DBObject getUserInfo(Integer userId){
        if(userId == null){
            return new BasicDBObject();
        }
        return mainMongo.getCollection("users").findOne(userId, user_field);
    }

    /**
     * 获得用户前端展示信息包含家族信息
     * @param userId
     * @return
     */
    public static DBObject getUserInfoWithFamily(Integer userId){
        DBObject user =getUserInfo(userId);
        //wrapFamilyinfo(user);
        return user;
    }

    /**
     * 获得家族信息
     * @param user
     * @return
     */
    public static void wrapFamilyinfo(DBObject user){
        if(user.get("family") != null){
            Map family = (Map) user.get("family");
            Integer family_id = (Integer)family.get("family_id");
            DBObject myFamily = familyMongo.getCollection("familys").findOne($$(_id, family_id).append("status", StatusType.通过.ordinal()));
            family.put(_id,family_id);
            family.put("badge_name", myFamily.get("badge"));
            family.put("family_name", myFamily.get("name"));
            user.put("family", family);
        }
    }

    public static DBObject getFamilyinfo(Integer familyId){
        DBObject family = familyMongo.getCollection("familys").findOne($$(_id, familyId).append("status", StatusType.通过.ordinal()),
                $$("name",1).append("badge", 1).append("pic", 1).append("prestige", 1));
        if(family != null){
            Long prestige = 0l;
            if(family.containsField("prestige")){
                prestige = Long.valueOf(family.get("prestige").toString());
            }
            family.put("level", Level.familyLevel(prestige));
        }
        return family;
    }

    public static boolean codeVeri(HttpServletRequest req, Integer uid) {
        String auth_code = req.getParameter("auth_code");
        String key =  KeyUtils.USER.authCode(uid);
        String red_code = mainRedis.opsForValue().get(key);
        logger.debug("auth_code : {}", auth_code);
        logger.debug("auth_key : {}", key);
        logger.debug("red_code : {}", red_code);
        mainRedis.delete(key);
        if (null == auth_code || !auth_code.equalsIgnoreCase(red_code)) {
            return false;//[code: 30419]
        }
        return true;
    }

    public static Map currentUser(){
        return getSession();
    }

    public static Integer getCurrentFamilyId(){
        Map map = getSession();
        String familyId = "0" ;
        if(null != map && map.containsKey("family_id"))
            familyId = map.get("family_id").toString() ;
        else
            logger.error("currentUserId:OAuth2SimpleInterceptor.getSession is----->: null");

        return Integer.valueOf(familyId);
    }

    public static Integer getCurrentFamilyPriv(){
        Map map = getSession();
        String family_priv = "0" ;
        if(null != map && map.containsKey("family_priv"))
            family_priv = map.get("family_priv").toString() ;
        else
            logger.error("currentUserId:OAuth2SimpleInterceptor.getSession is----->: null");

        return Integer.valueOf(family_priv);
    }

    public static Integer getCurrentUserId(){
        Integer uid = 0 ;
        uid = Integer.valueOf(currentUserId());
        return uid ;
    }
    final static  Logger logger = LoggerFactory.getLogger(Web.class) ;
    public static String currentUserId(){
        Map map = getSession();
        String userId = "0" ;
        if(null == map)
            logger.error("currentUserId:OAuth2SimpleInterceptor.getSession is----->: null");
        else
            userId = map.get(_id).toString() ;

        return userId;
    }

    public static String currentUserNick(){
        Map map = getSession();
        String nickName = "----" ;
        if(null == map)
            logger.error("currentUserNick:OAuth2SimpleInterceptor.getSession is---->: null");
        else
            nickName = (String)map.get("nick_name");

        return nickName ;
    }
    public static String getCurrentUserPic(){
        Map map = getSession();
        String pic = "" ;
        if(null == map)
            logger.error("currentUserNick:OAuth2SimpleInterceptor.getSession is---->: null");
        else
            pic = (String)map.get("pic");

        return pic ;
    }

    public static int currentUserType(){
        Integer priv = 3 ;
        try{
            Map map = currentUser() ;
            if(null !=map)
                priv = Integer.valueOf(map.get("priv").toString());
        }catch(Exception ex){
            priv = 3 ;
        }
        return priv ;
    }

    public static int getCurrentUserLevel(){
        Integer priv = 3 ;
        try{
            Map map = currentUser() ;
            if(null !=map)
                priv = Integer.valueOf(map.get("level").toString());
        }catch(Exception ex){
            priv = 1 ;
        }
        return priv ;
    }

    public static Map getSession(){
        return OAuth2SimpleInterceptor.getSession();
    }


    /**
     * 通过用户ID 获取token
     * @param user_id
     * @return
     */
    public static String id2token(Integer user_id){
        String token = null;
        DBObject user = mainMongo.getCollection("users").findOne(user_id, new BasicDBObject("tuid",1));
        if(user != null){
            String tuid = user.get("tuid").toString();
            token = UserWebApi.getToken(tuid);
        }
        return token;
    }



    public static BasicDBObject logCost(String type, Integer cost, Integer roomId){
        return logCost(type, cost, roomId, null) ;
    }

    public static BasicDBObject logCost(String type, Integer cost, Integer roomId, Integer family_id){
        Map<String, Object> obj = new HashMap<>();
        obj.put("type", type);
        obj.put("cost", cost);

        if(roomId!=null)
            obj.put("room", roomId);

        obj.put("session", Web.getSession());
        obj.put("timestamp", System.currentTimeMillis());
        obj.put("createdAt", new Date());
        if (Web.getSession() != null && Web.getSession().get("_id") != null) {
            String _id = String.valueOf(Web.getSession().get("_id"));
            obj.put("user_id", Integer.valueOf(_id));
        }

        if(family_id != null)//用户所在家族ID
            obj.put("family_id", family_id);

        return new BasicDBObject(obj);
    }


    public static Date getEtime(HttpServletRequest request){
        return getTime(request,"etime");
    }

    public static Date getStime(HttpServletRequest request){
        return getTime(request,"stime");
    }
    public static final String DFMT = "yyyy-MM-dd";
    private static Date getTime(HttpServletRequest request, String key)  {
        String str = request.getParameter(key);
        if(StringUtils.isNotBlank(str)){
            try {
                return new SimpleDateFormat(DFMT).parse(str);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
    public static QueryBuilder fillTimeBetween(HttpServletRequest req){
        QueryBuilder query = QueryBuilder.start();
        Date stime = getStime(req);
        Date etime = getEtime(req);
        if (stime !=null || etime !=null){
            query.and("timestamp");
            if(stime != null){
                query.greaterThanEquals(stime.getTime());
            }
            if (etime != null){
                query.lessThan(etime.getTime());
            }
        }
        return query;
    }



    public static boolean isBanned(HttpServletRequest req){
        return isBanned(getClientId(req));
    }

    public static boolean isBanned(String clientId){
        return liveRedis.hasKey(KeyUtils.USER.blackClient(clientId));
    }

    /**
     * 用户是否在线
     * @param userId
     * @return
     */
    public static boolean isOnline(Integer userId){
        return userRedis.opsForHash().hasKey(KeyUtils.user_online_list(), userId.toString());
    }

    /**
     * 用户是否在线
     * @param userIds
     * @return
     */
    public static Set<Integer> isOnline(final List<Integer> userIds){
        Set<Integer> onlines = new HashSet<>();
        List results = (List) userRedis.execute(new RedisCallback<List<Object>>() {
            public List<Object> doInRedis(RedisConnection connection) throws DataAccessException {
                connection.openPipeline();
                for (Integer userId : userIds) {
                    connection.hExists(KeyUtils.serializer(KeyUtils.user_online_list()), KeyUtils.serializer(userId.toString()));
                }
                return connection.closePipeline();
            }
        });
        int index = 0;
        for (Integer userId : userIds) {
            if((Boolean)results.get(index++)){
                onlines.add(userId);
            }
        }
        logger.debug("isOnline userIds : {}", userIds);
        logger.debug("Onlines : {}", onlines);
        return null;
    }

    /**
     * 用户是否在房间内
     * @param userId
     * @return
     */
    public static boolean isInRoom(Integer roomId, Integer userId){
        return userRedis.opsForSet().isMember(KeyUtils.ROOM.users(roomId), userId.toString());
    }

    /**
     * 是否包含黑白名单
     * @param userId
     * @param blackListType
     * @return
     */
    public static boolean isInBlackList(Integer userId, BlackListType blackListType){
        return isInBlackList(userId + "_" + blackListType.ordinal());
    }

    /**
     * 是否包含黑白名单
     */
    public static boolean isInBlackList(String bid){
        return adminMongo.getCollection("blacklist").count($$(_id, bid)) >= 1;
    }

    /**
     * 获取用户VIP等级，需要更新
     * @return
     */
    public static Integer setVip(Integer userId) {
        DBObject user = mainMongo.getCollection("users").findOne(
                new BasicDBObject("_id", userId).append("vip_level", new BasicDBObject("$exists", Boolean.TRUE)),
                new BasicDBObject("vip_level", 1).append("vip_expires", 1));
        if (user == null) {
            return 0;
        }
        String key = KeyUtils.USER.vip(user.get("_id"));
        String vip_level = user.get("vip_level").toString();
        Long expire = Long.parseLong(user.get("vip_expires").toString());
        if (expire <= System.currentTimeMillis()) {
            return 0;
        }
        mainRedis.opsForValue().set(key, vip_level, expire, TimeUnit.MILLISECONDS);
        return Integer.parseInt(vip_level);
    }

    public static Integer setVip(DBObject user) throws Exception {
        if (user == null || !user.containsField("_id")) {
            throw new Exception("invalid user info with none user_id when set user vip");
        }
        if (!user.containsField("vip_level") || !user.containsField("vip_expires")) {
            return setVip(Integer.valueOf(user.get("_id").toString()));
        }
        String key = KeyUtils.USER.vip(user.get("_id"));
        String vip_level = user.get("vip_level").toString();
        Long expire = Long.parseLong(user.get("vip_expires").toString());
        if (expire <= System.currentTimeMillis()) {
            return 0;
        }
        Long expire_time = (expire - System.currentTimeMillis()) / 1000L;
        mainRedis.opsForValue().set(key, vip_level, expire_time, TimeUnit.SECONDS);
        return Integer.parseInt(vip_level);

    }

    public static Integer getVip(Integer userId) {
        String key = KeyUtils.USER.vip(userId);
        String val = mainRedis.opsForValue().get(key);
        Integer vip_level;
        if (StringUtils.isNotBlank(val)) {
            vip_level = Integer.parseInt(val);
        } else {
            vip_level = 0;
        }
        return vip_level;
    }

    public static Integer getVip(DBObject user) throws Exception {
        if (user == null || !user.containsField("_id")) {
            throw new Exception("invalid user info with none user_id when get user vip");
        }
        String key = KeyUtils.USER.vip(user.get("_id"));
        String val = mainRedis.opsForValue().get(key);
        Integer vip_level;
        if (StringUtils.isNotBlank(val)) {
            vip_level = Integer.parseInt(val);
        } else {
            vip_level = 0;
        }
        return vip_level;
    }

    public static String getClientId(HttpServletRequest req){
        String client_id = req.getParameter(Param.uid);
        if(StringUtils.isNotBlank(client_id)){
            return client_id;
        }
        return getClientIp(req);
    }

    public static String getClientIp(HttpServletRequest req){
        /*String ip = req.getHeader(Param.XFF);
        if(StringUtils.isBlank(ip)){
            ip = req.getRemoteAddr();
        }
        ip = StringUtils.remove(ip, ", 192.168.2.20");
        ip = StringUtils.remove(ip, ", 192.168.2.21");*/
        return getIpAddr(req);
    }

    private static String getIpAddr(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");//先从nginx自定义配置获取
        //logger.info("X-Forwarded-For: {}", ip);
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
            //logger.info("X-Real-IP: {}", ip);
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("PROXY_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {

            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        //ip = StringUtils.remove(ip, ", 192.168.2.20");
        //ip = StringUtils.remove(ip, ", 192.168.2.21");
        //logger.info(">>>>>>>>>>>>> Ip: {}", ip);
        //如果为多个切成1个
        ip = StringUtils.substringBefore(ip, ",");
        return ip;
    }

    public static Map<String, String> getHeaderInfo(HttpServletRequest request){
        Map<String, String> map = new HashMap<String, String>();
        Enumeration headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String key = (String) headerNames.nextElement();
            String value = request.getHeader(key);
            map.put(key, value);
        }
        logger.debug("request header info : {}", map);
        String User_Agent = map.get("User-Agent");

        logger.debug("request header iUser_Agent:{},  ip : {}", User_Agent, getClientIp(request));
        return map;
    }

    public static String getClientType(HttpServletRequest req) {
        return req.getHeader("clientType");
    }

    public static final String getBlackBox(HttpServletRequest req) {
        return req.getHeader("blackBox");
    }

    public static void setSpend(Integer userId, String field, Long coin_spend_total){
        putUserInfoToSession(userId,field, String.valueOf(coin_spend_total));
    }

    public static DBObject day_login(HttpServletRequest req, Integer uid){
        Date time = new Date();
        Long tmp = time.getTime();
        String id = new SimpleDateFormat("yyyyMMdd_").format(time) + uid;
        Map<String,Object> setOnInsert = new HashMap<>();
        setOnInsert.put("user_id",uid);
        setOnInsert.put(timestamp,tmp);
        String mobileId = req.getParameter(Param.uid);
        if(StringUtils.isNotBlank(mobileId)){
            setOnInsert.put("uid",mobileId);
        }
        String ip = getClientIp(req);
        Integer platform = ServletRequestUtils.getIntParameter(req, "p", PlatformType.android.ordinal());
        setOnInsert.put("ip",ip);
        //添加qd
        DBObject user = mainMongo.getCollection("users").findOne(new BasicDBObject(_id,uid),
                new BasicDBObject("qd",1)
                        .append(User.Level, 1)
                        .append(User.Exp, 1));
        String qd = null;
        if(user == null) return null;

        if(null != user.get("qd"))
            qd = user.get("qd").toString();
        if(StringUtils.isNotEmpty(qd))
            setOnInsert.put("qd",qd);

        setOnInsert.put("platform",platform);

        if (req.getHeader("blackBox") != null) {
            setOnInsert.put("blackBox", req.getHeader("blackBox"));
        }
        if (req.getHeader("clientType") != null) {
            setOnInsert.put("clientType", req.getHeader("clientType"));
        }

        if(logMongo.getCollection("day_login").findAndModify(new BasicDBObject(_id,id),null,
                null,false,
                new BasicDBObject($setOnInsert,setOnInsert),true,true //upsert
        ).get(timestamp).equals(tmp)){
            mainMongo.getCollection("users").update($$(_id,uid), $$(MongoKey.$set, $$("last_login", System.currentTimeMillis())));
            /*if(User.VIP.HIGH_LEVEL.equals(user.get(User.VIP.vip))){
                Long ttlMills = (Long) user.get(User.VIP.vip_expires) - System.currentTimeMillis();
                if(ttlMills > 0){
                    mainRedis.opsForValue().set(KeyUtils.USER.vip_limit(uid),
                            User.VIP.MANAGE_LIMIT.toString(),ttlMills,TimeUnit.MILLISECONDS);
                }
                if(user.containsField(Finance.finance)){
                    Map finance = (Map) user.get(Finance.finance);
                    if(finance.containsKey(Finance.coin_spend_total)){
                        Long coin_spend = (Long) finance.get(Finance.coin_spend_total);
                        putUserInfoToSession(req, "spend",coin_spend.toString());
                    }
                }
            }*/
            putUserInfoToSession(req, "platform", platform.toString());
        }
        return user;
    }

    public static void day_register(HttpServletRequest req, DBObject newUser){
        Date time = new Date();
        Long tmp = time.getTime();
        String id = new SimpleDateFormat("yyyyMMdd_").format(time) + newUser.get(_id);
        Map<String,Object> setOnInsert = new HashMap<>();
        setOnInsert.put("user_id",newUser.get(_id));
        setOnInsert.put(timestamp,tmp);
        String ip = getClientIp(req);
        setOnInsert.put("ip",ip);

        Integer platform = ServletRequestUtils.getIntParameter(req, "p", PlatformType.android.ordinal());
        setOnInsert.put("platform",platform);
        if (req.getHeader("blackBox") != null) {
            setOnInsert.put("blackBox", req.getHeader("blackBox"));
        }
        if (req.getHeader("clientType") != null) {
            setOnInsert.put("clientType", req.getHeader("clientType"));
        }

        logMongo.getCollection("day_register").findAndModify(new BasicDBObject(_id,id),null,
                null,false,
                new BasicDBObject($setOnInsert,setOnInsert),true,true //upsert
        );
    }


    public static List<byte[]> redisSort(String key, final SortParameters sortParam){
        final byte[] bytesKey = RestExtension.asBytes(key);
        return userRedis.execute(new RedisCallback<List<byte[]>>() {
            public List<byte[]> doInRedis(RedisConnection connection) throws DataAccessException {
                return connection.sort(bytesKey, sortParam);
            }
        });
    }


    public static boolean isStar(){
        return UserType.主播.ordinal() == currentUserType();
    }

    public static void saveChannel(String qd, String child_qd)
    {
        Map<String,Object> setOnInsert = new HashMap<>();
        setOnInsert.put("client","1");
        setOnInsert.put("comment","");
        setOnInsert.put("name",child_qd);
        setOnInsert.put("parent_qd",qd);
        setOnInsert.put("type","1");
        long tmp = System.currentTimeMillis();
        setOnInsert.put(timestamp,tmp);

        adminMongo.getCollection("channels").findAndModify($$(_id, child_qd), null, null, false,
                new BasicDBObject($setOnInsert, setOnInsert), true, true) ;

    }

    /**
     * 通过token获得用户信息
     * @param req
     * @return
     */
    public static Map getUserByAccessToken(HttpServletRequest req){
        String tokenValue = OAuth2SimpleInterceptor.parseToken(req);
        Map obj = null;
        if (StringUtils.isNotBlank(tokenValue)) {
            String token_key = KeyUtils.accessToken(tokenValue);
            if(!userRedis.hasKey(token_key)){
                // refresh the token
                try{
                    Object data = WebApi.api("user/info?access_token="+tokenValue);
                    if (data != null) {
                        obj = (Map) data;
                    }
                }catch (Exception e){
                    logger.error("refresh api error : {}", e);
                }
            } else {
                obj = userRedis.opsForHash().entries(token_key);
            }
        }
        return obj;
    }

    /**
     * 同步用户session信息
     * @param req parseToken from HttpServletRequest
     * @param field
     * @param value
     */
    public static void putUserInfoToSession(HttpServletRequest req, String field, String value){
        String token_key = KeyUtils.accessToken(OAuth2SimpleInterceptor.parseToken(req));
        putUserInfoToSession(token_key, field, value);
    }
    public static final Long THREE_DAY_SECONDS = 3 * 24 * 3600L;

    /**
     * 刷新在线用户缓存信息
     * @param userId
     * @return
     */
    public static Map<String, String> refreshUserInfoOfSession(Integer userId){
        return refreshUserInfoOfSession("", userId, null);
    }

    public static Map<String, String> refreshUserInfoOfSession(HttpServletRequest req, Integer userId){
        return refreshUserInfoOfSession(req, userId, null);
    }

    public static Map<String, String> refreshUserInfoOfSession(HttpServletRequest req, Integer userId, DBObject user){
        String access_token = OAuth2SimpleInterceptor.parseToken(req);
        return refreshUserInfoOfSession(access_token, userId, user);
    }

    public static Map<String, String> refreshUserInfoOfSession(String access_token, Integer userId, DBObject user){
        Map<String, String> hashResult = new HashMap<>();
        if(StringUtils.isEmpty(access_token)){
            access_token = userRedis.opsForValue().get(KeyUtils.USER.token(userId));
            if(StringUtils.isEmpty(access_token)){
                return hashResult;
            }
        }
        if(user == null){
            user = getUserInfo(userId);
        }
        hashResult.put("_id", userId.toString());
        hashResult.put("nick_name", (String) user.get("nick_name"));
        hashResult.put("priv", user.get("priv").toString());
        hashResult.put("pic", String.valueOf(user.get("pic")));
        hashResult.put("level", String.valueOf(user.get("level")));
        Map family = (Map) user.get("family");
        if(family != null){
            hashResult.put("family_name", String.valueOf(family.get("name")));
            hashResult.put("family_badge", String.valueOf(family.get("badge")));
            hashResult.put("family_id", String.valueOf(family.get("family_id")));
            hashResult.put("family_priv", String.valueOf(family.get("family_priv")));
        }
        String token_key = KeyUtils.accessToken(access_token);
        if(userRedis.hasKey(token_key)){
            userRedis.delete(token_key);
        }
        userRedis.opsForHash().putAll(token_key, hashResult);
        if(userRedis.getExpire(token_key) < 30){
            userRedis.expire(token_key, THREE_DAY_SECONDS, TimeUnit.SECONDS);
            userRedis.opsForValue().set(KeyUtils.USER.token(userId), access_token, THREE_DAY_SECONDS, TimeUnit.SECONDS);
        }
        return hashResult;
    }

    private static void putUserInfoToSession(String token_key, String field, String value){
        if(token_key != null && userRedis.getExpire(token_key) > 30){
            userRedis.opsForHash().put(token_key,field,value);
        }
    }
    private static void putUserInfoToSession(Integer userId, String field, String value){
        String id2token = KeyUtils.USER.token(userId);
        String access_token = userRedis.opsForValue().get(id2token);
        putUserInfoToSession(KeyUtils.accessToken(access_token), field, value);
    }

    public static final Integer RobotId = 1024000;  //机器人id

    public static List<Integer> getRoomViewers(Integer room_id){
        Set<String> viewers = userRedis.opsForSet().members(KeyUtils.ROOM.users(room_id));
        ArrayList<Integer> lst = new ArrayList<Integer>();
        if(viewers == null) return lst;
        for (String str : viewers){
            Integer tempUserId = Integer.valueOf(str);
            if (tempUserId > RobotId)
                lst.add(tempUserId); //过滤机器人
        }
        return lst;
    }

    /**
     * 是否为机器人
     * @param userId
     * @return
     */
    public static Boolean isRobot(Integer userId){
        return userId <= RobotId;
    }

    private static List<Integer> ROBOT_IDS;

    /**
     * 获取机器人ID列表
     * @return
     */
    public static List<Integer> getRoBotIdList(){
        try {
            if(ROBOT_IDS == null){
                DBObject query = new BasicDBObject("via", "robot");
                DBObject fields = new BasicDBObject("_id", 1);
                List<DBObject> robots =mainMongo.getCollection("users").find(query,fields).toArray();
                ROBOT_IDS = new ArrayList<>(robots.size());
                for(DBObject robot : robots){
                    ROBOT_IDS.add(Integer.valueOf(robot.get("_id").toString()));
                }
            }
        }catch (Exception e){
            logger.error("initRoBot Exception : {}", e);
        }
        return ROBOT_IDS;
    }

    public static void saveAwardLog(Integer familyId, Integer userId, UserAwardType userAwardType, Object award){
        try{
            BasicDBObject log = awardLog(userId, userAwardType, award);
            if (familyId != null) {
                log.put("room_id", familyId);
            }
            logMongo.getCollection("user_award_logs").save(log);
        }catch (Exception e){
            logger.error("saveAwardLog Exception : {}", e);
        }
    }

    public static void saveAwardLog(BasicDBObject logWithId, Object award){
        try{
            logMongo.getCollection("user_award_logs").save(logWithId);
        }catch (Exception e){
            logger.error("saveAwardLog Exception : {}", e);
        }
    }

    /**
     * 生成统一的奖励记录
     * @param userId
     * @param userAwardType
     * @param award
     * @return
     */
    public static BasicDBObject awardLog(Integer userId, UserAwardType userAwardType, Object award){
        Long time = System.currentTimeMillis();
        String _id = userId+"_"+userAwardType.getId()+"_"+time;
        BasicDBObject log = new BasicDBObject("_id",_id);
        log.append("user_id", userId)
                .append("type", userAwardType.getId())
                .append("award", award)
                .append("timestamp", time);
        return log;
    }

    public static void saveAwardLog(Integer userId, UserAwardType userAwardType, Object award){
        saveAwardLog(null, userId, userAwardType, award);

    }

    /**
     * 记录钻石奖励
     * @param userId
     * @param userAwardType
     * @param award
     */
    public static void saveDiamondLog(Integer userId, UserAwardType userAwardType, Object award){
        try{
            BasicDBObject log = awardLog(userId, userAwardType, award);
            logMongo.getCollection("diamond_add_logs").save(log);
        }catch (Exception e){
            logger.error("saveDiamondLog Exception : {}", e);
        }
    }

}
