package com.wawa.web.pay

import com.mongodb.DBCollection
import com.mongodb.DBCursor
import com.wawa.api.trade.DelayOrdeInfo
import com.wawa.api.trade.Order
import com.wawa.base.BaseController
import com.wawa.base.anno.Rest
import com.wawa.common.doc.Result
import com.wawa.common.util.BusiExecutor
import com.wawa.common.util.KeyUtils
import com.wawa.model.Finance
import com.wawa.model.Mission
import com.wawa.web.api.MissionController
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.ServletRequestUtils

import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest
import java.util.concurrent.TimeUnit

import static com.wawa.common.util.WebUtils.$$
import static com.wawa.common.doc.MongoKey.$inc

/**
 * 支付回调后订单统一处理接口
 */
@Rest
class PayController extends BaseController {


    public static final Logger logger = LoggerFactory.getLogger(PayController.class)

    @Resource
    MissionController missionController

    DBCollection shop() { adminMongo.getCollection("shop") }

    /**
     * 充值加币
     * @param orderId 订单ID
     * @param cny 充值RMB
     * @param diamond 充值钻石
     * @param via 充值方式
     * @param transactionId 第三方交易流水ID
     * @param feeType 交易货币类型
     * @param remark 交易备注
     * @param ext 额外属性备用(例如:apple receiptData)
     * @return
     */
    public Boolean addDiamond(String orderId, Double cny, Long diamond, String via, String transactionId, String feeType, String remark, Object ext) {
        try {
            def finance = buildFinance(orderId, cny, diamond, via, feeType, transactionId, remark, ext)
            def userId = finance['to_id'] as Integer
            def user = users().findOne($$('_id', userId), $$(Finance.finance$diamond_count, 1))
            //充值成功
            success_pay(userId, finance)
            if (saveFinanceLog(finance)) {
                return Boolean.TRUE
            }
        } catch (Exception e) {
            logger.error("addCoin Exception : {}", e)
            logger.error('pay success,but addCoin error,orderId is {},transactionId is {}', orderId, transactionId)
        }
        return Boolean.FALSE
    }

    /**
     * 构建financeLog日志
     * @param financeMap
     *  orderId: "${userId}+'_'+${toId}+'_'+'timestamp'"
     */
    private Map buildFinance(String orderId, Double cny, Long diamond, String via, String feeType, String transactionId, String remark, Object ext) {
        def now = new Date().getTime()
        def orderArray = orderId.split('_')
        def userId = orderArray[0] as Integer
        def toId = orderArray[1] as Integer
        def rechargeTM = orderArray[2] as Long
        def rechargeCostTM = now - rechargeTM

        def user_query = $$('_id', userId)
        def user = users().findOne(user_query, $$(qd: 1))
        if (user == null) {
            logger.error('user was not found , userId is {}', userId)
            return null
        }
        def qd = user['qd']
        def financeLog = ['_id'    : orderId, 'user_id': userId, 'to_id': toId, 'cny': cny, 'diamond': diamond, 'via': via, ext: ext,
                          'feeType': feeType, 'transactionId': transactionId, 'qd': qd, 'remark': remark, 'c': rechargeCostTM, 'timestamp': now]

        return financeLog
    }

    def test_success_pay(HttpServletRequest req) {
        if (!isTest) {
            return Result.error
        }
        def user_id = ServletRequestUtils.getIntParameter(req, 'user_id')
        def item_id = ServletRequestUtils.getIntParameter(req, 'item_id')
        def award = ServletRequestUtils.getIntParameter(req, 'diamond')
        def ext = ServletRequestUtils.getStringParameter(req, 'ext')
        //模拟对应用户充值
        def financeLog = [_id: "test_${System.currentTimeMillis()}".toString(),
                          user_id: user_id, ext: [award: award, item_id:item_id, ext: ext]]

        //去掉首冲标识
        def first_charge = ServletRequestUtils.getBooleanParameter(req, 'is_first', false)
        if (first_charge) {
            def key = KeyUtils.MISSION.mission_users(Mission.首充100.id)
            if (mainRedis.opsForHash().hasKey(key, '' + user_id)) {
                mainRedis.opsForHash().delete(key, '' + user_id)
            }
        }
        success_pay(user_id, financeLog)
        return Result.success
    }


    void success_pay(Integer userId, Map financeLog) {
        try {
            //把用户获得的奖励加在提醒列表里
            def ext = financeLog['ext'] as Map
            if (ext != null && ext['item_id'] != null) {
                def item = shop().findOne($$(_id: ext['item_id'] as Integer)) as Map
                if (item != null) {
                    missionController.charge_mission(userId, item, financeLog)
                }
            }
        } catch(Exception e) {
            logger.error('error when success_pay process.' + e)
        }
    }

    /**
     * 记录操作日志
     * @param finance_update
     * @param user_update
     */
    private Boolean saveFinanceLog(Map finance) {
        def userId = finance['to_id'] as Integer
        String orderId = finance['_id']
        // 用户的钻石
        def diamond = finance['diamond'] as Long
        if (financeLog().count($$('_id': orderId)) == 0L
                && users().update($$('_id': userId, 'finance_log._id': [$ne: orderId]),
                $$($inc, $$(Finance.finance$diamond_count, diamond))
                        .append('$push', $$('finance_log', finance)), false, false, writeConcern).getN() == 1) {
            financeLog().save($$(finance), writeConcern)

            def query = $$('_id', userId)
            def field = $$('finance':1)
            def update = $$('$pull', $$('finance_log', $$('_id', orderId)))
            def newUser = users().findAndModify(query,field,null,false,update,true,false);
            //订单完成 （防止掉单，漏单）
            Order.completeOrder(orderId);
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    /**
     * 创建订单号
     * @return
     */
    public String createOrder(Integer userId, Integer toId) {
        String orderId = userId + '_' + toId + '_' + new Date().getTime()
        return orderId
    }

    /**
     * 查询延迟订单
     * @param req
     * @return
     */
    def find_delay_order(HttpServletRequest req) {
        String orderId = req['order_id'] as String
        def viaSets = Order.orderProcessList.keySet()
        for (String via : viaSets) {
            logger.debug("find_delay_order {} : {}", orderId, via);
            DelayOrdeInfo orderInfo = Order.findDelayOrder(orderId, via);
            if (orderInfo != null) {
                orderInfo.setVia(via);
                return [code: 1, data: orderInfo];
            }
        }
        return [code: 1, data: null]
    }

    def static final Long MIN_MILLS = 60 * 1000l
    //15分钟 1小时 4小时 8小时 24小时
    def static final List<Long> CHECK_POINTS_MIN1 = [60 * MIN_MILLS, 4 * 60 * MIN_MILLS, 24 * 60 * MIN_MILLS, 4 * 24 * 60 * MIN_MILLS];
    def static final List<Long> CHECK_POINTS_MIN2 = [15 * MIN_MILLS, 30 * MIN_MILLS, 1 * 60 * MIN_MILLS, 4 * 60 * MIN_MILLS, 8 * 60 * MIN_MILLS, 24 * 60 * MIN_MILLS, 3 * 24 * 60 * MIN_MILLS];
    def static final List<List<Long>> SPEED_LEVELS = [CHECK_POINTS_MIN1, CHECK_POINTS_MIN2]
    /**
     * 延迟订单自动补单
     * @param req
     * @return
     */
    def delay_order_fix(HttpServletRequest req) {
        final Integer speed = ServletRequestUtils.getIntParameter(req, 'speed', 1)
        final orderLogs = logMongo.getCollection("order_logs")
        final Long now = System.currentTimeMillis();
        final query = $$(status: 1, checkpoint: [$lte: now])
        Long curr_time = System.currentTimeMillis();
        Long needCheckOrderCount = orderLogs.count(query)
        logger.debug("delay_order_fix  needCheckOrderCount: {}", needCheckOrderCount)
        if (needCheckOrderCount == 0L) return [code: 1, data: [check_order_count: needCheckOrderCount, cost_time: (System.currentTimeMillis() - curr_time)]];
        final def delayRedisKey = "delay:order:fix";
        Boolean isRun = mainRedis.opsForValue().setIfAbsent(delayRedisKey, "1")
        if (isRun) {
            mainRedis.expire(delayRedisKey, 10 * MIN_MILLS, TimeUnit.MILLISECONDS);

            BusiExecutor.execute({
                Long begin = System.currentTimeMillis();
                //获取到达检查点时间的订单
                DBCursor orderList = orderLogs.find(query, $$(via: 1)).batchSize(1000)
                //处理订单 检查订单是否支付完成
                while (orderList.hasNext()) {
                    try {
                        def order = orderList.next();
                        String orderId = order['_id'] as String
                        String via = order['via'] as String
                        DelayOrdeInfo orderInfo = Order.findDelayOrder(orderId, via);
                        if (orderInfo != null) {
                            if (addDiamond(orderInfo.getOrderId(), orderInfo.getCny(), orderInfo.getCoin(), via, orderInfo.getTradeNo(), orderInfo.getAttach(), orderInfo.getAttach(), null)) {
                                logger.debug("delay_order_fix success orderId: {}", orderInfo.getOrderId());
                            }
                        }
                    } catch (Exception e) {
                        logger.error("delay_order_fix Exception : {}", e)
                    }
                }
                //更新订单检查时间
                List<Long> check_points_min = SPEED_LEVELS[speed]
                Integer checkcount = 0;
                while (checkcount < check_points_min.size()) {
                    query = $$(status: 1, checkpoint: [$lte: curr_time], checkcount: checkcount)
                    orderLogs.updateMulti(query, $$($inc: [checkcount: 1, checkpoint: check_points_min[checkcount]]));
                    checkcount++
                }
                mainRedis.delete(delayRedisKey);

                logger.debug("delay_order_fix over cost time : {}", System.currentTimeMillis() - begin);
            } as Runnable)
        }
        [code: 1, data: [check_order_count: needCheckOrderCount, begin2run: isRun, cost_time: (System.currentTimeMillis() - curr_time)]]
    }

}
