package com.wawa.web.partner

import com.mongodb.BasicDBObject
import com.mongodb.DBCollection
import com.mongodb.DBObject
import com.wawa.AppProperties
import com.wawa.base.BaseController
import com.wawa.base.Crud
import com.wawa.base.anno.Rest
import com.wawa.common.util.JSONUtil
import com.wawa.common.doc.Result
import com.wawa.common.util.DelayQueueRedis
import com.wawa.common.util.KeyUtils
import com.wawa.common.util.RandomExtUtils
import com.wawa.model.CatchMsgType
import com.wawa.model.CatchPostChannel
import com.wawa.model.CatchPostType
import com.wawa.model.CatchRecordType
import com.wawa.model.UserAwardType
import com.wawa.api.Web
import com.wawa.api.notify.RoomMsgPublish
import com.wawa.api.play.Qiyiguo
import com.wawa.api.play.dto.QiygOperateResultDTO
import com.wawa.web.api.UserController
import groovy.json.JsonSlurper
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.CollectionUtils
import org.springframework.web.bind.ServletRequestUtils

import javax.annotation.PostConstruct
import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest

import static com.wawa.api.Web.adminMongo
import static com.wawa.api.Web.isTest
import static com.wawa.api.Web.mainRedis

import static com.wawa.common.util.WebUtils.$$
import static com.wawa.common.doc.MongoKey.*

/**
 * 抓娃娃
 */
@Rest
class QiyiguoPublicController extends BaseController {

    static final Logger logger = LoggerFactory.getLogger(QiyiguoPublicController.class)

    private static final String GIF_DOMAIN = AppProperties.get("gif.domain", "http://test-record.17laihou.com/")
    public static final String APP_ID = isTest ? "984069e5f8edd8ca4411e81863371f16" : "984069e5f8edd8ca4411e81863371f16"
    public static final JsonSlurper jsonSlurper = new JsonSlurper()
    public static final int MIN_ROOM_COUNT = isTest ? 2 : 6

    @Resource
    private QiyiguoController qiyiguoController
    @Resource
    private UserController userController

    DBCollection catch_rooms() {
        return catchMongo.getCollection('catch_room')
    }
    DBCollection catch_toys() {
        return catchMongo.getCollection('catch_toy')
    }
    DBCollection catch_records() {
        return catchMongo.getCollection('catch_record')
    }
    DBCollection catch_success_logs() {
        return logMongo.getCollection('catch_success_logs')
    }
    DBCollection catch_post_logs() {
        return logMongo.getCollection('apply_post_logs')
    }
    DBCollection goods() {
        return adminMongo.getCollection('goods')
    }
    DBCollection category(){return adminMongo.getCollection('category')}

    private static int  max_check_count = 100 // 最大校验次数
    private static long semaphore = 0 //型号量
    private static final long delay = 5000L // 型号量


    @PostConstruct
    public void init2() {
        final String gif_domain = GIF_DOMAIN
        //游戏结束通知倒计时
        qiyiguoController.createNotifyQueue(qiyiguoController.result_notify_queue, new DelayQueueRedis.DelayQueueJobListener(){
            @Override
            void doJob(DelayQueueRedis.Task task) {
                //task的ID为房间id desc是房间和用户信息
                def desc = task.getDesc()
                if (StringUtils.isBlank(desc)) {
                    return
                }
                Map obj = JSONUtil.jsonToMap(desc)
                def room_id = obj['room_id']
                def user_id = obj['user_id'] as Integer
                def record_id = obj['record_id'] as String
                logger.debug('>>>>>>>>>>>>>>>>>>>>>>>>>result notify queue,' + JSONUtil.beanToJson(task))
                qiyiguoController.finish_notify(room_id, user_id, record_id)
            }
        })
        //游戏时间到期
        qiyiguoController.createNotifyQueue(qiyiguoController.room_finish_queue, new DelayQueueRedis.DelayQueueJobListener(){
            @Override
            void doJob(DelayQueueRedis.Task task) {
                //task的ID为房间id desc是房间和用户信息
                //这个地方很复杂
                logger.info('=========================> task game time out:' + task.getDesc())
                // 查询是否已发送通知，如果未发送通知 WTF!!!
                //task的ID为房间id desc是房间和用户信息
                def desc = task.getDesc()
                if (StringUtils.isBlank(desc)) {
                    return
                }
                Map obj = JSONUtil.jsonToMap(desc)
                def room_id = obj['room_id'] as Integer
                def user_id = obj['user_id'] as Integer
                def record_id = obj['record_id'] as String
                //step.1 查询当前房间record是否为同一个，如果不是同一个直接忽略
                def player_info = QiyiguoController.Room.getPlayerInfo(mainRedis, room_id)
                def user_room_id = QiyiguoController.Room.getPlayerRoomId(mainRedis, user_id)
                if ((player_info == null && user_room_id != room_id) || player_info[QiyiguoController.Room.finish] == '1' || player_info[QiyiguoController.Room.record_id] != record_id) {
                    //已进入正常流程无需额外处理
                    logger.debug('======success process callback, drop.')
                    return
                }
                //如果为同一个人，判断游戏结束到现在的时间是否进入异常时间
                def current = System.currentTimeMillis()
                //记录异常， 模拟调取callback
                def records = catch_records().findOne($$(_id: record_id))
                if (records == null) {
                    logger.info('===============room_finish_queue not found record:' + record_id)
                    return
                }
                def log_id = records['log_id'] as String
                def device_id = records['fid'] as String

                QiygOperateResultDTO operateResultDTO = Qiyiguo.operateResult(log_id)
                logger.debug('>>>>>>>>>>>>>>>>>>>operate log_id: ' + log_id + ', result:' + JSONUtil.beanToJson(operateResultDTO))
                def result = ''
                Integer goods_id = records['goods_id'] as Integer
                if (goods_id == null) {
                    goods_id =  getGoodsId(records['gid'] as Integer) ?: null
                }
                def params = [platform: Qiyiguo.PLATFORM, user_id: user_id, log_id: log_id, device_id: device_id, goods_id: goods_id, ts: '' + current, address: '', consignee: '', mobile: '']
                if (goods_id != null && operateResultDTO != null && operateResultDTO.getOperate_result() != null) {
                    params.put('operate_result', operateResultDTO.getOperate_result())
                    result = result_callback(Qiyiguo.PLATFORM, user_id, log_id, device_id, operateResultDTO.getOperate_result(), goods_id, createSign(params), '' + current, '', '', '')
                } else {
                    if (1 == catch_records().update($$(_id: record_id, type: CatchRecordType.扣费.ordinal()), $$($set: [need_attention: true]), false, false, writeConcern).getN()) {
                        params.put('operate_result', 2)
                        result = result_callback(Qiyiguo.PLATFORM, user_id, log_id, device_id, 2, goods_id, createSign(params), '' + current, '', '', '')
                    }
                }
                logger.debug('>>>>>>>>>>>>>>>>>>>>>>>>>>result_callback:' + result)
            }
        })
        //初始化延迟队列
        final DelayQueueRedis refreshRoomQueue = DelayQueueRedis.generateQueue("refresh:room:status");
        //创建任务
        final DelayQueueRedis.Task task = new DelayQueueRedis.Task('refresh_room_list', delay, "刷新娃娃机状态任务")
        //将任务加入延迟队列中
        refreshRoomQueue.offer(task);
        //注册延迟任务callback
        refreshRoomQueue.addListner(new DelayQueueRedis.DelayQueueJobListener(){

            public void doJob(DelayQueueRedis.Task doTask){
                //logger.debug(task.toString() + " 转出延迟队列>>>" + DateUtil.getFormatDate(DateUtil.DFMT, System.currentTimeMillis()));
                //刷新机器房间信息
                refreshRoomList();
                //启动下一轮
                refreshRoomQueue.offer(task);
            }

            private void refreshRoomList(){
                try {
                    //todo 查询结果信息
                    /*if (StringUtils.isNotBlank(objStr)) {
                        def catch_map = jsonSlurper.parseText(objStr) as Map

                        //只推送备货中的机器信息
                        map.each {String id, Map obj ->
                            def catchObj = catch_map.get(id)
                            if (catchObj != null) {
                                if ((obj['room_online'] != catchObj['room_online'] && !(obj['room_online'] as Boolean))) {
                                    //todo 房间下架
                                    RoomMsgPublish.publish2Room(obj['room_id'] as Integer, MsgAction.抓娃娃, [type: CatchMsgType.房间下架.ordinal()], false)
                                }
                                if ((obj['room_type'] != catchObj['room_type'])){
                                    //|| obj['status'] != catchObj['status'] && room_status(obj['status'] as String) == 2) {
                                    if ((obj['room_type'] as Boolean)) {//重启
                                        RoomMsgPublish.publish2Room(obj['room_id'] as Integer, MsgAction.抓娃娃, [type: CatchMsgType.房间开放.ordinal()], false)
                                    } else {//todo 备货中
                                        RoomMsgPublish.publish2Room(obj['room_id'] as Integer, MsgAction.抓娃娃, [type: CatchMsgType.备货中.ordinal()], false)
                                    }
                                }
                            }
                        }
                    }*/

                    //游戏结束状态补偿
                    def type_list = [CatchRecordType.结束.ordinal(), CatchRecordType.无效记录.ordinal(), CatchRecordType.人工干预.ordinal()]
                    def query = $$(type: [$nin: type_list], timestamp: [$gte: new Date().clearTime().getTime(), $lte: System.currentTimeMillis() - 300000L])
                    def list = catch_records().find(query).sort($$(n: 1)).limit(2).toArray()
                    for(DBObject obj : list) {
                        logger.debug('==============record id:' + obj['_id'])
                        QiygOperateResultDTO operateResultDTO = Qiyiguo.operateResult(obj['log_id'] as String)
                        def record = [:]
                        def inc = [:]
                        if (obj['replay_url'] == null) {
                            def replay_url = "${gif_domain}${new Date(obj['timestamp'] as Long).format('yyyyMMdd')}/${obj['room_id']}/${obj['_id']}.gif".toString()
                            record.put('replay_url', replay_url)
                            obj['replay_url'] = replay_url
                        }
                        if (operateResultDTO != null) {
                            def result = operateResultDTO.getOperate_result() == 1
                            if (operateResultDTO.getOperate_result() == 0) {
                                //没有操作
                                record.put('type', CatchRecordType.人工干预.ordinal())
                            } else {
                                record.put('type', CatchRecordType.结束.ordinal())
                            }
                            record.put('status', result)
                            record.put('play_record', JSONUtil.beanToJson(operateResultDTO))
                        } else {
                            if ((obj['n'] as Integer) + 1 >= max_check_count) {
                                record.put('type', CatchRecordType.人工干预.ordinal())
                            }
                        }
                        inc.put('n', 1)
                        //推送消息
                        def update = new BasicDBObject()
                        if (!record.isEmpty()) {
                            update.put('$set', record)
                        }
                        update.put($inc, ['n': 1])
                        if (1 == catch_records().update($$(_id: obj['_id'], type: [$ne: CatchRecordType.结束.ordinal()]), update, false, false, writeConcern).getN()) {
                            if (record['play_record'] != null) {
                                awardPoint(obj['user_id'] as Integer, record['status'] as Boolean)
                                if ((record['status'] as Boolean) ?: Boolean.FALSE) {
                                    saveSuccessRecord(obj)
                                }
                            }
                            qiyiguoController.deleteIfNE(obj['room_id'] as Integer, obj['_id'] as String, obj['user_id'] as Integer)
                        }
                    }
                } catch (Exception e) {
                    logger.error('=========loop error: {}', e)
                }

            }
        })
    }

    private Integer getGoodsId(Integer goods_id) {
        //列表页缓存
        def goods = goods().findOne($$(_id: goods_id))
        if (goods == null) {
            return null
        }
        def toy_id = goods['toy_id']
        def toy = catch_toys().findOne($$(_id: toy_id))
        if (toy == null || toy['goods_id'] == null) {
            return null
        }
        return toy['goods_id'] as Integer
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup CatchuPublic
     * @apiName room_list
     * @api {get} catchupublic/room_list?type=:type&page=:page&size=:size  获取房间列表
     * @apiDescription
     * 获取娃娃房详细信息
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Integer} [type]  查询类型 0或不传房间列表 1查询收藏列表
     * @apiParam {Integer} [page]  页号
     * @apiParam {Integer} [size]  个数
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/catchupublic/room_list
     *
     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "count": 1,
     *     "data": [
     *         {
     *             "_id": 10000016,
     *             "fid": 1005,
     *             "name": "wawa1",
     *             "type": true,  //true-正常游戏 false-备货中
     *             "pic": "http://www.baidu.com",
     *             "price": 300,
     *             "desc": "描述",
     *             "status": 0, 0-空闲 1-游戏中 2-准备中
     *             "timestamp": 1508232023761,
     *             "pull_url_1": "rtmp://l.ws.sumeme.com/meme/10000016?k=bda3453af8f7bf3c3d69d327f6b31557&t=59e5d260_1",
     *             "pull_url_2": "rtmp://l.ws.sumeme.com/meme/10000016?k=bda3453af8f7bf3c3d69d327f6b31557&t=59e5d260_2",
     *             "toy_id": 123
     *         }
     *     ],
     *     "exec": 11,
     *     "code": 1,
     *     "all_page": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def room_list(HttpServletRequest req) {
        //列表页状态缓存
        def type = ServletRequestUtils.getIntParameter(req, 'type', 0)
        def size = ServletRequestUtils.getIntParameter(req, 'size', 0)
        def query = $$(online: true)
        //获取收藏状态
        if (type == 1) {
            def obj = Web.getUserByAccessToken(req)

            if (obj == null || obj['_id'] == null) {
                return [code: 1, data: [], "all_page": 1, "count": 1]
            }
            def userId = obj['_id'] as Integer
            List<Integer> ids = Web.getFollowing(userId)
            if (CollectionUtils.isEmpty(ids)) {
                return [code: 1, data: [], "all_page": 1, "count": 1]
            }
            query.put('_id', [$in: ids])
        }
        def order = $$(order: 1, timestamp: -1)
        //推荐列表
        def data = [code: 1, data: []]
        if (type == 2) {
            List list = goods().find(query, ALL_FIELD).sort(order).skip(MIN_ROOM_COUNT).limit(100).toArray() as List<BasicDBObject>
            if (list.size() > MIN_ROOM_COUNT) {
                Collections.shuffle(list)
                if (isEasyMode(req)) {
                    sort(list)
                }
                data = [code: 1, data: list.subList(0,size)]
            }
        } else {
            def cate_id = ServletRequestUtils.getIntParameter(req, 'cate_id')
            if (cate_id != null) {
                query.put('cate_id', cate_id)
            }
            data = Crud.list(req, goods(), query, ALL_FIELD, order)
        }
        def room_map = room_map(data)
        def list = room_map['list'] as List
        if (type == 2 && list?.size() < 2) {
            data['data'] = []
            return data
        }
        if (isEasyMode(req)) {
            sort(list)
        }
        def offline = room_map['offline'] as List
        if (!offline.isEmpty()) {
            list.addAll(offline)
        }
        data['data'] = list
        return data
    }

    private Map recomm_result(List<BasicDBObject> list) {
        if (list.size() <= MIN_ROOM_COUNT) {
            return [code: 1, data: []]
        }
        def recom_list = []
        2.times {
            int index = RandomExtUtils.randomBetweenMinAndMax(2, list.size() - 1)
            def obj = list.remove(index)
            recom_list.add(obj)
        }
        return [code: 1, data: recom_list]
    }

    private static final Map<Integer, Integer> cate_map = new HashMap()

    private Map room_map(Map data) {
        if (data['code'] != 1 || data['data'] == null) {
            return [list: [], offline: []]
        }
        def list = data['data'] as List<Map>
        def rids = []
        list.each { Map map-> rids.add(map['room_id'] as Integer) }
        def map = getStatusByRoomIds(rids)
        def iter = list.iterator()
        def offline = []
        while(iter.hasNext()) {
            def obj = iter.next() as BasicDBObject
            obj['status'] = map.get(obj['room_id'])
            def tag_id = obj['tag_id'] as Integer
            def cate = cate_map.get(tag_id)
            if (cate == null) {
                cate = category().findOne($$(_id: tag_id), $$(img: 1))
            }
            if (cate != null) {
                obj['tag_img'] = cate['img']
            }
            def toy = catch_toys().findOne($$(_id: obj['toy_id'] as Integer))
            if (toy != null) {
                obj['pic'] = toy['head_pic'] as String
                obj['price'] = toy['price'] as Integer
            }
            if (!obj['type']) {
                iter.remove()
                offline.add(obj)
            }
        }
        return [list: list, offline: offline]
    }

    private Map getStatusByRoomIds(List<Integer> roomIds) {
        def result = [:]
        roomIds.each { Integer room_id->
            result.put(room_id, getRoomStatus(room_id))
        }
        return result
    }

    public int getRoomStatus(Integer roomId) {
        def status = 0
        def playerinfo = QiyiguoController.Room.getPlayerInfo(mainRedis, roomId)
        if (playerinfo != null) {
            status = 1
        }
        return status
    }

    private boolean isEasyMode(HttpServletRequest req) {
        def obj = Web.getUserByAccessToken(req)
        if (obj == null || obj['_id'] == null) {
            return false
        }
        def userId = obj['_id'] as Integer
        String key = KeyUtils.USER.first_doll(userId)
        if (mainRedis.hasKey(key)) {
            return true
        }
        return false
    }

    private void sort(List list) {
        Collections.sort(list, new Comparator<Map>() {
            @Override
            int compare(Map o1, Map o2) {
                return (o2['partner'] as Integer) - (o1['partner'] as Integer)
            }
        })
    }

    /**
     * log_id  第三方游戏记录
     * operate_result 1成功 2失败
     * @param req
     */
    def callback(HttpServletRequest req) {
        def platform = ServletRequestUtils.getStringParameter(req, 'platform', Qiyiguo.PLATFORM)
        def user_id = ServletRequestUtils.getIntParameter(req, 'user_id')
        def log_id = ServletRequestUtils.getStringParameter(req, 'log_id')
        def device_id = ServletRequestUtils.getStringParameter(req, 'device_id')
        def address = ServletRequestUtils.getStringParameter(req, 'address', '')
        def consignee = ServletRequestUtils.getStringParameter(req, 'consignee', '')
        def mobile = ServletRequestUtils.getStringParameter(req, 'mobile', '')
        def operate_result = ServletRequestUtils.getIntParameter(req, 'operate_result')
        def goods_id = ServletRequestUtils.getIntParameter(req, 'goods_id')
        def remoteSign = ServletRequestUtils.getStringParameter(req, 'sign')
        def ts = ServletRequestUtils.getStringParameter(req, 'ts')
        logger.info('===============receive callback msg from remote: ' + req.parameterMap)
        return result_callback(platform, user_id, log_id, device_id, operate_result, goods_id, remoteSign, ts, address, consignee, mobile)
    }

    def result_callback(String platform, Integer user_id, String log_id, String device_id, Integer operate_result, Integer goods_id, String remoteSign, String ts, String address, String consignee, String mobile) {
        def params = [platform: platform, user_id: user_id, log_id: log_id, device_id: device_id, address: address, consignee: consignee, mobile: mobile, operate_result: operate_result, goods_id: goods_id, ts: ts]
        def localSign = createSign(params)
        logger.error('===============receive qiyiguo callback. local sign: ' + localSign + ' remote:' + remoteSign)
        /*if (localSign != remoteSign) {
            logger.error('===============receive qiyiguo callback error. local sign: ' + localSign + ' missmatch from:' + remoteSign)
            return [code: 0]
        }*/

        //查询是否有此订单，记录log;若无则记录异常并返回
        def records = catch_records().findOne($$(user_id: user_id, log_id: log_id))
        logger.info('===============receive callback records:' + records)
        if (records == null) {
            return [code: 0]
        }
        if (isTest) {//todo 测试用
            operate_result = 1
        }
        def data = operate_result == 1 ? Boolean.TRUE.toString() : Boolean.FALSE.toString()
        def record = $$(log_id: log_id, operate_result: operate_result, timestamp: System.currentTimeMillis())
        //抓取结果(最终的结果)
        def status = Boolean.parseBoolean(data)
        def replay_url = "${GIF_DOMAIN}${new Date().format('yyyyMMdd')}/${records['room_id']}/${records['_id']}.gif".toString()
        def n = catch_records().update($$(_id: records['_id'], type: [$ne: CatchRecordType.结束.ordinal()]),
                $$($set: [type: 2, status: status, play_record: record, replay_url: replay_url, goods_id: goods_id]), false, false, writeConcern).getN()
        //logger.info("callback update ${log_id}, ${user_id}".toString())
        def award_points = records['award_points'] as Boolean ?: false //是否奖励抓取积分
        //给用户送积分
        def points = null
        if (award_points) {
            points = awardPoint(user_id, status)
        }

        // 推送房间消息 是否首次抓到
        def is_first = Boolean.FALSE
        if (1 == n) {
            def user = users().findOne(user_id, $$(nick_name: 1, pic: 1))
            def toy = records['toy'] as Map
            def name = toy != null ? (toy['name'] ?: '') : ''
            if (status) {
                is_first = isFirst(user_id)
                records.put('replay_url', replay_url)
                records.put('goods_id', goods_id)
                saveSuccessRecord(records)
                // 抓中后全房间发送提示
                RoomMsgPublish.publish2GlobalEvent([type: CatchMsgType.抓中全站提示.ordinal(), toy_name: name, user: user, device_type: records['device_type']])
            }
            //此时游戏已经结束
            def roomId = records['room_id'] as Integer
            def obj = [type: CatchMsgType.继续游戏.ordinal(), status: status, toy_name: name, record_id: records['_id'], result: log_id, is_first: is_first, user: user]
            if (points != null) {
                obj.put('points', points)
            }
            qiyiguoController.continue_notify(roomId, user_id, records['_id'] as String, obj)
            //兜底 延迟6.5秒后发送结束消息 desc需要改
            qiyiguoController.addTask(qiyiguoController.result_notify_queue, records['room_id'] as Integer, user_id, [record_id: records['_id']], 6500L)
        }
        RoomMsgPublish.roomVideoPause(records['room_id'], records['_id'])
        //RoomMsgPublish.roomVideoDispatchPause(records['room_id'], records['_id'])

        // 抓必中调整
        //unlimit_check(user_id, records['cost'] as Long, status)
        return [code: 1]
    }

    def saveSuccessRecord(def records) {
        try {
            def success_log = $$(_id: records['_id'],
                    room_id: records['room_id'],
                    user_id: records['user_id'],
                    toy: records['toy'],
                    post_type: CatchPostType.未处理.ordinal(),
                    coin: records['coin'],
                    timestamp: records['timestamp'],
                    replay_url: records['replay_url'],
                    goods_id: records['goods_id'],
                    channel: records['channel'] ?: CatchPostChannel.奇异果.ordinal(),
                    is_delete: false,
                    is_award: false,
                    toy_points: records['toy_points']
            )
            catch_success_logs().save(success_log, writeConcern)
        } catch (Exception e) {
            logger.error("save success record error.records: ${records}", e)
        }
    }

    def awardPoint(final Integer user_id, final Boolean status) {
        /*String key = KeyUtils.USER.unlimit(user_id)
        //已开启 无限抓
        if ('1' == mainRedis.opsForHash().get(key, 'unlimit_flag')) {
            return 0
        }*/
        def points
        try {
            if (status) {
                points = RandomExtUtils.randomBetweenMinAndMax(10, 20)
            } else {
                points = RandomExtUtils.randomBetweenMinAndMax(20, 30)
            }
            if (1 == users().update($$(_id: user_id), $$($inc: $$('bag.points.count', points)), false, false).getN()) {
                Web.saveAwardLog(user_id, UserAwardType.抓娃娃送积分, [points: points])
                return points
            }
        } catch (Exception e) {
            logger.error('award point error. user_id: ' + user_id + ', status:' + status)
        }
        return 0
    }

    public boolean isFirst(Object uid) {
        if (mainRedis.hasKey(KeyUtils.USER.first(uid)) && '1' == mainRedis.opsForValue().get(KeyUtils.USER.first(uid))) {
            mainRedis.delete(KeyUtils.USER.first(uid))
            return Boolean.TRUE
        }
        return Boolean.FALSE
    }

    public Boolean unlimit_check(Integer userId, Long cost, Boolean status) {
        String key = KeyUtils.USER.unlimit(userId)
        if (!mainRedis.hasKey(key)) {
            return null
        }
        //成功后删除,是否提示用户
        if (status) {
            mainRedis.delete(key)
            return true
        }
        //已开启
        if ('1' == mainRedis.opsForHash().get(key, 'unlimit_flag')) {
            return true
        }
        //未开启
        if (mainRedis.hasKey(key) && mainRedis.opsForHash().increment(key, 'unlimit_diamond_cost', -cost) <= 0) {
            mainRedis.opsForHash().put(key, 'unlimit_flag', '1')
            mainRedis.opsForHash().put(key, 'unlimit_home_notify', '1')
        }
        return false
    }

    String createSign(Map param) {
        SortedMap<String, Object> params = new TreeMap<>()
        params.put("platform", param.get('platform'))
        params.put("user_id", param.get('user_id'))
        params.put("log_id", param.get('log_id'))
        params.put("device_id", param.get('device_id'))
        params.put("address", param.get('address'))
        params.put("consignee", param.get('consignee'))
        params.put("mobile", param.get('mobile'))
        params.put("operate_result", param.get('operate_result'))
        params.put("goods_id", param.get('goods_id'))
        params.put("ts", param.get('ts'))
        return Qiyiguo.creatSign(params)
    }

    /**
     *
     *    order_id	订单ID	如：7432
     *    status	订单状态（30:已发货）	如：30
     *    mode	物流方式 EXPRESS  使用物流订单 NO_EXPRESS 无需物流	如：EXPRESS
     *    shipping_no	物流单号	如：420679234230
     *    shipping_com	物流公司标识，用于快递100查询单号的参数	如：shunfeng
     *    shipping_name	物流公司名字	如：“顺丰”
     *    shipping_memo	发货人备注	如：“已发货”
     *    shipping_time	发货时间	如：1511079568
     *
     */
    def order_callback(HttpServletRequest req) {
        def is_edit = ServletRequestUtils.getStringParameter(req, 'is_edit', '')
        def order_id = ServletRequestUtils.getStringParameter(req, 'order_id')
        def status = ServletRequestUtils.getStringParameter(req, 'status', '')
        def mode = ServletRequestUtils.getStringParameter(req, 'mode', '')
        def shipping_no = ServletRequestUtils.getStringParameter(req, 'shipping_no', '')
        def shipping_com = ServletRequestUtils.getStringParameter(req, 'shipping_com', '')
        def shipping_name = ServletRequestUtils.getStringParameter(req, 'shipping_name', '')
        def shipping_memo = ServletRequestUtils.getStringParameter(req, 'shipping_memo', '')
        def shipping_time = ServletRequestUtils.getStringParameter(req, 'shipping_time', '')
        def ts = ServletRequestUtils.getIntParameter(req, 'ts')
        def remoteSign = ServletRequestUtils.getStringParameter(req, 'sign')
        logger.info('===============receive order_callback msg from remote: ' + req.parameterMap)
        SortedMap<String, Object> params = new TreeMap<>()
        params.put("platform", Qiyiguo.PLATFORM)
        params.put("is_edit", is_edit)
        params.put("order_id", order_id)
        params.put("status", status)
        params.put("mode", mode)
        params.put("shipping_no", shipping_no)
        params.put("shipping_com", shipping_com)
        params.put("shipping_name", shipping_name)
        params.put("shipping_memo", shipping_memo)
        params.put("shipping_time", shipping_time)
        params.put("timestamp", System.currentTimeMillis())
        params.put("ts", ts)
        def localSign = Qiyiguo.creatSign(params)
        logger.info('===============receive qiyiguo order_callback. local sign: ' + localSign + ' remote from:' + remoteSign)
        /*if (localSign != remoteSign) {
            logger.info('===============receive qiyiguo order_callback error. local sign: ' + localSign + ' missmatch from:' + remoteSign)
            return Result.error
        }*/
        //查询是否有此订单
        def post_log = catch_post_logs().findOne($$(order_id: order_id))
        if (post_log == null) {
            logger.error('none order_id found:' + req.getParameterMap())
            return Result.error
        }
        if (1 == catch_post_logs().update($$(_id: post_log['_id'], post_type: CatchPostType.已发货.ordinal()),
                $$($set: [post_info: params, post_type: CatchPostType.已同步订单.ordinal()]), false, false, writeConcern).getN()) {
            return Result.success
        }
        return Result.error
    }
}
