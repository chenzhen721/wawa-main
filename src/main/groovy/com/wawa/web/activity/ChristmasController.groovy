package com.wawa.web.activity

import com.mongodb.DBCollection
import com.wawa.AppProperties
import com.wawa.base.anno.RestWithSession
import com.wawa.common.doc.Result
import com.wawa.model.CatchPostChannel
import com.wawa.model.CatchPostStatus
import com.wawa.model.CatchPostType
import com.wawa.base.BaseController
import com.wawa.api.Web
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.ServletRequestUtils

import javax.servlet.http.HttpServletRequest
import java.text.SimpleDateFormat

import static com.wawa.common.util.WebUtils.$$

/**
 * 圣诞节活动
 * 12月23日00：00     到    12月30日    23：59
 */
@RestWithSession
class ChristmasController extends BaseController {

    private Logger logger = LoggerFactory.getLogger(ChristmasController.class)

    //活动时间
    private static final boolean isTest = AppProperties.get("api.domain").contains("test-");
    private static final Long _begin = new SimpleDateFormat("yyyyMMdd").parse(System.getProperty("mfbegin", "20171223")).getTime()
    private static final Long _end = new SimpleDateFormat("yyyyMMdd").parse("20171231").getTime()
    private static final String ACTIVE_NAME = "christmas"
    DBCollection apply_post_logs() {
        return logMongo.getCollection('apply_post_logs')
    }
    DBCollection catch_success_logs() {
        return logMongo.getCollection('catch_success_logs')
    }
    DBCollection catch_toys() {
        return catchMongo.getCollection('catch_toy')
    }
    DBCollection catch_users() {
        return catchMongo.getCollection('catch_user')
    }

    //兑换商品ID:[组合娃娃ID1，组合娃娃ID2]
    private static final Map<Integer, List<Integer>> DOLL_GROUPS = [
            12863 : [100014, 12850], //充值卡
            12862 : [100053, 12855], //抱枕
            12861 : [100045, 12853], //手袋
            12860 : [12848, 12857], //单肩包
            12858 : [12849, 12851, 12852], //纸巾盒
            12859 : [12848, 12853, 12852], //双肩包
    ]

    protected static Boolean isPeriod(){
        if (isTest) {
            return true
        }
        Long now = System.currentTimeMillis()  ;
        if (now < _begin || now > _end)
            return false
        return true
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Activity
     * @apiName christmas_list
     * @api {get} christmas/list/:access_token/:_id  圣诞活动列表
     * @apiDescription
     * 圣诞活动列表
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {String} [_id] 商品ID
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/christmas/list/11f69035f0fdb1d11381407b2b0ed1df/123

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "data": {
     *         "bags": {
     *             "12580": 0,
     *             "100053": 0
     *         },
     *         "groups": {
     *             "12858": [
     *                 12849,
     *                 12851,
     *                 12852
     *             ]
     *         }
     *     },
     *     "exec": 9555,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def list(HttpServletRequest req){
        /*if(!isPeriod())
            return [code: 0, msg: "活动已经结束!"]*/

        Integer userId = Web.getCurrentUserId()
        def bags = new HashMap()
        def toys = new HashMap()
        def expire_time = new Date().clearTime().getTime() - 9 * DAY_MILLON
        //def tids = []
        DOLL_GROUPS.each { Integer id, List<Integer> dolls ->
            def count = apply_post_logs().count($$(user_id: userId, 'toys._id': id, channel: CatchPostChannel.活动人工.ordinal(), status: [$ne: CatchPostStatus.审核失败.ordinal()], is_delete: [$ne: true]))
            bags[id] = count >= 0 ? count: 0 //已兑换的数量
            //tids.add(id)
            for(Integer dollId : dolls) {
                def query = $$(user_id: userId, 'toy._id': dollId,  timestamp: [$gte: expire_time], is_award: [$ne: true], post_type: CatchPostType.未处理.ordinal(), is_delete: [$ne: true])
                def logs = catch_success_logs().find(query, $$(_id: 1)).toArray()
                bags[dollId] = logs?.size() ?: 0 //查询用户剩余娃娃数量
                //tids.add(dollId)
            }
        }
        [code: 1, data: [bags : bags, groups : DOLL_GROUPS]]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Activity
     * @apiName christmas_exchange
     * @api {get} christmas/exchange/:access_token?product_id=:_id  圣诞活动-兑换礼物
     * @apiDescription
     * 圣诞活动-兑换礼物
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {String} [product_id] 商品ID
     * @apiParam {String} [remark] 若为充值卡，填写充值的手机号
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/christmas/exchange/11f69035f0fdb1d11381407b2b0ed1df/123

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "exec": 9555,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def exchange(HttpServletRequest req){
        def time = System.currentTimeMillis()
        if (time < _begin) {
            return Result.活动未开始
        }
        if (time > _end) {
            return Result.活动已结束
        }

        Integer userId = Web.getCurrentUserId()
        def product_id = req.getParameter("product_id") as Integer
        def remark = ServletRequestUtils.getStringParameter(req, 'remark', '')
        List<Integer> dolls = DOLL_GROUPS[product_id] as List
        if(dolls == null) {
            return Result.丢失必需参数
        }
        def expire_time = new Date().clearTime().getTime() - 9 * DAY_MILLON
        //查询对应的记录
        def ids = []
        for(Integer id: dolls) {
            def query = $$('toy._id': id, user_id: userId, timestamp: [$gte: expire_time], is_award: [$ne: true],
                    post_type: CatchPostType.未处理.ordinal(), is_delete: [$ne: true])
            def record = catch_success_logs().find(query).sort($$(timestamp: 1)).limit(1).toArray()
            if (record == null || record.size() <= 0) {
                return Result.商品数量不足
            }
            ids.add(record[0]['_id'] as String)
        }

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
        def addressstr = "${address['province'] ?: ''}${address['city'] ?: ''}${address['region'] ?: ''}${address['address']}".toString()

        if (catch_success_logs().count($$(_id: [$in: ids], pack_id: [$exists: true])) > 0) {
            return [code: 0] //有不符合条件的记录
        }

        if (apply_post_logs().count($$('record_ids': [$in: ids], is_delete: [$ne: true])) > 0) {
            return [code: 0] //有已申请记录
        }

        //打包记录 改成后台发货
        def timeStr = new Date().format('yyMMddHHmmss')
        def pack_id = "${timeStr}${userId}".toString()
        //def logs = catch_success_logs().find($$(_id: [$in: ids]), $$(room_id: 1, toy: 1, goods_id: 1))
        def toys = []
        toys.add(catch_toys().findOne($$(_id: product_id)))
        /*logs.each { BasicDBObject obj ->
            def toy = obj['toy'] as Map
            toy.put('room_id', obj['room_id'])
            toy.put('record_id', obj['_id'])
            toys.add(obj['toy'])
        }*/
        def logWithId = $$([_id      : pack_id, user_id: userId, record_ids: ids, toys: toys,
                                                                timestamp: time, post_type: CatchPostType.待发货.ordinal(),
                                                                status   : CatchPostStatus.未审核.ordinal(), address: address, address_list: addressstr,
                                                                is_delete: false, channel: CatchPostChannel.活动人工.ordinal(), remark: remark])//, order_id: order_id

        if (1 <= catch_success_logs().update($$(_id: [$in: ids], pack_id: [$exists: false], is_delete: [$ne: true]),
                $$($set: [pack_id: pack_id, post_type: CatchPostType.待发货.ordinal(), apply_time: time, is_delete: false]), false, true, writeConcern).getN()) {
            apply_post_logs().save(logWithId, writeConcern)
            return Result.success
        }
        return Result.商品数量不足
    }

}
