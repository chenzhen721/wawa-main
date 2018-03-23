package com.wawa.web.api

import com.mongodb.BasicDBObject
import com.mongodb.DBCollection
import com.wawa.api.Web
import com.wawa.base.anno.RestWithSession
import com.wawa.common.doc.TwoTableCommit
import com.wawa.base.Crud
import com.wawa.common.doc.Result
import com.wawa.common.util.HttpClientUtils
import com.wawa.common.util.JSONUtil
import com.wawa.common.util.LabMsgExecutor
import com.wawa.common.util.RandomExtUtils
import com.wawa.model.CatchObserveStatus
import com.wawa.model.CatchPartnerType
import com.wawa.model.CatchPostChannel
import com.wawa.model.CatchPostStatus
import com.wawa.model.CatchPostType
import com.wawa.model.UserAwardType
import com.wawa.base.BaseController
import com.wawa.web.partner.QiyiguoController
import groovy.json.JsonSlurper
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.CollectionUtils
import org.springframework.web.bind.ServletRequestUtils

import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest

import static com.wawa.common.util.WebUtils.$$
import static com.wawa.common.doc.MongoKey.*

/**
 * 抓娃娃
 */
@RestWithSession
class CatchuController extends BaseController {

    static final Logger logger = LoggerFactory.getLogger(CatchuController.class)

    public static final String ORDER_PREFIX = isTest ? "test-play" : "play"
    public static final Integer min_count = isTest ? 3 : 3 //最少邮寄数量
    public static final long RECORD_EXPIRE_DAYS = 10
    public static final JsonSlurper jsonSlurper = new JsonSlurper()

    @Resource
    QiyiguoController qiyiguoController
    @Resource
    UserController userController

    DBCollection catch_rooms() {
        return catchMongo.getCollection('catch_room')
    }
    DBCollection catch_users() {
        return catchMongo.getCollection('catch_user')
    }
    DBCollection catch_toys() {
        return catchMongo.getCollection('catch_toy')
    }
    DBCollection catch_records() {
        return catchMongo.getCollection('catch_record')
    }
    DBCollection apply_post_logs() {
        return logMongo.getCollection('apply_post_logs')
    }
    DBCollection catch_success_logs() {
        return logMongo.getCollection('catch_success_logs')
    }
    DBCollection catch_observe_logs() {
        return logMongo.getCollection('catch_observe_logs')
    }
    DBCollection goods() {
        return adminMongo.getCollection('goods')
    }
    DBCollection stat_daily() {
        return adminMongo.getCollection('stat_daily')
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Catchu
     * @apiName heartbeat
     * @api {get} catchu/heartbeat/:access_token  网速心跳
     * @apiDescription
     * 网速心跳
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/catchu/heartbeat/15334c1921ad8da6224c98e202a652b4

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def heartbeat(HttpServletRequest req) {
        return [code: 1]
    }

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
     *     curl -i http://test-api.17laihou.com/catchu/room/15334c1921ad8da6224c98e202a652b4/1005

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "data": {
     *         "_id": 10000016,
     *         "fid": '1005',
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
     *         "partner": 1,
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
     *             "head_pic": "",
     *             "desc": "123"
     *         }
     *     },
     *     "exec": 3430,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def room(HttpServletRequest req) {
        // 房间状态
        def roomId = Web.roomId(req)
        if (!roomId) {
            return Result.丢失必需参数
        }
        def goods = goods().findOne($$(_id: roomId))
        if (goods == null) {
            return Result.丢失必需参数
        }
        inc_enter_count(roomId, goods['toy_id'] as Integer)
        if (goods['type'] == Boolean.FALSE) {
            return Result.设备维护中
        }
        //如果是代抓则分配机器
        def room = getRoom(goods)
        if (room == null) {
            return Result.设备维护中
        }
        room['toy_id'] = goods['toy_id']
        room['goods_id'] = roomId
        room['is_replace'] = goods['is_replace']
        room['type'] = goods['type']
        room.put('is_following', userController.isFollowing(Web.currentUserId, roomId))
        return qiyiguoController.room(room as Map)
    }

    /**
     * 获取房间信息
     * @param goods
     * @return
     */
    private Map getRoom(def goods) {
        def is_replace = goods['is_replace'] as Boolean ?: false
        def room_id = null
        def rids = goods['rids'] as List ?: []
        if (!is_replace) {
            room_id = goods['room_id']
        } else {
            /*if (CollectionUtils.isEmpty(rids)) {
                rids.add(goods['room_id'])
            }*/
            //获取用户是否在某间房间内游戏
            Integer userRoomId = QiyiguoController.Room.getPlayerRoomId(mainRedis, Web.currentUserId) as Integer
            if (rids.contains(userRoomId)) {
                room_id = userRoomId
            } else {
                def statusMap = getStatusByRoomIds(rids)
                def readyIds = [], busyIds = []
                for(Map.Entry entry : statusMap.entrySet()) {
                    Integer key = entry.getKey() as Integer
                    Integer value = entry.getValue() as Integer
                    if (value == 0) {
                        readyIds.add(key)
                    } else {
                        busyIds.add(key)
                    }
                }
                if (room_id == null && readyIds.size() > 0) {
                    room_id = readyIds.get(RandomExtUtils.randomBetweenMinAndMax(0, readyIds.size()))
                }
                if (room_id == null && busyIds.size() > 0) {
                    room_id = busyIds.get(RandomExtUtils.randomBetweenMinAndMax(0, busyIds.size()))
                }
            }
        }
        if (room_id == null) {
            room_id = goods['room_id'] as Integer
        }
        def room = catch_rooms().findOne($$(_id: room_id))
        room.put('goods_id', goods['_id'])
        room.put('toy_id', goods['toy_id'])
        return room as Map
    }

    private Map getStatusByRoomIds(List<Integer> roomIds) {
        def result = [:]
        roomIds.each { Integer room_id->
            result.put(room_id, getRoomStatus(room_id))
        }
        return result
    }

    // todo
    private int getRoomStatus(Integer roomId) {
        def status = 0
        /*def playerinfo = ZegoController.Room.getPlayerInfo(mainRedis, roomId) //这个逻辑和奇异果一样
        if (playerinfo != null) {
            status = 1
        }*/
        return status
    }

    /**
     * 增加房间计数
     * @param req
     * @return
     */
    private void inc_enter_count(final Integer goods_id, final Integer toy_id) {
        final DBCollection stat_daily = stat_daily()
        //生成对应天数的记录sign_logs, 奖励当天算起
        LabMsgExecutor.execute(
                new Runnable() {
                    @Override
                    void run() {
                        def _id = "${new Date().format('yyyyMMdd')}_${goods_id}_${toy_id}_entry_count".toString()
                        def type = 'room_entry_event'
                        stat_daily.update($$(_id: _id), $$($set: [goods_id: goods_id, toy_id: toy_id, type: type, timestamp: new Date().clearTime().getTime()], $inc: [total: 1]), true, false)
                    }
                }
        )
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Catchu
     * @apiName start
     * @api {get} catchu/start/:access_token/:room_id?goods_id=:goods_id  开始游戏
     * @apiDescription
     * 开始游戏
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Integer} [room_id]  房间ID
     * @apiParam {Integer} [goods_id]  货架ID
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/catchu/start/11f69035f0fdb1d11381407b2b0ed1df/10000016?goods_id=123

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "data": {
     *         "record_id": "1234567",
     *         "ws_url": "ws://"
     *     },
     *     "exec": 293,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def start(HttpServletRequest req) {
        def goods_id = ServletRequestUtils.getIntParameter(req, 'goods_id')
        if (goods_id == null) {
            return Result.丢失必需参数
        }
        def goods = goods().findOne($$(_id: goods_id))
        if (!goods) {
            return Result.丢失必需参数
        }
        if (goods['online'] == null || !goods['online'] || goods['type'] == null || !goods['type']) {
            return Result.设备维护中
        }
        def roomId = Web.roomId(req)
        def room = catch_rooms().findOne(roomId)
        if (!room) {
            return Result.丢失必需参数
        }
        room.put('toy_id', goods['toy_id'])
        room.put('gid', goods_id)

        return qiyiguoController.start(room as Map)
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Catchu
     * @apiName cancel_paly
     * @api {get} catchu/cancel_paly/:access_token/:room_id  放弃继续游戏
     * @apiDescription
     * 放弃继续游戏
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Integer} [room_id]  房间ID
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/catchu/cancel_paly/11f69035f0fdb1d11381407b2b0ed1df/10000016

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "data": {
     *         "record_id": "1234567",
     *         "ws_url": "ws://"
     *     },
     *     "exec": 293,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def cancel_paly(HttpServletRequest req) {
        def roomId = Web.roomId(req)
        def room = catch_rooms().findOne(roomId)
        if (!room) {
            return Result.丢失必需参数
        }
        return qiyiguoController.cancel_play(req)
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Catchu
     * @apiName state_flow
     * @api {get} catchu/state_flow/:access_token/:record_id?msg=:msg  游戏消息流
     * @apiDescription
     * 游戏消息流
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {String} [record_id]  游戏记录ID
     * @apiParam {String} [msg]  消息内容
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/catchu/confirm_ready/11f69035f0fdb1d11381407b2b0ed1df/10000016

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "data": {
     *         "record_id": "1234567",
     *         "ws_url": "ws://"
     *     },
     *     "exec": 293,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def state_flow(HttpServletRequest req) {
        return [code: 1]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Catchu
     * @apiName catch_list
     * @api {get} catchu/catch_list/:access_token?type=:type&post_type=:post_type&room_id=:room_id&page=:page&size=:size  全部流水
     * @apiDescription
     * 全部流水
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Integer} [room_id]  房间ID type为0 必传
     * @apiParam {Integer} [type]  查询类型 0 房间记录 1用户记录 不传按时间顺序查询全部
     * @apiParam {Integer} [page]  页号
     * @apiParam {Integer} [size]  个数
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/catchu/catch_list/11f69035f0fdb1d11381407b2b0ed1df?page=1&size=10

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "data": {
     *         "_id": "1234567",
     *         "user_id": "ws://",
     *         "room_id": "ws://",
     *         "timestamp": "ws://",
     *         "toy": {
     *             "desc": "",
     *             "pic": "",
     *             "name": "",
     *             "_id": ""
     *         },
     *         "user": {
     *             "nick_name": "",
     *             "pic": ""
     *         },
     *         "is_delete": true, //是否删除
     *         "award_points": 100, //奖励积分
     *         "is_award": true, //是否已兑换积分
     *         "remaining": 10 //是否过期
     *     },
     *     "exec": 293,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def catch_list(HttpServletRequest req) {
        def type = ServletRequestUtils.getIntParameter(req, 'type')
        def query = $$(type: 2, is_delete: [$ne: true]) //正常结束且成功的记录
        def sort = SJ_DESC
        query.put('user_id', Web.currentUserId)
        //命中次数
        def time = new Date().clearTime().getTime()
        Crud.list(req, catch_records(), query, ALL_FIELD, sort) { List<BasicDBObject> list->
            for (BasicDBObject obj: list) {
                if (type == 1) {
                    def timestamp = obj['timestamp'] as Long
                    def dur = (((time - timestamp) / DAY_MILLON) as Double).longValue() + 1
                    def remaining = RECORD_EXPIRE_DAYS - dur
                    obj['remaining'] = remaining >= 0 ? remaining : 0 //剩余天数
                    //obj['is_delete'] = obj['is_delete'] ?: false //是否删除
                    def diamond = obj['coin'] as Integer
                    obj['award_points'] = obj['toy_points'] ?: (diamond * 100).intValue() //
                    obj['is_award'] = obj['is_award'] ?: false //是否兑换积分
                } else {
                    obj['user'] = users().findOne(obj['user_id'] as Integer, $$(nick_name: 1, pic: 1))
                }
            }
        }
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Catchu
     * @apiName record_list
     * @api {get} catchu/record_list/:access_token?type=:type&post_type=:post_type&room_id=:room_id&page=:page&size=:size  成功流水
     * @apiDescription
     * 成功流水
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Integer} [room_id]  房间ID
     * @apiParam {Integer} [type]  查询类型 0 房间记录 1用户记录 不传按时间顺序查询全部
     * @apiParam {Integer} [post_type]  邮寄状态
     * @apiParam {Integer} [page]  页号
     * @apiParam {Integer} [size]  个数
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/catchu/record_list/11f69035f0fdb1d11381407b2b0ed1df?post_type=1&room_id=&page=1&size=10

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "data": {
     *         "_id": "1234567",
     *         "user_id": "ws://",
     *         "room_id": "ws://",
     *         "timestamp": "ws://",
     *         "toy": {
     *             "desc": "",
     *             "pic": "",
     *             "name": "",
     *             "_id": "",
     *             "head_pic": ""
     *         },
     *         "user": {
     *             "nick_name": "",
     *             "pic": ""
     *         },
     *         "is_delete": true, //是否删除
     *         "award_points": 100, //奖励积分
     *         "is_award": true, //是否已兑换积分
     *         "remaining": 10, //是否过期
     *         "is_observ": 是否已申述 新增字段
     *     },
     *     "exec": 293,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def record_list(HttpServletRequest req) {
        def type = ServletRequestUtils.getIntParameter(req, 'type')
        def postType = ServletRequestUtils.getIntParameter(req, 'post_type')
        def query = $$(is_delete: [$ne: true]) //正常结束且成功的记录
        def sort = SJ_DESC
        if(type == 0) {
            def room_id = ServletRequestUtils.getIntParameter(req, 'room_id')
            if (room_id == null) {
                return Result.丢失必需参数
            }
            query.put('room_id', room_id)
        } else if (type == 1) {
            sort = $$(post_type: 1, timestamp: -1)
            query.put('user_id', Web.currentUserId)
        }
        if (postType != null) {
            query.put('post_type', postType)
        }
        //命中次数
        def time = new Date().clearTime().getTime()
        Crud.list(req, catch_success_logs(), query, ALL_FIELD, sort) { List<BasicDBObject> list->
            for (BasicDBObject obj: list) {
                if (type == 1) {
                    def timestamp = obj['timestamp'] as Long
                    def dur = (((time - timestamp) / DAY_MILLON) as Double).longValue() + 1
                    def remaining = RECORD_EXPIRE_DAYS - dur
                    obj['remaining'] = remaining >= 0 ? remaining : 0 //剩余天数
                    obj['is_delete'] = obj['is_delete'] ?: false //是否删除
                    def diamond = obj['coin'] as Integer
                    obj['award_points'] = obj['toy_points'] ?: (diamond * 100).intValue() //todo 奖励积分
                    obj['is_award'] = obj['is_award'] ?: false //是否兑换积分
                } else {
                    obj['user'] = users().findOne(obj['user_id'] as Integer, $$(nick_name: 1, pic: 1))
                }
                obj['is_observ'] = obj['is_observ'] ?: false //是否申述
            }
        }
    }

    /**
     * 抓中娃娃过期处理
     * @param req
     * @return
     */
    def catch_success_expire(HttpServletRequest req) {
        Date stime = Web.getStime(req)
        Date etime = Web.getEtime(req)
        if (stime == null || etime == null) {
            return Result.error
        }
        def query = $$(timestamp: [$gte: stime.getTime(), $lt: etime.getTime()], is_award: [$ne: true], post_type: CatchPostType.未处理.ordinal(), is_delete: [$ne: true])
        catch_success_logs().find(query).toArray().each { BasicDBObject obj ->
            exchange(obj)
        }
        return Result.success
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Catchu
     * @apiName exchange_points
     * @api {get} catchu/exchange_points/:access_token?ids=:ids  手动兑换积分
     * @apiDescription
     * 手动兑换积分
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {String} [ids]  记录ID,多条记录以|分隔
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/catchu/exchange_points/11f69035f0fdb1d11381407b2b0ed1df

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "exec": 293,
     *     "code": 1,
     *     "data": {
     *         "success": ['123', '234'],
     *         "fail": ['123', '234']
     *     }
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def exchange_points(HttpServletRequest req) {
        def ids = req.getParameter('ids')
        if (StringUtils.isBlank(ids)) {
            return Result.丢失必需参数
        }
        def logIds = ids.split('\\|')
        def query = $$(_id: [$in: logIds], is_award: [$ne: true], post_type: CatchPostType.未处理.ordinal(), is_delete: [$ne: true])
        def succ = []
        def fail = []
        catch_success_logs().find(query).toArray().each { BasicDBObject obj ->
            if (exchange(obj)) {
                succ.add(obj['_id'])
            } else {
                fail.add(obj['_id'])
            }
        }
        return [code: 1, data: [success: succ, fail: fail]]
    }

    private boolean exchange(BasicDBObject obj) {
        def user_id = obj['user_id'] as Integer
        def coin = obj['coin'] as Integer
        //获取补单的娃娃积分
        def points = obj.containsField('toy_points') ? obj['toy_points'] as Integer: (coin * 100).intValue() //奖励积分 减娃娃
        if (catch_success_logs().update($$(_id: obj['_id'], is_award: [$ne: true], post_type: CatchPostType.未处理.ordinal(), is_delete: [$ne: true]),
                $$($set: [is_award: true, toy_points: points]),
                false , false, writeConcern).getN() == 1) {
            def logWithId = Web.awardLog(user_id, UserAwardType.过期兑积分, [points: points])
            logWithId.append('success_log_id', obj['_id'])
            boolean succ = Crud.doTwoTableCommit(logWithId, [
                    main           : { mainMongo.getCollection("users") },
                    logColl        : { logMongo.getCollection('user_award_logs') },
                    queryWithId    : { $$('_id': user_id) },
                    update         : { $$($inc, $$('bag.points.count', points)) },
                    successCallBack: {
                        return true
                    },
                    rollBack       : { $$($inc, $$('bag.points.count', -points)) }
            ] as TwoTableCommit)
            if (!succ) {
                logger.error('failed to update expire toys, log: ' + obj['_id'])
                catch_success_logs().update($$(_id: obj['_id'], is_award: true), $$($unset: [is_award: 1, points: 1]),
                        false , false, writeConcern)
                return false
            }
            return true
        }
        return false
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Catchu
     * @apiName record_delete
     * @api {get} catchu/record_delete/:access_token/_id  删除过期已兑换积分的记录
     * @apiDescription
     * 删除过期已兑换积分的记录
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Integer} [_id]  记录ID
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/catchu/record_delete/11f69035f0fdb1d11381407b2b0ed1df/123

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "exec": 293,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def record_delete(HttpServletRequest req) {
        def _id = Web.firstParam(req)
        if (_id == null) {
            return Result.丢失必需参数
        }
        //def expire = new Date().clearTime().getTime() - (RECORD_EXPIRE_DAYS - 1) * DAY_MILLON
        if (1 == catch_success_logs().update($$(_id: _id, is_award: true, is_delete: [$ne: true]), $$($set: [is_delete: true]), false, false, writeConcern).getN()) {
            return Result.success
        }
        return Result.error
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Catchu
     * @apiName record_observation
     * @api {get} catchu/record_observation/:access_token/_id?type=:type 记录申述
     * @apiDescription
     * 记录申述
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Integer} [_id]  记录ID
     * @apiParam {Integer} [type] 申述类型 0 卡顿延迟 1 抓到娃娃不算
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/catchu/record_observation/11f69035f0fdb1d11381407b2b0ed1df/123

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "exec": 293,
     *     "code": 1
     * }
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def record_observation(HttpServletRequest req) {
        def type = ServletRequestUtils.getIntParameter(req, 'type')
        def record_id = Web.firstParam(req)
        if (StringUtils.isBlank(record_id) || type == null) {
            return Result.丢失必需参数
        }
        def records = catch_records().findOne($$(_id: record_id))
        if (records == null) {
            return Result.丢失必需参数
        }
        if (catch_observe_logs().count($$(_id: record_id)) > 0) {
            return Result.success
        }
        //奖励用 award
        if (1 == catch_observe_logs().save($$(_id: record_id, replay_url: records['replay_url'], type: type,
                status: CatchObserveStatus.未处理.ordinal(), is_notify: false,
                user_id: Web.currentUserId, room_id: records['room_id'], user: Web.currentUser(), timestamp: System.currentTimeMillis()), writeConcern).getN()) {
            catch_records().update($$(_id: record_id), $$($set: [is_observ: true]))
            return Result.success
        }
        return Result.error
    }

    /**
     * 只通知一次
     * @param req
     */
    def record_observ_notify(Integer user_id) {
        def logs = catch_observe_logs().find($$(user_id: user_id, status: CatchObserveStatus.已处理.ordinal(), is_notify: false, award: [$exists: true])).toArray()
        if (logs.size() < 0) {
            return null
        }
        def ids = []
        def n = 0
        logs.each { BasicDBObject obj ->
            if (!isTest || (isTest && n < 1)) {
                ids.add(obj['_id'])
            }
            n = n + 1
        }
        if (1 <= catch_observe_logs().update($$(_id: [$in: ids]), $$($set: [is_notify: true]), false , true, writeConcern).getN()) {
            return logs
        }
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Catchu
     * @apiName post_list
     * @api {get} catchu/post_list/:access_token?type=:type  邮寄记录
     * @apiDescription
     * 邮寄记录
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Integer} [type]  发货状态
     * @apiParam {Integer} [channel]  邮寄通道：0 奇异果, 1 活动人工,2 即构
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/catchu/post_list/11f69035f0fdb1d11381407b2b0ed1df?type=&page=1&size=10

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "data": {
     *         "_id": "1234567",
     *         "user_id": "ws://",
     *         "room_id": "ws://",
     *         "timestamp": "ws://",
     *         "toys": [{
     *             "desc": "",
     *             "pic": "",
     *             "name": "",
     *             "_id": "",
     *             "head_pic": ""
     *         }],
     *         "apply_time":123,
     *         "post_type": 1,  1- 待发货, 2-已发货
     *     },
     *     "exec": 293,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def post_list(HttpServletRequest req) {
        def user_id = Web.currentUserId
        def query = $$(user_id: user_id, is_delete: [$ne: true], post_type: [$ne: CatchPostType.未处理.ordinal()], status: [$ne: CatchPostStatus.审核失败.ordinal()])
        def type = ServletRequestUtils.getIntParameter(req, 'type')
        if (type != null) {
            query.put('post_type', type)
        }
        /*def channel = ServletRequestUtils.getIntParameter(req, 'channel')
        if (channel != null) {
            query.put('channel', channel)
        }*/
        Crud.list(req, apply_post_logs(), query, ALL_FIELD, $$(timestamp: -1))
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Catchu
     * @apiName apply_post
     * @api {get} catchu/apply_post/:access_token?records=:records  申请邮寄
     * @apiDescription
     * 申请邮寄
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {String} [records]  需要邮寄的记录ID, 多个记录用|分隔
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/catchu/apply_post/11f69035f0fdb1d11381407b2b0ed1df?records=123

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "exec": 293,
     *     "code": 1,
     *     "data": {
     *         "_id": ""
     *     }
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def apply_post(HttpServletRequest req) {
        def record_ids = ServletRequestUtils.getStringParameter(req, 'records')
        if (StringUtils.isBlank(record_ids)) {
            return Result.丢失必需参数
        }
        def ids = record_ids.split('\\|')
        if (ids.size() <= 0) {
            return Result.丢失必需参数
        }
        def need_postage = Boolean.FALSE
        if (ids.size() < min_count) {
            //需付邮费才能邮寄
            need_postage = Boolean.TRUE
        }
        def userId = Web.currentUserId
        //用户的默认地址
        def def_addr = catch_users().findOne(userId)
        if (def_addr == null) {
            return Result.未填写地址
        }
        def list = def_addr['address_list'] as List
        if (list == null || list.size() <= 0) {
            return Result.未填写地址
        }
        def address = null
        for(int i = 0; i < list.size(); i++) {
            def add = list.get(i)
            if (add['is_default'] == Boolean.TRUE) {
                address = add
                break
            }
        }
        if (address == null) {
            return Result.未选择默认地址
        }
        def addressstr = "${address['province'] ?: ''}${address['city'] ?: ''}${address['region'] ?: ''}${address['street'] ?: ''}${address['address']}".toString()
        if (catch_success_logs().count($$(_id: [$in: ids], pack_id: [$exists: true])) > 0) {
            return Result.无法下单 //有不符合条件的记录
        }

        if (apply_post_logs().count($$('record_ids': [$in: ids], is_delete: [$ne: true], is_award: [$ne: true])) > 0) {
            return Result.无法下单 //有已申请记录
        }

        //打包记录 改成后台发货
        def time = System.currentTimeMillis()
        def timeStr = new Date().format('yyMMddHHmmss')
        def pack_id = "${timeStr}${userId}".toString()
        def logs = catch_success_logs().find($$(_id: [$in: ids]), $$(room_id: 1, toy: 1, goods_id: 1))
        def toys = create_toy(logs)
        def logWithId = $$([_id         : pack_id, user_id: userId, record_ids: ids, toys: toys, timestamp: time, post_type: CatchPostType.待发货.ordinal(),
                                                                status      : CatchPostStatus.未审核.ordinal(), address: address, address_list: addressstr, is_delete: false,
                                                                need_postage: need_postage])//, order_id: order_id
        if (need_postage) {
            logWithId.append('is_pay_postage', Boolean.FALSE)
        }
        if (1 <= catch_success_logs().update($$(_id: [$in: ids], pack_id: [$exists: false], is_delete: [$ne: true], is_award: [$ne: true]),
                $$($set: [pack_id: pack_id, post_type: CatchPostType.待发货.ordinal(), apply_time: time, is_delete: false]), false, true, writeConcern).getN()) {
            apply_post_logs().save(logWithId, writeConcern)
            if (need_postage) {
                return [code: 1, data: [_id: pack_id]]
            }
            return Result.success
        }
        return Result.抢的人太多了
    }

    private List create_toy(def logs) {
        def toys = []
        logs.each { BasicDBObject obj ->
            def toy = obj['toy'] as Map
            if (toy['channel'] == CatchPostChannel.奇异果.ordinal()) {
                if (toy['goods_id'] == null) {
                    Integer goods_id = obj['goods_id'] as Integer ?: null
                    toy.put('goods_id', goods_id)
                }
            }
            toy.put('room_id', obj['room_id'])
            toy.put('record_id', obj['_id'])
            toys.add(obj['toy'])
        }
        return toys
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Catchu
     * @apiName post_info
     * @api {get} catchu/post_info/:access_token/:_id  邮寄详情
     * @apiDescription
     * 申请邮寄
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {String} [_id]  邮寄记录ID
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/catchu/post_info/11f69035f0fdb1d11381407b2b0ed1df/123

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "data": {
     *         "state": 3, // 0在途 1揽件 2疑难 3签收 4退签 5派件 6退回
     *         "shipping_no": 快递单号,
     *         "shipping_com": 快递公司名称,
     *         "list": [
     *             {
     *                 "time": "2017-11-30 13:28:33", //时间
     *                 "location": "肥东龙岗开发区四", //包裹地址
     *                 "context": "", //消息内容
     *                 "ftime": "2017-11-30 13:28:33" //可忽略
     *             }
     *         ]
     *     },
     *     "exec": 3,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def post_info(HttpServletRequest req) {
        def order_id = Web.firstParam(req)
        if (StringUtils.isBlank(order_id)) {
            return Result.丢失必需参数
        }
        //快递单号查询
        def post_logs = apply_post_logs().findOne($$(_id: order_id, user_id: Web.currentUserId), $$(post_info: 1))
        if (post_logs == null || post_logs['post_info'] == null || post_logs['post_info']['shipping_no'] == null) {
            return Result.暂无快递信息
        }
        def post_info = post_logs['post_info'] as BasicDBObject
        //需要查询物流信息
        def no = post_info['shipping_no']
        def com = post_info['shipping_com']

        if (post_info['state'] == 3 || (post_info['next_time'] != null && (post_info['next_time'] as Long) > System.currentTimeMillis())) {
            if (post_info['state'] == null || post_info['post_list'] == null) {
                return Result.暂无快递信息
            }
            def post_list = jsonSlurper.parseText(post_info['post_list'] as String)
            return [code: 1, data: [state: post_info['state'], list: post_list, shipping_no: no, shipping_com: com]]
        }

        def url = "http://www.kuaidi100.com/query?id=1&type=${com}&postid=${no}".toString()
        def list_str = null
        try {
            list_str = HttpClientUtils.get(url, null)
        } catch (Exception e) {
            logger.error("error to get user post list, ${url}.".toString() + e)
        }
        def obj = null
        if (StringUtils.isNotBlank(list_str)) {
            obj = jsonSlurper.parseText(list_str)
        }
        def next_time = System.currentTimeMillis() + 2 * 60 * 60 * 1000L
        if (obj == null || obj['status'] != "200" || obj['state'] == null || obj['data'] == null) {
            apply_post_logs().update($$(_id: order_id), $$($set: ['post_info.next_time': next_time]), false, false, writeConcern)
            return Result.暂无快递信息
        }
        //保存信息至快递栏/
        def state = Integer.parseInt(obj['state'] as String)
        def list = obj['data'] as List
        def update = $$($set: ['post_info.next_time': next_time, 'post_info.state': state, 'post_info.post_list': JSONUtil.beanToJson(list)])
        apply_post_logs().update($$(_id: order_id), update, false, false, writeConcern)
        return [code: 1, data: [state: state, list: list, shipping_no: no, shipping_com: com]]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Catchu
     * @apiName address_list
     * @api {get} catchu/address_list/:access_token  用户收货地址列表
     * @apiDescription
     * 用户收货地址列表
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/catchu/address_list/11f69035f0fdb1d11381407b2b0ed1df

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "data": [{
     *             "_id": "20088731_1508416721947",
     *             "province": "1",
     *             "city": "2",
     *             "region": "3",
     *             "address": "4",
     *             "name": "5",
     *             "tel": "6",
     *             "is_default": true
     *         }],
     *     "exec": 293,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def address_list(HttpServletRequest req) {
        def userId = Web.currentUserId
        def catchUser = catch_users().findOne($$(_id: userId), $$(address_list: 1))
        return [code: 1, data: (catchUser && catchUser['address_list']) ? catchUser['address_list'] : []]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Catchu
     * @apiName address_default
     * @api {get} catchu/address_default/:access_token  用户默认收货地址
     * @apiDescription
     * 用户默认收货地址
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/catchu/address_default/11f69035f0fdb1d11381407b2b0ed1df

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "data": {
     *             "_id": "20088731_1508416721947",
     *             "province": "1",
     *             "city": "2",
     *             "region": "3",
     *             "address": "4",
     *             "name": "5",
     *             "tel": "6",
     *             "is_default": true
     *         },
     *     "exec": 293,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def address_default(HttpServletRequest req) {
        def userId = Web.currentUserId
        def catchUser = catch_users().findOne($$(_id: userId), $$(address_list: 1))
        if (catchUser == null) {
            return [code: 1]
        }
        def list = catchUser['address_list'] as List ?: []
        def index = 0
        for(int i = 0; i < list.size(); i++) {
            def add = list.get(i)
            def is_default = add['is_default'] as Boolean ?: false
            if (is_default) {
                index = i
                break
            }
        }
        return [code: 1, data: list.size() > 0 ? list[index] : [:]]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Catchu
     * @apiName address_add
     * @api {get} catchu/address_add/:access_token  收货地址添加
     * @apiDescription
     * 收货地址添加
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {String} [province]  省
     * @apiParam {String} [city] 市
     * @apiParam {String} [region]  区
     * @apiParam {String} [street]  区
     * @apiParam {String} [address]  详细地址
     * @apiParam {String} [name]  联系人
     * @apiParam {String} [tel]  电话
     * @apiParam {Bool} [is_default]  是否默认
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/catchu/address_add/11f69035f0fdb1d11381407b2b0ed1df

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "exec": 293,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def address_add(HttpServletRequest req) {
        def userId = Web.currentUserId
        def province = ServletRequestUtils.getStringParameter(req, 'province')
        def city = ServletRequestUtils.getStringParameter(req, 'city')
        def region = ServletRequestUtils.getStringParameter(req, 'region')
        def street = ServletRequestUtils.getStringParameter(req, 'street')
        def address = ServletRequestUtils.getStringParameter(req, 'address')
        def name = ServletRequestUtils.getStringParameter(req, 'name')
        def tel = ServletRequestUtils.getStringParameter(req, 'tel')
        def is_default = ServletRequestUtils.getBooleanParameter(req, 'is_default', false)
        def _id = "${userId}_${System.currentTimeMillis()}".toString()
        def addr = $$(_id: _id, province: province, city: city, region: region, street: street, address: address, name: name, tel: tel)
        def user = catch_users().findOne($$(_id: userId))
        if (user == null || user['address_list'] == null || CollectionUtils.isEmpty(user['address_list'] as List)) {
            is_default = true
        }
        addr.put('is_default', is_default)
        if (1 == catch_users().update($$(_id: userId), $$($push: [address_list: addr]), true, false, writeConcern).getN()) {
            return Result.success
        }
        return Result.error
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Catchu
     * @apiName address_edit
     * @api {get} catchu/address_edit/:access_token/:_id  收货地址修改
     * @apiDescription
     * 收货地址添加
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {String} [_id]  id
     * @apiParam {String} [province]  省
     * @apiParam {String} [city] 市
     * @apiParam {String} [region]  区
     * @apiParam {String} [street]  区
     * @apiParam {String} [address]  详细地址
     * @apiParam {String} [name]  联系人
     * @apiParam {String} [tel]  电话
     * @apiParam {Bool} [is_default]  是否默认
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/catchu/address_edit/11f69035f0fdb1d11381407b2b0ed1df/1

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "exec": 293,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def address_edit(HttpServletRequest req) {
        def _id = Web.firstParam(req)
        if (!_id) {
            return Result.丢失必需参数
        }
        def userId = Web.currentUserId
        def is_default = ServletRequestUtils.getBooleanParameter(req, 'is_default', false)
        def query = $$(_id: userId, 'address_list._id': _id)
        def update = new BasicDBObject()
        def set = new BasicDBObject()
        def unset = new BasicDBObject()
        def def_addr = catch_users().findOne(userId)
        if (def_addr == null) {
            return [code: 0]
        }
        def list = def_addr['address_list'] as List
        def index = -1
        for(int i = 0; i < list.size(); i++) {
            def add = list.get(i)
            if (add['_id'] == _id) {
                index = i
            }
            if (is_default) {
                if (add['is_default'] && add['_id'] != _id) {
                    set.put("address_list.${i}.is_default".toString(), false)
                    query.put("address_list.${i}.is_default".toString(), true)
                }
                if (!add['is_default'] && add['_id'] == _id) {
                    set.put("address_list.${i}.is_default".toString(), true)
                    query.put("address_list.${i}.is_default".toString(), false)
                }
            }
        }
        ['province', 'city', 'region', 'street', 'address', 'name', 'tel'].each { String key ->
            def value = ServletRequestUtils.getStringParameter(req, key)
            if (value != null) {
                set.put("address_list.${index}.${key}".toString(), value)
            }
        }
        if (!unset.isEmpty()) {
            update.put($unset, unset)
        }
        if (!set.isEmpty()) {
            update.put($set, set)
        }
        if (update.isEmpty()) {
            return Result.丢失必需参数
        }
        if (1 == catch_users().update(query, update, false, false, writeConcern).getN()) {
            return Result.success
        }
        return Result.error
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Catchu
     * @apiName address_del
     * @api {get} catchu/address_del/:access_token/:_id  收货地址删除
     * @apiDescription
     * 收货地址添加
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {String} [_id] 要删除的记录ID
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/catchu/address_del/11f69035f0fdb1d11381407b2b0ed1df/123

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "exec": 293,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def address_del(HttpServletRequest req) {
        def _id = Web.firstParam(req)
        if (!_id) {
            return Result.丢失必需参数
        }
        def userId = Web.currentUserId
        if (1 == catch_users().update($$(_id: userId), $$($pull: [address_list: [_id: _id]])).getN()) {
            return Result.success
        }
        return Result.error
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Catchu
     * @apiName toy_detail
     * @api {get} catchu/toy_detail/:access_token/:_id  商品详情
     * @apiDescription
     * 商品详情
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {String} [_id] 商品ID
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/catchu/toy_detail/11f69035f0fdb1d11381407b2b0ed1df/123

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "data": {
     *         "_id": 123,
     *         "name": "",
     *         "pic": "",
     *         "desc": ""
     *     },
     *     "exec": 293,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def toy_detail(HttpServletRequest req) {
        def toy_id = Web.firstNumber(req)
        def toy = catch_toys()findOne(toy_id, $$(type: 0))
        if (toy == null) {
            return [code: 0]
        }
        return [code: 1, data: toy]
    }
}
