package com.wawa.common.util;


import com.wawa.base.ext.RestExtension;
import com.wawa.model.CatchPartnerType;
import com.wawa.model.User;
import groovy.transform.CompileStatic;

/**
 * 约定的key 值
 */
@CompileStatic
public abstract class KeyUtils {
    public static byte[] serializer(String string) {
        return RestExtension.asBytes(string);
    }

    public static String decode(byte[] data) {
        return RestExtension.asString(data);
    }

    /**
     * 用作redis的标记值
     */
    public static final String MARK_VAL = "";

    private static final String SPLIT_CHAR = ":";


    public static String accessToken(String token) {
        return "token:" + token;
    }

    public static String vistor_counts() {
        return "web:ttxiuvistor:counts";
    }

    public static String all_cars() {
        return "all:aiwanzhibo:cars";
    }

    public static String all_props(Object type) {
        return "all:ttxiuapp:props:" + type;
    }

    public static String all_medals() {
        return "all:aiwanzhibo:medals";
    }

    public static String all_gifts() {
        return "all:aiwanzhibo:gifts";
    }

    public static String gifts(Object type) {
        return "all:aiwanzhibo:gifts:" + type;
    }

    public static String local_gifts_flag() {
        return "local:aiwanzhibo:gifts:flag";
    }


    public static String local_cars_flag() {
        return "local:aiwanzhibo:cars:flag";
    }

    public static String user_online_list() {
        return "im:online";
    }

    //每月充值用户领取奖励次数上限
    public static String charge_award_monthly(Object month) {
        return "charge:award:monthly:" + month;
    }

    //每日充值用户领取奖励次数上限
    public static String charge_award_daily(Object date) {
        return "charge:award:daily:" + date;
    }

    /**
     * 手机客户端相关
     */
    public static class APP {
        public static final String APP = "app:";

        public static String index_data() {
            return APP + "index:data";
        }

        public static String index_data_expire() {
            return APP + "index:data:expire";
        }

        public static String city_data() {
            return APP + "city:data";
        }

        public static String load_data() {
            return APP + "load:data";
        }
    }

    /**
     * H5相关
     */
    public static class H5 {
        public static final String APP = "h5:";

        public static String index_data() {
            return APP + "index:data";
        }

        public static String index_data_expire() {
            return APP + "index:data:expire";
        }

        public static String load_data() {
            return APP + "load:data";
        }
    }

    /**
     * 家族
     */
    public static class FAMILIES {
        public static final String FAMILIES = "family:";

        public static String champion() {
            return FAMILIES + "support:champion";
        }

        public static String total_rank(int weekofyear) {
            return FAMILIES + weekofyear + ":rank";
        }

        public static String following(Object uid) {
            return FAMILIES + uid + SPLIT_CHAR + User.following;
        }

        public static String followers(Object uid) {
            return FAMILIES + uid + SPLIT_CHAR + User.followers;
        }

        public static String ranks() {
            return FAMILIES + "prestige:rank:list";
        }
        public static String donate_prestige_limit(Object fid) {
            return FAMILIES + "donate:prestige:limit:"+fid;
        }

        /**
         * 被攻击中家族
         * @param fid
         * @return
         */
        public static String ack_family(Object fid) {
            return FAMILIES + "ack:family:"+fid;
        }

        /**
         * 家族成员贡献榜(家族争霸)
         * @param fid
         * @return
         */
        public static String family_donate_ranks(Object fid) {
            return FAMILIES + "donate:rank:" + fid;
        }

        /**
         * 家族成员贡献日榜
         * @param fid
         * @return
         */
        public static String family_donate_day_ranks(Object fid) {
            return FAMILIES + "donate:rank:day:" + fid;
        }

        /**
         * 家族成员贡献周榜
         * @param fid
         * @return
         */
        public static String family_donate_week_ranks(Object fid) {
            return FAMILIES + "donate:rank:week:" + fid;
        }

        /**
         * 家族奖励红包
         */
        public static String family_reward_redpack(Object fid) {
            return FAMILIES + "reward:redpack:" + fid;
        }

        /**
         * 家族奖励红包时间戳
         */
        public static String family_redpack_timestamp(Object fid) {
            return FAMILIES + "reward:redpack:" + fid + ":timestamp";
        }

        /**
         * 家族威望信息流
         */
        public static String family_prestige_list(Object fid) {
            return FAMILIES + "prestige:list:" + fid;
        }
    }

    /**
     * 任务领取用户
     */
    public static class MISSION {
        public static final String MISSION = "mission:";
        public static final String DAILY_MISSION = "daily_mission:";
        public static final String SIX_FREE_SUN = "six_free_sun:";

        public static String firstCharge(Object mission_id, Object uid) {
            return MISSION + mission_id + SPLIT_CHAR + uid + SPLIT_CHAR + "firstcharge";
        }

        public static String firstCharge(Object mission_id) {
            return MISSION + mission_id + SPLIT_CHAR + "first";
        }

        public static String signDaily(Object mission_id, Object uid) {
            return MISSION + mission_id + SPLIT_CHAR + uid;
        }

        public static String mission_users(String mission_id) {
            return MISSION + mission_id + "users";
        }

        public static String mission_list(Object uid) {
            return MISSION + uid;
        }

        public static  String daily_mission_list(String date,Object uid){
            return DAILY_MISSION + date + SPLIT_CHAR +  uid;
        }

        public static  String daily_mission(String date,String mission_id,Object uid){
            return   DAILY_MISSION + uid + SPLIT_CHAR + date + SPLIT_CHAR + mission_id;
        }

        public static String mission_daily(String date, String mission_id, Object uid) {
            return MISSION + "daily" + SPLIT_CHAR + date + SPLIT_CHAR +uid + SPLIT_CHAR + mission_id;
        }

    }

    public static class USER {

        public static final String USER = "user:";

        public static String hash(Object uid) {
            return USER + uid;
        }

        /**
         * 新注册用户标识
         * @param uid
         * @return
         */
        public static String fresh(Object uid) {
            return USER + uid + ":fresh";
        }

        /**
         * 首次抓中标识
         * @param uid
         * @return
         */
        public static String first(Object uid) {
            return USER + uid + ":first";
        }

        /**
         * 前三抓标识
         * @param uid
         * @return
         */
        public static String first_doll(Object uid) {
            return USER + uid + ":first:doll";
        }

        /**
         * 抓必中标识
         * @param uid
         * @return
         */
        public static String unlimit(Object uid) {
            return USER + uid + ":unlimit";
        }

        public static String unlimit_notify(Object uid) {
            return USER + uid + ":unlimit:notify";
        }

        /**
         * 首次登录标识
         * @param uid
         * @return
         */
        public static String login(Object uid) {
            return USER + uid + ":login";
        }

        public static String vip(Object uid) {
            return USER + uid + ":vip";
        }

        public static String car(Object uid) {
            return USER + uid + ":car";
        }


        public static String authCode(Object uid) {
            return USER + uid + ":auth";
        }

        public static String token(Object uid) {
            return USER + uid + SPLIT_CHAR + User.access_token;
        }

        public static String following(Object uid) {
            return USER + uid + SPLIT_CHAR + User.following;
        }


        public static String followers(Object uid) {
            return USER + uid + SPLIT_CHAR + User.followers;
        }

        /**
         * 主播历史所有关注用户
         *
         * @param uid
         * @return
         */
        public static String historyFollowers(Object uid) {
            return USER + uid + SPLIT_CHAR + "history" + SPLIT_CHAR + User.followers;
        }

        //用户设备有效期
        public static String deviceCheck(Object uid) {
            return USER + uid + ":device:check:legal";
        }

        //用户虚拟机黑名单
        public static String deviceCheckillegalList() {
            return USER + ":device:check:illegal:list";
        }

        public static String blackClient(String uid) {
            return "uidblack:" + uid;
        }

        /**
         * 好友列表
         *
         * @param uid
         * @return
         */
        public static String friends(Object uid) {
            return USER + uid + SPLIT_CHAR + User.friends;
        }

        /**
         * 是否新好友申请状态
         */
        public static String friends_apply_flag(Object uid) {
            return USER + uid + SPLIT_CHAR + User.friends + SPLIT_CHAR + "apply_flag";
        }
        /**
         * 好友申请限制
         */
        public static String friendApplyLimit(Object uid) {
            return USER + "friend:apply:" + uid;
        }

        public static String onlyToken2id(String token) {
            return "ot2id:" + token;
        }

        public static String cashCodeLimit(Object uid) {
            return USER + "cash:code:" + uid;
        }

        public static String horn(Object uid) {
            return USER + uid + ":horn";
        }

        public static String prestigeCount(Object uid) {
            return USER + uid + ":prestige";
        }

        public static String room_operation(Object uid) {
            return USER + uid + ":prestige";
        }

    }

    /**
     * 好友消息
     */
    public static class IM {
        public static String user(Object userId) {
            return "IM:USER:CHANNEL:" + userId;
        }
    }

    public static class CHANNEL {
        public static String room(Object roomId) {
            return "room:" + roomId;
        }

        public static String user(Object userId) {
            return "USERchannel:" + userId;
        }

        public static String all() {
            return "ALLchannel";
        }

        public static String operate() {
            return "play:channel:user:action";
        }

        public static String operate_callback() {
            return "";
        }
    }

    /**
     * 幸运礼物
     */
    public static class LUCK {
        public static String powerList() {
            return "luck:powerlist";
        }

        public static String prizePool() {
            return "luck:prizepool";
        }
    }

    public static class PUBLIC {
        public static String taglist(Object tagId) {
            return "taglist:" + tagId;
        }

        public static String appleUsers() {
            return "apple:user";
        }
    }

    /**
     * 房间
     */
    public static class ROOM {
        public static final String ROOM = "room:";

        public static String micUser(Object roomId, String mic) {
            return ROOM + roomId + SPLIT_CHAR + mic +":mic:user";
        }

        public static String shutup(Object roomId, Object uid) {
            return ROOM + roomId + ":shutup:" + uid;
        }

        @Deprecated
        public static String shutupSet(Object roomId) {
            return ROOM + roomId + ":shutup:set";
        }

        @Deprecated
        public static String kickSet(Object roomId) {
            return ROOM + roomId + ":kick:set";
        }

        public static String users(Object roomId) {
            return ROOM + roomId + ":users";
        }
        public static String robots(Object roomId) {
            return ROOM + roomId + ":robots";
        }

        //用户进入直播间时间
        public static String userEntersTime(Object roomId) {
            return ROOM + roomId + ":users:enter:timestamp";
        }

        public static String kick(Object roomId, Object uid) {
            return ROOM + roomId + ":kick:" + uid;
        }


        public static String liveFlag(Object roomId) {
            return ROOM + roomId + ":live";
        }

        /**
         * 用户上麦心跳
         * @param roomId
         * @param userId
         * @return
         */
        public static String liveFlag(Object roomId, Integer userId) {
            return ROOM + "mic:live:"+roomId+SPLIT_CHAR+userId;
        }

        public static String clientFlagHash(Object roomId) {
            return ROOM + roomId + ":client";
        }

        public static String admin(Object roomId) {
            return ROOM + roomId + ":admin:string";
        }

        public static String familysRank(Object roomId) {
            return ROOM + roomId + ":familys:rank:string";
        }

        public static String photoCount(Object roomId) {
            return ROOM + roomId + ":photo:count:string";
        }

        public static String room_repair(Object roomId) {
            return ROOM + roomId + ":repair:apply";
        }

    }
    public static class LIVE {

        public static final String LIVE = "live:";

        public static String all(Object roomId) {
            return LIVE + roomId + ":*";
        }

        public static String userCostZset(Object roomId) {
            return LIVE + roomId + ":user:costzset";
        }

        /**
         * 直播间用户申请连麦列表
         *
         * @param roomId
         * @return
         */
        public static String userMicList(Object roomId) {
            return LIVE + roomId + ":user:mic:list";
        }

        public static String userMicInvite(Object roomId, Object userId) {
            return LIVE + roomId + ":user:mic:invite:"+userId;
        }
    }

    /**
     * Message 消息
     */
    public static class MESSAGE {
        public static final String MESSAGE = "msg:";

        public static String hash(Object messageId) {
            return MESSAGE + messageId;
        }
    }


    /**
     * black_blist
     */
    public static class BLACKBLIST {
        public static final String BLACKBLIST = "blackblist:";

        public static String blacklists(Integer type) {
            return BLACKBLIST + type + ":blacklists";
        }
    }

    public static class Actives {
        public static final String ACTIVES = "Actives:";
        /**
         * 活动信息缓存
         *
         * @return
         */
        public static String info(Object activityId) {
            return ACTIVES + "info:" + activityId;
        }

        /**
         * 活动送礼间隔时间内ip限制
         *
         * @param ip
         * @return
         */
        public static String LimitIntervalPerIp(Object activityId, String ip) {
            return ACTIVES + "interval:ip:limit:" + activityId + SPLIT_CHAR + ip;
        }

        /**
         * 活动送礼ip限制
         *
         * @param ip
         * @return
         */
        public static String LimitTotalPerIp(Object activityId, String ip) {
            return ACTIVES + "total:ip:limit:" + activityId + SPLIT_CHAR + ip;
        }

        /**
         * 未通过验证的挂机ip
         *
         * @param ip
         * @return
         */
        public static String forbiddenIp(Object ip) {
            return ACTIVES + "forbidden:ip:" + ip;
        }

        /**
         * 未通过验证的挂机用户
         *
         * @param userId
         * @return
         */
        public static String forbiddenUser(Object userId) {
            return ACTIVES + "forbidden:user:" + userId;
        }

        /**
         * 用户礼物倒计时冷却时间
         *
         * @param activityId
         * @param userId
         * @return
         */
        public static String cooldownOfUser(Object activityId, Object userId) {
            return ACTIVES + activityId + SPLIT_CHAR + "cooldown:user" + SPLIT_CHAR + userId;
        }

        /**
         * 同一ip下礼物倒计时冷却时间
         *
         * @param activityId
         * @param ip
         * @return
         */
        public static String cooldownOfIp(Object activityId, Object ip) {
            return ACTIVES + activityId + SPLIT_CHAR + "cooldown:ip" + SPLIT_CHAR + ip;
        }

        /**
         * 用户一定时间类累计送达么么哒数量
         *
         * @param activityId
         * @param userId
         * @return
         */
        public static String LimitTotalPerUser(Object activityId, Object userId) {
            return ACTIVES + "limit:total:user:" + activityId + SPLIT_CHAR + userId;
        }
    }

    public static class Card {
        public static final String SHARE = "card:";

        public static String userRefreshCdPerDay(Object userId) {
            return SHARE + "total:user:refesh:cd" + SPLIT_CHAR + userId;
        }

    }

    /**
     * 用户昵称 -> id 转换
     */
    public static final String NAME2ID_NS = "_name2id_:";

    /**
     * 抓娃娃
     */
    public static class CATCHU {
        public static final String CATCHU = "catchu:";

        public static String room_map(CatchPartnerType catchPartnerType) {
            return CATCHU +"room:map:" + catchPartnerType.ordinal();
        }
        //todo 等快来下线改成room_map
        public static String room_map_aochu(CatchPartnerType catchPartnerType) {
            return CATCHU +"room:map:aochu:" + catchPartnerType.ordinal();
        }

        public static String room_player(Object rid) {
            return CATCHU +"room:player:" + rid;
        }

        public static String room_status_hash(String channel_id) {
            return CATCHU +"room:map:" + channel_id;
        }
        /**
         * 每个房间当前游戏信息的hashkey
         */
        public static String room_player_hash(Object rid) {
            return CATCHU +"room:hash:" + rid;
        }

        public static String player_info_hash() {
            return CATCHU +"room:player:hash";
        }

        public static String room_queue(Object roomId) {
            return CATCHU +"room:queue:" + roomId;
        }

        public static String room_queue_candidate(Object roomId) {
            return CATCHU +"room:queue:candidate" + roomId;
        }

        public static String is_queue(Object userId) {
            return CATCHU +"is:queue:" + userId;
        }
    }

}
