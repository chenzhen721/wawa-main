package com.wawa.web.api

import com.mongodb.BasicDBObject
import com.mongodb.DBCollection
import com.wawa.api.Web
import com.wawa.base.anno.RestWithSession
import com.wawa.common.doc.Result
import com.wawa.common.util.KeyUtils
import com.wawa.common.util.MsgExecutor
import com.wawa.model.HomeNotifyType
import com.wawa.model.Mission
import com.wawa.model.UserAwardType
import com.wawa.base.BaseController
import org.apache.commons.lang3.time.DateUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest
import java.util.concurrent.TimeUnit

import static com.wawa.common.util.WebUtils.$$
import static com.wawa.common.doc.MongoKey.*

/**
 * 任务相关
 */
@RestWithSession
class MissionController extends BaseController {

    private Logger logger = LoggerFactory.getLogger(MissionController.class)
    public static final List<Integer> FIRST_PAY_AWARD_ITEM_ID = [12881] //享受首冲特权的充值商品

    DBCollection missions() { adminMongo.getCollection("missions") }
    DBCollection shop() { adminMongo.getCollection("shop") }

    DBCollection award_notify_logs() { logMongo.getCollection("award_notify_logs")}
    DBCollection sign_logs() { logMongo.getCollection('sign_logs')}
    DBCollection invitor_logs() { logMongo.getCollection('invitor_logs')}
    DBCollection apply_post_logs() {
        return logMongo.getCollection('apply_post_logs')
    }

    public static final int award_invite_count = isTest ? 2 : 20
    public static final int award_follow_weixin = 20
    public static final int doll_unlimit_min_cost = isTest ? 100 : 500

    /**
     * 充值相关任务
     */
    def charge_mission(Integer userId, Map item, Map financeLog) {
        //首冲奖励
        firstCharge(userId, item)
        //签到奖励
        sign_award_notify(userId, item, financeLog['_id'] as String)
        //付费邮寄
        pay_postage(userId, item, financeLog['ext'] as Map, financeLog['_id'] as String)
        //抓必中,特权
        //doll_unlimit(userId, item)
    }

    /**
     * 首冲奖励
     */
    def firstCharge(Integer userId, Map item) {
        if (item['group'] != 'diamond' || item['award_type'] != 1) {
            return
        }
        //award_type = 1代表有首冲奖励
        def key = KeyUtils.MISSION.mission_users(Mission.首充100.id)
        if (mainRedis.opsForHash().putIfAbsent(key, '' + userId, '' + System.currentTimeMillis())) {
            //发给用户20个邀请名额 wow
            if (1 != users().update($$(_id: userId), $$($inc: ['mission.invite': award_invite_count, 'mission.invite_total': award_invite_count]), false, false, writeConcern).getN()) {
                logger.error('mission invite add error:' + userId)
            }
        }
    }

    /**
     * 签到奖励提醒
     */
    def sign_award_notify(Integer user_id, Map item, final String orderId) {
        //放入签到队列
        if (item == null || item['after_award_diamond'] == null || item['after_award_days'] == null ||
                (item['after_award_days'] as Integer) <= 0 || (item['after_award_diamond'] as Integer) <= 0) {
            return
        }
        final def diamond = item['after_award_diamond'] as Integer
        final def days = item['after_award_days'] as Integer
        final DBCollection sign_log = sign_logs()
        def s = new Date().clearTime().getTime()
        if (item['group'] == 'card') { //只能续时不能叠加
            def logs = sign_log.find($$(user_id: user_id, item_id: item['_id'], is_award: false)).sort($$(timestamp: -1)).limit(1).toArray()
            if (logs.size() > 0) {
                def log = logs[0]
                s = (log['timestamp'] as Long) + DAY_MILLON
            }
        }
        final long start = s
        def obj = $$(user_id: user_id, is_used: false, item_id: item['_id'], group: item['group'], is_pre_used: false, is_award: false, award: diamond?: 0, days: days, order_id: orderId)
        //生成对应天数的记录sign_logs, 奖励当天算起
        MsgExecutor.execute(
            new Runnable() {
                @Override
                void run() {
                    def t = start
                    days.times { Integer day->
                        def time = t + day * DAY_MILLON
                        obj.putAll([_id: "${user_id}_${day}_${System.currentTimeMillis()}".toString(), day: day + 1, timestamp: time])
                        sign_log.save(obj)
                    }
                }
            }
        )
    }

    /**
     * 注册奖励提醒
     */
    def new_award_notify(Integer user_id, Integer diamond, Boolean is_award) {
        def _id = "${user_id}_${diamond}_${System.currentTimeMillis()}".toString()
        def obj = $$(_id: _id, user_id: user_id,
                award: [diamond: diamond],
                is_used: false,
                is_award: is_award,
                type: Mission.注册奖励.id,
                timestamp: System.currentTimeMillis())
        award_notify_logs().save(obj, writeConcern)
    }

    /**
     * 付费邮寄
     */
    def pay_postage(Integer user_id, Map item, Map ext, String orderId) {
        if (item == null || item['group'] != 'postage' || ext['ext'] == null) {
            return
        }
        def _id = ext['ext'] as String

        if (1 != apply_post_logs().update($$(_id: _id, user_id: user_id, is_pay_postage: Boolean.FALSE),
                $$($set: [is_pay_postage: Boolean.TRUE, finance_log_id: orderId]), false , false, writeConcern).getN()) {
            logger.info('=====>pay postage. order_id:' + orderId + ', post_id:' + _id + ', user_id:' + user_id)
        }
    }

    /**
     * 关注奖励提醒
     */
    def weixin_follow_notify(Integer user_id, Integer diamond, Boolean is_award) {
        def _id = "${user_id}_${diamond}_${System.currentTimeMillis()}".toString()
        def obj = $$(_id: _id, user_id: user_id,
                award: [diamond: diamond],
                is_used: false,
                is_award: is_award,
                type: Mission.关注奖励.id,
                timestamp: System.currentTimeMillis())
        award_notify_logs().save(obj, writeConcern)
        return obj
    }

    /**
     * 发奖
     * @param user_id
     * @return
     */
    def award_diamond_follow_weixin(Integer user_id) {
        def logs = award_notify_logs().findOne($$(user_id: user_id, type: Mission.关注奖励.id))
        if (logs == null) {
            logs = weixin_follow_notify(user_id, award_follow_weixin, false)
        }
        if (logs['is_award'] == true) {
            return
        }
        def diamond = award_follow_weixin
        if (logs != null) {
            diamond = logs['award']['diamond'] as Integer
        }
        if (1 == award_notify_logs().update($$(user_id: user_id, type: Mission.关注奖励.id, is_award: false), $$($set: [is_award: true]), false, false, writeConcern).getN()) {
            //奖励钻石
            def log = Web.awardLog(user_id, UserAwardType.关注钻石, [diamond: diamond])
            if (!addDiamond(user_id, Long.parseLong('' + diamond), log)) {
                logger.error('follow_weixin award diamond error:' + log)
            }
        }
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup User
     * @apiName sign_daily
     * @api {get} user/sign_daily/:token  用户每日签到
     * @apiDescription
     * 用户每日签到
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/user/sign_daily/031928b93e543825298982e06a00796c
     *
     * @apiSuccessExample {json} Success-Response:
     * {
     *     code: 1,
     *     data: {
     *          award: cur,
     *          next: next,
     *          combo: combo
     *     }
     * }
     *
     */
    def sign_daily(HttpServletRequest req) {
        def data = [:]
        def userId = Web.getCurrentUserId()
        def startOfDay = new Date().clearTime().getTime()
        def end = startOfDay + DAY_MILLON
        def logs = sign_logs().find($$(user_id: userId, $or: [[is_used: false], [is_award: false]], timestamp: [$gte: startOfDay, $lt: end])).toArray()
        def ids = []
        def count = 0
        def total = 0
        def detail = []
        def used_ids = []
        logs.each { BasicDBObject obj->
            def is_award = obj['is_award'] as Boolean
            def diamond = obj['award'] as Integer ?: 0
            if (!is_award) {
                count = count + diamond
                ids.add(obj['_id'])
            }
            def is_used = obj['is_used'] as Boolean
            if (!is_used) {
                total = total + diamond
                detail.add(diamond)
                used_ids.add(obj['_id'])
            }
        }
        if (count > 0) {
            if (1 <= sign_logs().update($$(_id: [$in: ids], is_award: false), $$($set: [is_award: true]), false, true, writeConcern).getN()) {
                def log = Web.awardLog(userId, UserAwardType.签到钻石, [diamond: count])
                log.put('sign_logs_ids', ids)
                //添加钻石成功
                if (!addDiamond(userId, Long.parseLong('' + count), log)) {
                    logger.error('add diamond by sign log error.' + userId + ':' + ids)
                }
            }
        }
        if (total > 0) {
            if (1 <= sign_logs().update($$(_id: [$in: used_ids], is_used: false), $$($set: [is_used: true]), false, true, writeConcern).getN()) {
                //加上提示
                data.put('award', total)
                //data.put('award_details', used_ids)
            }
        }
        //查询明天能获得签到奖励
        def next_start = startOfDay + DAY_MILLON
        def next_end = next_start + DAY_MILLON
        if (sign_logs().count($$(user_id: userId, is_pre_used: false, timestamp: [$gte: next_start, $lt: next_end])) > 0) {
            def next_logs = sign_logs().find($$(user_id: userId, timestamp: [$gte: next_start, $lt: next_end])).toArray()
            def next_ids = []
            def next_count = 0
            next_logs.each { BasicDBObject obj ->
                def diamond = obj['award'] as Integer ?: 0
                next_count = next_count + diamond
                def preUsed = obj['is_pre_used'] as Boolean
                if (!preUsed) {
                    next_ids.add(obj['_id'])
                }
            }
            if (next_count > 0) {
                if (1 <= sign_logs().update($$(_id: [$in: next_ids], is_pre_used: false), $$($set: [is_pre_used: true]), false, true, writeConcern).getN()) {
                    //加上返回结果
                    data.put('next', next_count)
                    //data.put('next_details', next_ids)
                }
            }
        }
        if (!data.isEmpty()) {
            data.put('type', HomeNotifyType.签到奖励.ordinal())
            return [code: 1, data: [award: data]]
        }
        return [code: 1]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup User
     * @apiName invite_notice
     * @api {get} user/invite_notice/:token  邀请到的用户提醒
     * @apiDescription
     * 邀请到的用户提醒
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/user/invite_notice/031928b93e543825298982e06a00796c
     *
     * @apiSuccessExample {json} Success-Response:
     * {
     *     code: 1,
     *     data: {
     *         _id: 记录ID,
     *         user_id: 邀请到的用户ID,
     *         diamond_count: 奖励钻石,
     *         points_count: 奖励积分,
     *         timestamp: 时间戳,
     *         session: { //邀请到的用户信息
     *             {
     *             	"level" : "2",
     *             	"pic" : "https://aiimg.sumeme.com/49/1/1500964546481.png",
     *             	"nick_name" : "萌新436907",
     *             	"_id" : "1203357",
     *             	"priv" : "3"
     *             }
     *         }
     *     }
     * }
     *
     */
    def invite_notice(HttpServletRequest req) {
        def invitor = Web.getCurrentUserId()
        if (invitor == null) {
            return Result.权限不足
        }
        def query = $$(invitor: invitor, beyond_toplimit: false, is_used: false, timestamp: [$gte: new Date().clearTime().getTime()])
        def invitorLogs = invitor_logs().find(query).toArray()
        def ids = invitorLogs*._id
        if (invitor_logs().update($$(_id: [$in: ids]), $$($set: [is_used: true]), false, true).getN() <= 0) {
            return [code: 1]
        }

        return [code: 1, data: [award: [type: HomeNotifyType.邀请奖励.ordinal(), list: invitorLogs]]]
    }

    /**
     * 充值奖励提示
     * @param req
     */
    def charge_notice(HttpServletRequest req) {
        def userId = Web.getCurrentUserId()
        def ids = []
        def details = []
        def diamond = 0
        award_notify_logs().find($$(type: Mission.注册奖励.id, user_id: userId, is_used: false,
                timestamp: [$lt: System.currentTimeMillis()])).toArray().each{ BasicDBObject obj->
            def award = obj['award']
            ids.add(obj['_id'])
            if (award != null && award['diamond'] != null) {
                diamond = diamond + (award['diamond'] as Integer ?: 0)
                details.add(award['diamond'] as Integer)
            }
        }
        if (1 <= award_notify_logs().update($$(_id: [$in: ids], is_used: false), $$($set: [is_used: true]), false, true, writeConcern).getN()) {
            return [code: 1, data: [award: [type: HomeNotifyType.注册奖励.ordinal(), diamond_count: diamond, details: details]]]
        }
        return [code: 1]
    }

    /**
     * 关注奖励提示
     * @param req
     */
    /*def follow_notice(HttpServletRequest req) {
        def userId = currentUserId
        def ids = []
        def obj = award_notify_logs().findOne($$(type: Mission.关注奖励.id, user_id: userId, is_used: false, is_award: true,
                timestamp: [$lt: System.currentTimeMillis()]))
        if (1 <= award_notify_logs().update($$(_id: [$in: ids], is_award: true, is_used: false), $$($set: [is_used: true]), false, true, writeConcern).getN()) {
            return [code: 1, data: [award: [type: HomeNotifyType.关注奖励.ordinal(), diamond_count: obj['diamond']]]]
        }
        return [code: 1]
    }*/

    /**
     * 无限抓
     */
    def doll_unlimit(Integer userId, Map item) {
        def cost = item['cost'] as Integer //充值金额, 单位分
        if (cost < doll_unlimit_min_cost) {
            return
        }
        //已有抓中记录
        if (!isFirst(userId)) {
            return
        }
        //写redis,，用户每次进首页查询是否开启，如果开启给提示
        //用户每次进房间判断是否开启特权，如果开启给提示，查询是否特权已结束
        String key = KeyUtils.USER.unlimit(userId)
        //初始化
        if (mainRedis.opsForHash().putIfAbsent(key, 'unlimit_flag', '0')) { //无限抓标识
            mainRedis.opsForHash().increment(key, 'unlimit_diamond_cost', (doll_unlimit_min_cost/10).toLong()) //开启无限抓需消耗的钻石
            mainRedis.opsForHash().put(key, 'unlimit_home_notify', '0') //无限抓首页提示
        }
    }

    /**
     * 是否未抓中过， true-未中过 false-中过
     * @param uid
     * @return
     */
    public boolean isFirst(Object uid) {
        if (mainRedis.hasKey(KeyUtils.USER.first(uid)) && '1' == mainRedis.opsForValue().get(KeyUtils.USER.first(uid))) {
            return Boolean.TRUE
        }
        return Boolean.FALSE
    }

    /**
     * 领取抓必中通知（暂停）
     * @param req
     */
    def unlimit_notify_notice(HttpServletRequest req) {
        def userId = Web.getCurrentUserId()
        String key = KeyUtils.USER.unlimit(userId)
        def user = users().findOne(userId)
        def diamond = user['finance']['diamond_count'] as Integer ?: 0
        // 未领取无限抓机会， 余额不足
        String notify_key = KeyUtils.USER.unlimit_notify(userId)
        if (isFirst(userId) && !mainRedis.hasKey(key) && diamond < 20 && !mainRedis.hasKey(notify_key)) {
            def time = DateUtils.addDays(new Date().clearTime(), 1).getTime()
            mainRedis.opsForValue().set(notify_key, '' + userId, time, TimeUnit.MILLISECONDS)
            return [code: 1, data:[award: [type: HomeNotifyType.抓必中领取通知.ordinal()]]]
        }
        return [code: 1]
    }

    /**
     * 抓必中开启通知(停止)
     * @param req
     */
    def unlimit_open_notice(HttpServletRequest req) {
        def userId = Web.getCurrentUserId()
        String key = KeyUtils.USER.unlimit(userId)
        if ('1' == mainRedis.opsForHash().get(key, 'unlimit_home_notify')) {
            mainRedis.opsForHash().delete(key, 'unlimit_home_notify')
            return [code: 1, data: [award: [type: HomeNotifyType.抓必中开启通知.ordinal()]]]
        }
        return [code: 1]
    }

}
