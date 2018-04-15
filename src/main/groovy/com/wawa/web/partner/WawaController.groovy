package com.wawa.web.partner

import com.mongodb.BasicDBObject
import com.mongodb.DBCollection
import com.wawa.api.DoCost
import com.wawa.api.Web
import com.wawa.api.notify.RoomMsgPublish
import com.wawa.api.play.WawaMachine
import com.wawa.api.play.dto.WWAssignDTO
import com.wawa.api.play.dto.WWRoomDTO
import com.wawa.base.BaseController
import com.wawa.base.anno.RestWithSession
import com.wawa.common.doc.MsgAction
import com.wawa.common.doc.Result
import com.wawa.common.util.DelayQueueRedis
import com.wawa.common.util.JSONUtil
import com.wawa.common.util.KeyUtils
import com.wawa.model.CatchMsgType
import com.wawa.model.CatchPostChannel
import com.wawa.model.CatchRecordType
import com.wawa.web.api.CatchuController
import com.wawa.web.api.UserController
import groovy.json.JsonSlurper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate

import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest
import java.util.concurrent.TimeUnit

import static com.wawa.common.util.WebUtils.$$

/**
 * 娃娃商-奇异果
 * Created by Administrator on 2017/11/9.
 */
@RestWithSession
class WawaController extends BaseController{

    static final Logger logger = LoggerFactory.getLogger(WawaController.class)

    public static final String ORDER_PREFIX = "play"
    //public static final String APP_ID = isTest ? "984069e5f8edd8ca4411e81863371f16" : "984069e5f8edd8ca4411e81863371f16"

    public static final JsonSlurper jsonSlurper = new JsonSlurper()
    public static final int DEFAULT_PLAY_TIME = 40
    public static final int PLAY_WAITING_TIME = 30

    @Resource
    CatchuController catchuController

    @Resource
    UserController userController

    DBCollection catch_users() {
        return catchMongo.getCollection('catch_user')
    }
    DBCollection catch_records() {
        return catchMongo.getCollection('catch_record')
    }
    DBCollection catch_toys() {
        return catchMongo.getCollection('catch_toy')
    }

    public static final int unlimit_min_cost = 20

    /**
     * @apiVersion 0.0.1
     * @apiGroup Catchu
     * @apiName room
     * @api {get} catchu/room/:access_token/:room_id  获取娃娃房详细信息
     * @apiDescription
     * 获取娃娃房详细信息
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiParam {Integer} [room_id]  房间ID
     *
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/catchu/room/15334c1921ad8da6224c98e202a652b4/1005

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "data": {
     *         "_id": 10000016,
     *         "fid": 1005,
     *         "name": "wawa1",
     *         "type": true, //true-正常游戏 false-备货中
     *         "pic": "http://www.baidu.com",
     *         "price": 300,
     *         "desc": "描述",
     *         "cd": 123,
     *         "status": 0, 0-空闲 1-游戏中 2-准备中
     *         "is_following": 是否关注 true,false（用户要登录）
     *         "timestamp": 1508232023761,
     *         "is_play": true,
     *         "paly_time": true,
     *         "log_id": 1233312,
     *         "record": {
     *             "ws_url": "",
     *             "record_id": ""
     *         },
     *         "player": {
     *             "_id": 123,
     *             "nick_name": "",
     *             "pic": ""
     *         },
     *         "toy_id": 123,
     *         "toy": {
     *             "_id": ,
     *             "name": "",
     *             "pic": "",
     *             "desc": "123"
     *         }
     *     },
     *     "exec": 3430,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def room(Map room) {
        def roomId = room['_id'] as Integer
        WWRoomDTO roomDTO = WawaMachine.room_detail(room['fid'] as String)
        if (room == null) {
            return Result.error
        }
        if (roomDTO != null) {
            def status = roomDTO.device_status
            room.putAll(room_player(roomId, status))
        }

        Integer price = null
        if (room['toy_id'] != null) {
            def toy = catch_toys().findOne(room['toy_id'] as Integer) as Map ?: null
            if (toy != null) {
                room.put('toy', [_id: toy['_id'], name: toy['name'], pic: toy['pic'], head_pic: toy['head_pic'], desc: toy['desc']])
                price = toy['price'] as Integer
            }
        }
        /*String key = KeyUtils.USER.unlimit(Web.currentUserId)
        if (price <= unlimit_min_cost && mainRedis.hasKey(key) && '1' == mainRedis.opsForHash().get(key, 'unlimit_flag')) {
            room.put('price', 0)
        } else {*/
            room.put('price', price)
        /*}*/
        return [code: 1, data: room]
    }

    Map room_player(Integer roomId, int status) {
        //如果是游戏中查询是否当前访问用户,
        def map = [status: status] as Map
        def playerInfo = Room.getPlayerInfo(mainRedis, roomId)
        if (playerInfo == null) {
            if (status != 0) {
                map['status'] = 2
            }
            return map
        }

        def play_until = playerInfo[Room.play_until] as Long
        def user_id = playerInfo[Room.user_id] as Integer
        def log_id = playerInfo[Room.log_id] as Long
        def record_id = playerInfo[Room.record_id] as String
        def ws_url = playerInfo[Room.ws_url] as String
        def finish = playerInfo[Room.finish] as Integer ?: 0
        def remain = ((play_until - System.currentTimeMillis()) / 1000).toInteger()
        map['status'] = 1
        map.put('player', users().findOne(user_id, $$(nick_name: 1, pic: 1)))
        map.put('is_play', true)
        map.put('record', [log_id: log_id ?: '', finish_waiting: finish, record_id: record_id ?: '', ws_url: ws_url ?: '', time: remain < 0 ? 0 : remain])
        return map
    }

    def start(Map room) {
        def roomId = room['_id'] as Integer
        def fid = room['fid'] as String
        def userId = Web.currentUserId
        //查询房间状态，如果不为空闲
        WWRoomDTO roomDTO = WawaMachine.room_detail(fid)
        if (roomDTO == null || roomDTO.getOnline_status() != 'on') {
            return Result.设备维护中
        }
        def status = roomDTO.device_status
        if (status == 2) {
            return Result.设备维护中
        }
        def t = System.currentTimeMillis()
        Integer userRoomId = playerRoomInfo(userId) as Integer
        if (userRoomId != null && userRoomId != roomId) {
            return Result.您正在其它房间游戏中
        }
        //todo 房间状态信息
        Map playerInfo = Room.getPlayerInfo(mainRedis, roomId)
        logger.info('playerinfo: ' + playerInfo)
        if (playerInfo != null) {
            if (userId == playerInfo[Room.user_id] && playerInfo[Room.record_id] != null) {
                if ('1' != playerInfo[Room.finish]) {
                    def play_until = playerInfo[Room.play_until] as Long //游戏截止时间
                    def remain = ((play_until - t) / 1000).toInteger()
                    return [code: 1, data: [record_id: playerInfo[Room.record_id], log_id: playerInfo[Room.log_id], ws_url: playerInfo[Room.ws_url], time: remain < 0 ? 0 : remain]]
                } else {
                    deleteIfNE(roomId, playerInfo[Room.record_id] as String, userId)
                }
            } else if (playerInfo[Room.record_id] == null) { //有异常
                mainRedis.delete(Room.hash(roomId))
                mainRedis.opsForHash().delete(Room.user_hash(), '' + userId)
            } else {
                return Result.其他人正在游戏中
            }
        }
        if (status == 1) {
            return Result.状态切换冷却中
        }
        if (status == 2) {
            return Result.设备维护中
        }

        /*String key = KeyUtils.USER.unlimit(userId)
        def unlimit = mainRedis.hasKey(key) && '1' == mainRedis.opsForHash().get(key, 'unlimit_flag')*/
        def unlimit = false

        //def catchUser = catch_users().findOne($$(_id: userId), $$(tid: 1))
        def record_id = "${ORDER_PREFIX}_${userId}_${roomId}_${System.currentTimeMillis()}".toString()
        //def cost = 0
        def value = [:]
        if (!Room.putIfAbsent(mainRedis, roomId, Room.user_id, userId)) {
            return Result.其他人正在游戏中
        }
        mainRedis.opsForHash().put(Room.user_hash(), '' + userId, '' + roomId)
        def hash = Room.hash(roomId)
        def playtime = (room['playtime'] as Integer ?: DEFAULT_PLAY_TIME) + PLAY_WAITING_TIME
        mainRedis.expire(hash, Long.parseLong('' + playtime), TimeUnit.SECONDS)
        value[Room.device_id] = fid
        value[Room.user_id] = '' + userId
        value[Room.record_id] = record_id
        value[Room.timestamp] = '' + t
        value[record_id] = '1'

        //记录流水
        def toy_id = room['toy_id'] as Integer
        def toy = null
        if (toy_id != null) {
            toy = catch_toys().findOne(toy_id) ?: null
        }
        if (toy == null || toy['price'] == null) {
            return Result.丢失必需参数
        }
        def price = toy['price'] as Integer
        def cost = (unlimit && price <= unlimit_min_cost) ? 0 : price
        //status 成功 true 失败 false
        //type 0-扣费 1-投币开始 2-结束 3-异常对账记录
        //n 用于补偿次数记录
        //todo 生成抓力控制参数
        int lw = 100
        int hw = 100
        int htl = 100
        def record = [_id: record_id, user_id: userId, room_id: roomId, fid: fid, type: CatchRecordType.扣费.ordinal(),
                      status: false, device_type: room['device_type'], channel: toy['channel'], coin: price, gid: room['gid'],
                      toy_points: toy['points'], cost: cost, n: 0, timestamp: t, is_delete: false, is_award: false] as Map
        if (toy != null) {
            def toy_record = [_id: toy['_id'], channel: toy['channel'], name: toy['name'], pic: toy['pic'], head_pic: toy['head_pic'], desc: toy['desc']]
            record.put('toy', toy_record)//记录礼物
        }
        def cost_log = Web.logCost("catch_live", toy['price'] as Integer, roomId, null)
        boolean assign = Boolean.TRUE
        boolean success = costDiamond(userId, cost, new DoCost() {
            @Override
            boolean costSuccess() {
                // 扣费成功调用绑定接口
                WWAssignDTO qiygAssignDTO = WawaMachine.assign(fid, record_id, userId, lw, hw, htl)
                if (qiygAssignDTO == null) {
                    assign = Boolean.FALSE
                    return false
                }
                record.put('log_id', qiygAssignDTO.getLog_id()) //游戏记录
                record.put('play_time', qiygAssignDTO.getPlaytime()) //游戏时长
                record.put('ws_url', qiygAssignDTO.getWs_url())  //socket地址
                //更改redis时间，观看者显示倒计时
                def time = qiygAssignDTO.getPlaytime()
                value.put(Room.play_time, '' + time)
                value.put(Room.play_until, '' + (System.currentTimeMillis() + (time + 1) * 1000L))
                value.put(Room.ws_url, qiygAssignDTO.getWs_url())
                Room.put(mainRedis, roomId, value)
                return true
            }
            BasicDBObject costLog() {cost_log}
        })

        if (!success) {
            //记录日志
            mainRedis.delete(Room.hash(roomId))
            mainRedis.opsForHash().delete(Room.user_hash(), '' + userId)
            if (!assign) {
                return Result.状态切换冷却中
            }
            return Result.余额不足
        }
        if (!catch_records().save($$(record), writeConcern)) {
            logger.error('catchu start error. record:' + record)
            mainRedis.delete(Room.hash(roomId))
            mainRedis.opsForHash().delete(Room.user_hash(), '' + userId)
            return Result.游戏记录创建失败
        }
        // 推送房间消息
        def user = users().findOne(userId, $$(nick_name: 1, pic: 1))
        def obj = [type: CatchMsgType.游戏开始.ordinal(), result: record_id, user: user, timestamp: t, play_time: record['play_time']]
        RoomMsgPublish.publish2Room(roomId, MsgAction.抓娃娃, obj, false)
        //RoomMsgPublish.roomVideoRestart(roomId, record_id)
        //RoomMsgPublish.roomVideoDispatchRestart(roomId, record_id)

        //增加一个延迟补偿队列, 到这个时间无论用户有没有收到回调都会弹出结果界面
        def delay = ((record['play_time'] as Long ?: 40L) + 15L) * 1000L
        addTask(room_finish_queue, roomId, userId, [record_id: record_id], delay)

        return [code: 1, data: [record_id: record_id, log_id: record['log_id'], time: record['play_time'] ?: 40, ws_url: record['ws_url']]]
    }

    /**
     * http://test-api.17laihou.com/qiyiguo/cancel_play/
     * @param req
     */
    def cancel_play(HttpServletRequest req) {
        // 用户放弃继续游戏
        def record_id = req.getParameter('record_id')
        def record = catch_records().findOne($$(_id: record_id))
        if (record == null) {
            return Result.丢失必需参数
        }
        def user_id = record['user_id'] as Integer
        def room_id = record['room_id'] as Integer
        finish_notify(room_id, user_id, record_id as String)
        return Result.success
    }

    def deleteIfNE(Object roomId, String record_id, Integer user_id) {
        def player_info = Room.getPlayerInfo(mainRedis, roomId as Integer)
        def record_count = mainRedis.opsForHash().get(Room.hash(roomId as Integer), record_id)
        def user_room_id = Room.getPlayerRoomId(mainRedis, user_id)

        if (player_info != null && record_count != null && record_id == player_info[Room.record_id]) {
            def n = record_count as Long
            if (Room.increment(mainRedis, roomId as Integer, record_id, 1L) == n + 1) {
                mainRedis.delete(Room.hash(roomId as Integer))
                return true
            }
        }
        logger.debug('=======>deleteifne record_id:' + record_id + ', room_id:' + roomId + ' ,playerinfo:' + player_info + ' ,user_room_id:' + user_room_id)
        if ((player_info == null || record_id == player_info[Room.record_id]) && user_room_id == roomId) {
            mainRedis.opsForHash().delete(Room.user_hash(), '' + user_id)
        }
        return false
    }

    /**
     * 获取用户所在的游戏房间，如果游戏结束则删除信息，返回null
     * @param userId
     * @return
     */
    def playerRoomInfo(Integer userId) {
        Integer roomId = Room.getPlayerRoomId(mainRedis, userId)
        if (roomId == null) {
            return null
        }
        def playerInfo = Room.getPlayerInfo(mainRedis, roomId)
        if (playerInfo == null || playerInfo[Room.user_id] != userId) {
            mainRedis.opsForHash().delete(Room.user_hash(), '' + userId)
            return null
        }
        return roomId
    }

    def finish_notify(Object room_id, Integer user_id, String record_id) {
        if (deleteIfNE(room_id, record_id, user_id)) {
            //房间推送结束通知
            def obj = [type: CatchMsgType.游戏结束.ordinal(), record_id: record_id, user: users().findOne(user_id, $$(nick_name: 1, pic: 1))]
            RoomMsgPublish.publish2Room(room_id, MsgAction.抓娃娃, obj, false)
        }
    }

    def need_notify(Object roomId, String record_id) {
        def player_info = Room.getPlayerInfo(mainRedis, roomId as Integer)
        if (player_info != null && player_info[Room.finish] == null && record_id == player_info[Room.record_id]) {
            if (Room.increment(mainRedis, roomId as Integer, Room.finish, 1L) == 1) {
                return true
            }
        }
        return false
    }

    /**
     * 是否发送继续抓提示
     * @param roomId
     * @param userId
     * @param log_id
     * @param obj
     * @return
     */
    def continue_notify(Integer roomId, Integer userId, String record_id, Map obj) {
        if (need_notify(roomId, record_id)) {
            RoomMsgPublish.publish2User(userId, MsgAction.抓娃娃, obj, false)
        }
    }

    static class Room {

        //用户ID
        public static final String user_id = 'user_id'
        //record_id，一个房间同一时间只有一个
        public static final String record_id = 'record_id'
        public static final String device_id = 'device_id'
        //开始时间
        public static final String timestamp = 'timestamp'
        public static final String play_time = 'play_time'
        public static final String play_until = 'play_until'
        public static final String log_id = 'log_id'
        public static final String ws_url = 'ws_url'
        public static final String finish = 'finish'

        //user_hash key
        public static final String room_id = 'room_id'

        static String user_hash() {
            return KeyUtils.CATCHU.player_info_hash()
        }

        static String hash(Integer roomId) {
            return KeyUtils.CATCHU.room_player_hash(roomId)
        }

        static boolean putIfAbsent(StringRedisTemplate mainRedis, Integer roomId, String key, Object value) {
            return mainRedis.opsForHash().putIfAbsent(hash(roomId), key, String.valueOf(value))
        }

        static void put(StringRedisTemplate mainRedis, Integer roomId, Map map) {
            mainRedis.opsForHash().putAll(hash(roomId), map)
        }

        static void put(StringRedisTemplate mainRedis, Integer roomId, String key, Object value) {
            mainRedis.opsForHash().put(hash(roomId), key, String.valueOf(value))
        }

        static Long increment(StringRedisTemplate mainRedis, Integer roomId, String targetKey, Long value) {
            return mainRedis.opsForHash().increment(hash(roomId), targetKey, value)
        }

        static Map getPlayerInfo(StringRedisTemplate mainRedis, Integer roomId) {
            def hash = hash(roomId)
            def opsForHash = mainRedis.opsForHash()
            if (!mainRedis.hasKey(hash) || !opsForHash.hasKey(hash, user_id)) {
                return null
            }
            def play_until = opsForHash.get(hash, play_until) as Long //游戏截止时间
            def user_id = opsForHash.get(hash, user_id) as Integer
            def log_id = opsForHash.get(hash, log_id) as String
            def record_id = opsForHash.get(hash, record_id) as String
            def ws_url = opsForHash.get(hash, ws_url) as String
            def finish = opsForHash.get(hash, finish) as String

            return [user_id: user_id, log_id: log_id, record_id: record_id, ws_url: ws_url, play_until: play_until, finish: finish]
        }

        static Integer getPlayerRoomId(StringRedisTemplate mainRedis, Integer userId) {
            def opsForHash = mainRedis.opsForHash()
            if (!mainRedis.hasKey(user_hash()) || !opsForHash.hasKey(user_hash(), '' + userId)) {
                return null
            }
            def roomId = opsForHash.get(user_hash(), '' + userId)
            return Integer.parseInt(roomId as String)
        }

    }

    public static final String result_notify_queue = "notify:room:result:qiyiguo"
    public static final String room_finish_queue = "notify:room:finish:qiyiguo"

    def createNotifyQueue(String queueName, DelayQueueRedis.DelayQueueJobListener listener) {
        //初始化延迟队列
        final DelayQueueRedis notifyQueue = DelayQueueRedis.generateQueue(queueName);
        //创建任务
        //final DelayQueueRedis.Task task = new DelayQueueRedis.Task('refresh_room_list', delay, "刷新娃娃机状态任务")
        //将任务加入延迟队列中
        //refreshRoomQueue.offer(task);
        //注册延迟任务callback
        notifyQueue.addListner(listener)
    }

    def addTask(String queueName, Integer roomId, Integer userId, Map desc, Long delay) {
        DelayQueueRedis queue = DelayQueueRedis.generateQueue(queueName)
        //String key = KeyUtils.CATCHU.room_player_continue(roomId)
        //if (mainRedis.opsForValue().setIfAbsent(key, '' + userId)) {
        desc.putAll([room_id: roomId, user_id: userId])
        DelayQueueRedis.Task task = new DelayQueueRedis.Task('' + roomId, delay, JSONUtil.beanToJson(desc))
        queue.offer(task)
        //}
    }
}
