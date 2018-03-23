package com.wawa.base;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;
//import com.ttpod.rest.AppProperties;
//import com.ttpod.rest.web.support.ControllerSupport7;
//import com.ttpod.star.common.util.MsgExecutor;
//import com.ttpod.star.model.AppPropType;
//import com.ttpod.star.model.User;
//import com.ttpod.star.web.api.DoCost;
//import com.ttpod.star.web.api.Web;
import com.wawa.AppProperties;
import com.wawa.api.DoCost;
import com.wawa.api.Web;
import com.wawa.base.support.ControllerSupport7;
import com.wawa.common.util.MsgExecutor;
import com.wawa.model.AppPropType;
import com.wawa.model.User;
import groovy.transform.CompileStatic;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wawa.api.Web.chatRedis;
import static com.wawa.common.doc.MongoKey.*;
import static com.wawa.common.util.WebUtils.$$;
import static com.wawa.model.Finance.finance$cash_count;
import static com.wawa.model.Finance.finance$coin_count;
import static com.wawa.model.Finance.finance$diamond_count;

//import static com.ttpod.rest.common.doc.MongoKey.*;
//import static com.ttpod.rest.common.util.WebUtils.$$;
//import static com.ttpod.star.model.Finance.*;
//import static com.ttpod.star.web.api.Web.chatRedis;

/**
 * BaseController<
 */
@CompileStatic
@Slf4j
public abstract class BaseController extends ControllerSupport7 {

    @Resource
    public MongoTemplate mainMongo;
    @Resource
    public MongoTemplate adminMongo;
    @Resource
    public MongoTemplate topicMongo;
    @Resource
    public MongoTemplate logMongo;
    @Resource
    public MongoTemplate unionMongo;
    @Resource
    public MongoTemplate activeMongo;
    @Resource
    public MongoTemplate rankMongo;
    @Resource
    public MongoTemplate familyMongo;
    @Resource
    public MongoTemplate friendMongo;
    @Resource
    public MongoTemplate catchMongo;

    public static final String SHOW_URL = AppProperties.get("site.domain");
    public static final String h5_SHOW_URL = AppProperties.get("h5.domain");
    public static final StringRedisTemplate mainRedis = Web.mainRedis;
    public static final StringRedisTemplate userRedis = Web.userRedis;
    /*public static final StringRedisTemplate chatRedis = Web.chatRedis;
    public static final StringRedisTemplate imRedis = Web.imRedis;*/
    @Resource
    public StringRedisTemplate liveRedis;

    static final Logger logs = LoggerFactory.getLogger(BaseController.class);
    @Resource
    public WriteConcern writeConcern;

    public DBCollection users() {
        return mainMongo.getCollection("users");
    }

    public DBCollection missions() {
        return adminMongo.getCollection("missions");
    }

    public DBCollection familys() {
        return familyMongo.getCollection("familys");
    }
    public DBCollection missionLogs() {
        return logMongo.getCollection("mission_logs");
    }

    public DBCollection financeLog() {
        return adminMongo.getCollection("finance_log");
    }

    public DBCollection rooms() {
        return mainMongo.getCollection("rooms");
    }

    public DBCollection photos() {
        return mainMongo.getCollection("photos");
    }

    public DBCollection red_packets() {
        return activeMongo.getCollection("red_packets");
    }


    public static final boolean isTest = AppProperties.get("api.domain").contains("test-");

    //TODO 临时测试user info token 问题
    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        try {
            return super.handleRequest(request, response);
        } catch (Throwable throwable) {
            logs.error(" handleRequest Error. ", throwable.getMessage());
            logs.error(" handleRequest Error. ", throwable.getStackTrace());
            logs.error(" handleRequest Error. ", throwable);
            if (throwable instanceof Exception) {
                throw (Exception) throwable;
            } else {
                throw new Exception(throwable);
            }
        }
    }

    /**
     * 上麦日志
     *
     * @param roomId
     * @param data
     */
    public void logMic(Integer roomId, Object data) {
        Map obj = new HashMap();
        obj.put("room", roomId);
        obj.put("data", data);
        obj.put("session", Web.getSession());
        obj.put("timestamp", System.currentTimeMillis());
        obj.put("createdAt", new Date());
        logMongo.getCollection("mic_log").save(new BasicDBObject(obj));
    }

    /**
     * 保存中奖日志
     *
     * @param lotteryId   ID
     * @param active_name 活动名称
     * @param userId      用户ID
     * @param star_id     主播或者房间ID
     * @param nickName    用户nickname
     * @param award_name  奖品名称
     * @param award_count 奖品数量
     * @param award_coin  奖励柠檬
     * @param cost_coin   花费柠檬
     * @param platform    平台
     * @return
     */
    public Boolean saveLotteryLog(String lotteryId, String active_name, Integer userId, Integer star_id, String nickName,
                                  String award_name, int award_count, long award_coin, int cost_coin, Integer platform) {

        Long time = System.currentTimeMillis();
        Map<Object, Object> obj = new HashMap<Object, Object>();
        obj.put(_id, lotteryId);
        obj.put("user_id", userId);
        obj.put("star_id", star_id);
        obj.put("nick_name", nickName);
        obj.put("award_name", award_name);
        obj.put("award_count", award_count);
        obj.put("timestamp", time);
        obj.put("active_name", active_name);
        obj.put("cost_coin", cost_coin);
        obj.put("award_coin", award_coin);
        obj.put("platform", platform);
        try {
            logMongo.getCollection("lottery_logs").insert(new BasicDBObject(obj), writeConcern);
            return Boolean.TRUE;

        } catch (Exception e) {
            logger.error("saveLotteryLog", e);
            return Boolean.FALSE;
        }
    }


    public void saveAwardLog(Integer userId, String name, Integer award) {
        Long time = System.currentTimeMillis();
        String _id = userId + "_" + award+"_" + time;
        BasicDBObject log = new BasicDBObject("_id", _id);
        log.append("user_id", userId)
                .append("name", name)
                .append("award", award)
                .append("type", "family_award")
                .append("timestamp", time);
        logMongo.getCollection("user_award_logs").save(log);
    }

    public Boolean saveLotteryLog(String lotteryId, String active_name, Integer userId, Integer star_id, String nickName,
                                  String award_name, int award_count, long award_coin, int cost_coin) {

        return saveLotteryLog(lotteryId, active_name, userId, star_id, nickName, award_name,
                award_count, award_coin, cost_coin, AppPropType.android.ordinal());
    }


    /**
     * 扣费流程
     * @param userId
     * @param costCount
     * @param doCost
     * @return
     */
    private Boolean costProcess(Integer userId, Integer costCount, DoCost doCost, String costField, String logCollections, BasicDBObject costLog) {
        DBCollection users = users();
        if (costLog.get(timestamp) == null) {
            costLog.append(timestamp, System.currentTimeMillis());
            logger.error("Exception costCoin log timestamp is NULL");
        }
        BasicDBObject update = $$($inc, $$(costField, -costCount)).append($push, $$(logCollections, costLog));
        if (0 == users.update($$(_id, userId).append(costField, $$($gte, costCount)), update, false, false, writeConcern).getN()) {
            return false;
        }

        boolean hasError;
        try {
            hasError = !doCost.costSuccess();
        } catch (Exception e) {
            logs.error("cost Error", e);
            hasError = true;
        }

        if (hasError) { //花钱失败了
            users.update(new BasicDBObject(_id, userId),
                    new BasicDBObject($inc, $$(costField, costCount))
                            .append($pull, $$(logCollections, $$(_id, costLog.get(_id)))),
                    false, false, writeConcern);
            return false;
        }
        return true;
    }

    /**
     * 消费
     */
    public boolean costCoin(Integer userId, Integer costCount, DoCost doCost, String costField, String logCollections) {
        BasicDBObject costLog;
        if (costCount < 0 || (costLog = doCost.costLog()) == null) {
            return false;
        }
        //TODO nano 也无法支持超高并发
        String costLogId = userId + "_" + System.nanoTime(); // use nano To query NO need log._id :[$ne : logId ]
        costLog.put(_id, costLogId);
        //扣费流程
        if (costProcess(userId, costCount, doCost, costField, logCollections, costLog)) {
            // 默认认为扣费成功。。 timestamp 用来做检查
            logMongo.getCollection(logCollections).save(costLog, writeConcern);
            users().update(new BasicDBObject(_id, userId), new BasicDBObject($pull, new BasicDBObject(logCollections, new BasicDBObject(_id, costLogId))));
            return true;
        }

        return false;
    }

    /**
     * 消费金币
     * @param userId
     * @param coin
     * @param doCost
     * @return
     */
    public boolean costCoin(Integer userId, Integer coin, DoCost doCost) {
        return costCoin(userId, coin, doCost, finance$coin_count, room_cost);
    }

    /**
     * 消费钻石
     * @param userId
     * @param diamond
     * @param doCost
     * @return
     */
    public boolean costDiamond(Integer userId, Integer diamond, DoCost doCost) {
        return costCoin(userId, diamond, doCost, finance$diamond_count, diamond_cost);
    }

    /**
     * 消费星尘
     * @param userId
     * @param diamond
     * @param doCost
     * @return
     */
    /*public boolean costPoints(Integer userId, Integer diamond, DoCost doCost) {
        return costCoin(userId, diamond, doCost, finance$points_count, dust_cost_logs);
    }*/

    /**
     * 消费现金
     * @param userId
     * @param cash
     * @param doCost
     * @return
     */
    public boolean costCash(Integer userId, Integer cash, DoCost doCost) {
        return costCoin(userId, cash, doCost, finance$cash_count, cash_cost_logs);
    }

    static final String finance_log = "finance_log";
    static final String exp_log = "exp_log";
    static final String finance_log_id = finance_log + "." + _id;
    static final String exp_log_id = exp_log + "." + _id;
    public static final String room_cost = "coin_cost_logs";
    public static final String diamond_cost = "diamond_cost_logs";
    public static final String diamond_add = "diamond_add_logs";
    public static final String dust_cost_logs = "dust_cost_logs";
    public static final String cash_cost_logs = "cash_cost_logs";
    public static long DAY_MILLON = 24 * 3600 * 1000L;

    public boolean addDiamond(Integer userId, Long diamond, BasicDBObject logWithId) {
        String log_id = (String) logWithId.get(_id);
        if (diamond < 0 || log_id == null) {
            return false;
        }
        DBCollection users = users();

        DBCollection logColl = logMongo.getCollection(diamond_add);
        if (logColl.count(new BasicDBObject(_id, log_id)) == 0 &&
                users.update(new BasicDBObject(_id, userId).append(diamond_add + "." + _id, new BasicDBObject($ne, log_id)),
                        new BasicDBObject($inc, new BasicDBObject(finance$diamond_count, diamond))
                                .append($push, new BasicDBObject(diamond_add, logWithId.append(timestamp, System.currentTimeMillis()))),
                        false, false, writeConcern
                ).getN() == 1) {

            logColl.save(logWithId, writeConcern);
            users.update(new BasicDBObject(_id, userId),
                    new BasicDBObject($pull, new BasicDBObject(diamond_add, new BasicDBObject(_id, log_id))),
                    false, false, writeConcern);

            return true;
        }
        return false;
    }

    public boolean addCoin(Integer userId, Long coin, BasicDBObject logWithId) {
        String log_id = (String) logWithId.get(_id);
        if (coin < 0 || log_id == null) {
            return false;
        }
        Long returnCoin = 0l;
        logWithId.put("returnCoin", returnCoin);
        coin = coin + returnCoin;
        DBCollection users = users();

        if (logWithId.get("to_id") == null) {
            logWithId.put("to_id", userId);
        }
        DBObject my_user = users.findOne(new BasicDBObject(_id, userId), new BasicDBObject("qd", 1));
        if (my_user != null) {
            if (null != my_user.get("qd")) {
                String qd = my_user.get("qd").toString();
                logWithId.append("qd", qd);
            }
        }
        DBCollection logColl = adminMongo.getCollection(finance_log);
        if (logColl.count(new BasicDBObject(_id, log_id)) == 0 &&
                users.update(new BasicDBObject(_id, userId).append(finance_log_id, new BasicDBObject($ne, log_id)),
                        new BasicDBObject($inc, new BasicDBObject(finance$coin_count, coin))
                                .append($push, new BasicDBObject(finance_log, logWithId.append(timestamp, System.currentTimeMillis()))),
                        false, false, writeConcern
                ).getN() == 1) {
            logs.debug("添加日志成功");
            logColl.save(logWithId, writeConcern);
            users.update(new BasicDBObject(_id, userId),
                    new BasicDBObject($pull, new BasicDBObject(finance_log, new BasicDBObject(_id, log_id))),
                    false, false, writeConcern);

            return true;
        }
        return false;
    }

    /**
     * 增加经验
     * @param userId
     * @param exp
     * @param logWithId
     * @return
     */
    public boolean addExp(Integer userId, Long exp, BasicDBObject logWithId) {
        String log_id = (String) logWithId.get(_id);
        if (exp < 0 || log_id == null) {
            return false;
        }
        DBCollection users = users();
        DBCollection logColl = adminMongo.getCollection(exp_log);
        if (logColl.count(new BasicDBObject(_id, log_id)) == 0 &&
                users.update(new BasicDBObject(_id, userId).append(exp_log_id, new BasicDBObject($ne, log_id)),
                        new BasicDBObject($inc, new BasicDBObject(User.Exp, exp))
                                .append($push, new BasicDBObject(exp_log, logWithId.append(timestamp, System.currentTimeMillis()))),
                        false, false, writeConcern
                ).getN() == 1) {
            logs.debug("添加日志成功");
            logColl.save(logWithId, writeConcern);
            users.update(new BasicDBObject(_id, userId),
                    new BasicDBObject($pull, new BasicDBObject(exp_log, new BasicDBObject(_id, log_id))),
                    false, false, writeConcern);
            return true;
        }
        return false;
    }

    /**
     * 特殊字符过滤清除
     *
     * @param str
     * @return
     */
    public static String specialCharFilter(String str) {
        // 清除掉所有特殊字符
        String regEx = "[`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？_]";
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(str);
        return m.replaceAll("").trim();
    }

    public static void subscribe(final byte[] channel, final MessageListener listener) {
        MsgExecutor.execute(new Runnable() {
            @Override
            public void run() {
                chatRedis.execute(new RedisCallback<Object>() {
                    @Override
                    public Object doInRedis(RedisConnection redisConnection) throws DataAccessException {
                        redisConnection.subscribe(listener, channel);
                        redisConnection.close();
                        return null;
                    }
                });
            }
        });
    }

}


