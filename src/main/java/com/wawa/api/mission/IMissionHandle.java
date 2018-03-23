package com.wawa.api.mission;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;
import com.wawa.base.StaticSpring;
import com.wawa.common.util.DateUtil;
import com.wawa.common.util.KeyUtils;
import com.wawa.model.MissionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.wawa.common.doc.MongoKey._id;
import static com.wawa.common.util.WebUtils.$$;


public interface IMissionHandle {

    // 处理任务
    public boolean handle(DBObject mission, DBObject user, DBObject to);

    // 领取任务奖励
    public boolean award(DBObject mission, int user);

    enum Mission implements IMissionHandle {
        新用户领取一次免费阳光 {
            @Override
            public boolean handle(DBObject mission, DBObject from, DBObject to) {
                return false;
            }

            @Override
            public boolean award(DBObject mission, int userId) {
                MongoTemplate mainMongo = (MongoTemplate) StaticSpring.get("mainMongo");
                DBObject from = mainMongo.getCollection("users").findOne($$("_id", userId));
                String missionId = String.valueOf(mission.get("_id"));
                if (!HandleHelp.isMissionComplete(missionId, from)) {
                    return false;
                }
                String missionLogId = userId + "_" + missionId;
                BasicDBObject missionLog = HandleHelp.buildMissionLog(missionLogId, userId, mission);
                int complete = 1;
                Map<String, Object> body = HandleHelp.buildPublishBody(from, null, mission, complete);
                return HandleHelp.addCoin(userId, mission, body, missionLog);
            }
        },
        免费红包奖励{
            @Override
            public boolean handle(DBObject mission, DBObject user, DBObject to) {
                int userId = (int)user.get("_id");
                return award(mission,userId);
            }

            @Override
            public boolean award(DBObject mission, int userId) {
                MongoTemplate mainMongo = (MongoTemplate) StaticSpring.get("mainMongo");
                DBObject from = mainMongo.getCollection("users").findOne($$("_id", userId));
                String missionId = "first_red_packet";
                if (!HandleHelp.isMissionComplete(missionId, from)) {
                    return false;
                }
                String missionLogId = userId + "_" + missionId;
                BasicDBObject missionLog = HandleHelp.buildMissionLog(missionLogId, userId, mission);
                int complete = 1;
                Map<String, Object> body = HandleHelp.buildPublishBody(from, null, mission, complete);
                return HandleHelp.addCoin(userId, mission, body, missionLog);
            }
        },
    }
}


class HandleHelp {

    static final int AWARDED = 99;

    static final Logger logger = LoggerFactory.getLogger(HandleHelp.class);

    /**
     * 任务完成并且领取
     *
     * @param from
     * @param to
     * @param mission
     * @return
     */
    public static boolean completeAndAward(DBObject from, DBObject to, DBObject mission) {
        int userId = (int) from.get("_id");
        int complete = 1;
        Map<String, Object> body = HandleHelp.buildPublishBody(from, to, mission, complete);
        String missionId = String.valueOf(mission.get("_id"));
        String missionLogId = userId + "_" + missionId;
        BasicDBObject missionLog = HandleHelp.buildMissionLog(missionLogId, userId, mission);
        return HandleHelp.addCoin(userId, mission, body, missionLog);
    }

    /**
     * 判断普通任务是否完成
     *
     * @param missionId
     * @param user
     * @return
     */
    public static boolean isMissionComplete(String missionId, DBObject user) {
        Boolean isDone = true;
        if (user != null && user.containsField("mission")) {
            Map mission = (Map) user.get("mission");
            // 要判断是否为空
            if (mission.containsKey(missionId)) {
                Long value = (Long) mission.get(missionId);
                if (value >= 1) {
                    isDone = false;
                }
            }
        }
        return isDone;
    }


    /**
     * 更新redis中用户对应的日常任务状态
     *
     * @param from
     * @param mission
     * @param to
     * @return
     */
    public static boolean dailyComplete(DBObject from, DBObject mission, DBObject to) {
        final StringRedisTemplate mainRedis = (StringRedisTemplate) StaticSpring.get("mainRedis");
        Long timestamp = new Date().getTime();
        String date = DateUtil.getFormatDate("yyyyMMdd", timestamp);
        int userId = (int) from.get("_id");
        String missionId = String.valueOf(mission.get("_id"));
        String redisKey = KeyUtils.MISSION.daily_mission(date, missionId, userId);
        Map missionNode = mainRedis.opsForHash().entries(redisKey);
        int complete = Integer.valueOf(missionNode.get("complete").toString());
        int total = Integer.valueOf(missionNode.get("total").toString());
        if (complete++ < total) {
            Map<String, Object> body = buildPublishBody(from, to, mission, complete);
            //MsgPublish.publishMissionCompleteEvent(body, userId);
            mainRedis.opsForHash().put(redisKey, "complete", String.valueOf(complete));
        }
        return true;
    }

    /**
     * 处理日常任务的领奖逻辑
     * 领过奖的任务赋值为 awarded
     *
     * @param mission
     * @param userId
     * @return
     */
    public static boolean dailyAward(DBObject mission, int userId) {
        boolean success = false;
        final StringRedisTemplate mainRedis = (StringRedisTemplate) StaticSpring.get("mainRedis");
        Long timestamp = new Date().getTime();
        String date = DateUtil.getFormatDate("yyyyMMdd", timestamp);
        String missionId = String.valueOf(mission.get("_id"));
        String redisKey = KeyUtils.MISSION.daily_mission(date, missionId, userId);
        Map missionNode = mainRedis.opsForHash().entries(redisKey);
        int complete = Integer.valueOf(missionNode.get("complete").toString());
        int total = Integer.valueOf(missionNode.get("total").toString());
        if (complete == total) {
            String missionLogId = userId + "_" + date + "_" + missionId;
            BasicDBObject missionLog = buildMissionLog(missionLogId, userId, mission);
            success = addCoin(userId, mission, new HashMap<String, Object>(), missionLog);
            if (success) {
                mainRedis.opsForHash().put(redisKey, "complete", String.valueOf(AWARDED));
            }
        }
        return success;
    }

    /***
     * 构建推送信息
     * @param from
     * @param to
     * @param mission
     * @return
     */
    public static Map<String, Object> buildPublishBody(DBObject from, DBObject to, DBObject mission, int complete) {
        Map<String, Object> body = new HashMap<String, Object>();
        String missionId = String.valueOf(mission.get("_id"));
        int total = (int) mission.get("total");
        body.put("mission_id", missionId);
        body.put("total", total);
        body.put("from", from);
        body.put("to", to);
        body.put("complete", complete);
        return body;
    }

    /**
     * 构建任务对象
     *
     * @param userId
     * @param mission
     * @return
     */
    public static BasicDBObject buildMissionLog(String missionLogId, int userId, DBObject mission) {
        int coin = (int) mission.get("coin_count");
        String missionId = String.valueOf(mission.get("_id"));
        BasicDBObject missionLog = new BasicDBObject();
        missionLog.append("_id", missionLogId).append("user_id", userId).append("coin", coin).append("mission_id", missionId);
        return missionLog;
    }

    static final String mission_award_log = "mission_logs";
    static final String mission_award_log_id = mission_award_log + "." + _id;

    /**
     * 加币 加日志
     */
    public static boolean addCoin(int userId, DBObject mission, Map<String, Object> body, BasicDBObject missionLog) {
        MongoTemplate mainMongo = (MongoTemplate) StaticSpring.get("mainMongo");
        MongoTemplate logMongo = (MongoTemplate) StaticSpring.get("logMongo");
        WriteConcern writeConcern = (WriteConcern) StaticSpring.get("writeConcern");
        DBCollection missionLogs = logMongo.getCollection(mission_award_log);
        DBCollection users = mainMongo.getCollection("users");

        Long timestamp = new Date().getTime();
        int coin = (int) mission.get("coin_count");
        String missionName = String.valueOf(mission.get("_id"));

        String missionLogId = String.valueOf(missionLog.get("_id"));
        BasicDBObject user_query = $$("_id", userId)
                .append(mission_award_log_id, $$("$ne", missionLogId));

        BasicDBObject user_update = $$("$inc", $$("finance.coin_count", coin))
                .append("$push", $$(mission_award_log, missionLog.append("timestamp", timestamp)));

        int missionType = (int) mission.get("type");
        if (missionType == MissionType.新人专属任务.ordinal() || missionType == MissionType.其他任务.ordinal()) {
            user_update.append("$set", $$("mission." + missionName, timestamp));
        }

        if (missionLogs.count($$("_id", missionLogId)) == 0L
                && users.update(user_query, user_update).getN() == 1) {
            logger.debug("insert success ..");
            missionLogs.save(missionLog, writeConcern);
            users.update(user_query, $$("$pull", $$(mission_award_log, $$("_id", missionLogId))));
            body.put("complete", timestamp);
            //MsgPublish.publishMissionCompleteEvent(body, userId);
            return true;
        }
        logger.error("insert fail ..");
        return false;
    }

}