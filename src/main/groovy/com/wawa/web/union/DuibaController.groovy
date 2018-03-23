package com.wawa.web.union

import com.mongodb.DBCollection
import com.wawa.api.Web
import com.wawa.base.BaseController
import com.wawa.base.Crud
import com.wawa.base.anno.Rest
import com.wawa.common.doc.TwoTableCommit
import com.wawa.common.util.JSONUtil
import com.wawa.common.util.MsgDigestUtil
import com.wawa.model.UserAwardType
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.ServletRequestUtils

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static com.wawa.common.util.WebUtils.$$
import static com.wawa.common.doc.MongoKey.*

/**
 * 兑吧积分商城
 * Created by Administrator on 2017/11/15.
 */
@Rest
class DuibaController extends BaseController{

    static final Logger logger = LoggerFactory.getLogger(DuibaController.class)

    static final String APPKEY = isTest ? "2cMiWojjzn34FpfZy448ENrAPb1j" : "3nfpgtYpX7xKE6JLune7Q5CHSutP"
    static final String APPSECRET = isTest ? "yDuDgHAbNNhhy5NsAi87iRs6fGc" : "3nHisYGVKjXYfyQNHZg1LzQu1mkY"
    static final String REDIRECT_HOST = isTest ? "http://www.duiba.com.cn/autoLogin/autologin" : "http://www.duiba.com.cn/autoLogin/autologin"

    DBCollection duiba_points_logs() {
        return logMongo.getCollection('duiba_points_logs')
    }

    /**
     * 登录接口
     */
    def login(HttpServletRequest req, HttpServletResponse resp) {
        logger.debug('login.' + req.getParameterMap())
        def userCache = Web.getUserByAccessToken(req)
        def uid = userCache['_id']
        def credits = 0
        if (uid == null) {
            uid = "not_login"
        } else {
            def user = users().findOne($$(_id: Integer.parseInt('' + uid)))
            if (user != null && user['bag'] != null && user['bag']['points'] != null && user['bag']['points']['count'] != null) {
                credits = user['bag']['points']['count'] as Integer
            }
        }
        def redirect = ServletRequestUtils.getStringParameter(req, 'redirect', "")
        def timestamp = System.currentTimeMillis()
        SortedMap param = new TreeMap()
        param.put('uid', uid)
        param.put('credits', credits)
        param.put('appKey', APPKEY)
        param.put('timestamp', timestamp)
        param.put('redirect', redirect)
        param.put('appSecret', APPSECRET)
        def sign = createSign(param)
        def url = "${REDIRECT_HOST}?uid=${uid}&credits=${credits}&appKey=${APPKEY}&sign=${sign}&timestamp=${timestamp}".toString()
        if (redirect != "") {
            url = url + '&redirect=' + redirect
        }
        return [code: 1, data: url]
        //resp.sendRedirect(url)
    }

    //扣积分接口
    def exchange(HttpServletRequest req, HttpServletResponse resp) {
        logger.debug('exchange:' + req.getParameterMap())
        def uid = req.getParameter('uid') //用户ID
        def credits = req.getParameter('credits') as Integer //本次兑换扣除的积分
        def itemCode = req.getParameter('itemCode') //自有商品商品编码(非必须字段)
        def appKey = req.getParameter('appKey') //接口appKey，应用的唯一标识
        def timestamp = req.getParameter('timestamp') //时间戳
        def description = req.getParameter('description') //本次积分消耗的描述(带中文，请用utf-8进行url解码)
        def orderNum = req.getParameter('orderNum') //兑吧订单号(请记录到数据库中)
        def type = req.getParameter('type') //兑换类型：alipay(支付宝), qb(Q币), coupon(优惠券), object(实物), phonebill(话费), phoneflow(流量), virtual(虚拟商品), littleGame（小游戏）,singleLottery(单品抽奖)，hdtoolLottery(活动抽奖),htool(新活动抽奖),manualLottery(手动开奖),ngameLottery(新游戏),questionLottery(答题),quizzLottery(测试题),guessLottery(竞猜) 所有类型不区分大小写
        def facePrice = req.getParameter('facePrice') //兑换商品的市场价值，单位是分
        def actualPrice = req.getParameter('actualPrice') //此次兑换实际扣除开发者账户费用，单位为分
        def ip = req.getParameter('ip') //用户ip
        def waitAudit = req.getParameter('waitAudit') //是否需要审核
        def params = req.getParameter('params') //详情参数，不同的类型，返回不同的内容，中间用英文冒号分隔。(支付宝类型带中文，请用utf-8进行解码) 实物商品：返回收货信息(姓名:手机号:省份:城市:区域:详细地址)、支付宝：返回账号信息(支付宝账号:实名)、话费：返回手机号、QB：返回QQ号
        def sign = req.getParameter('sign') //MD5签名

        def out = resp.getWriter()
        //判断是否重复
        def status = 'fail'
        def result = ['status': status, 'credits': credits] as Map//'bizId': orderId
        //校验sign
        def sign_params = [uid: uid, credits: credits, itemCode: itemCode, appKey: appKey, timestamp: timestamp, description: description, orderNum: orderNum,
                      type: type, status: 0, facePrice: facePrice, actualPrice: actualPrice, ip: ip, waitAudit: waitAudit, params: params] as TreeMap
        if (sign != createSign(getAllParam(req))) {
            logger.debug('sign error.remote:' + sign + ';local=' + createSign(req.getParameterMap()))
            out.print(JSONUtil.beanToJson(result))
            return
        }
        def points = duiba_points_logs().findOne($$(orderNum: orderNum, status: 0))
        if (points != null) {
            result['bizId'] = points['_id']
            out.print(JSONUtil.beanToJson(result))
            return
        }
        def _id = uid + '_duiba_deduct_' + credits + '_' + System.currentTimeMillis()
        def user_id = Integer.valueOf(uid)
        def logWithId = Web.awardLog(user_id, UserAwardType.兑吧扣积分, [points: 0 - credits])
        boolean succ = Crud.doTwoTableCommit(logWithId, [
                main           : { mainMongo.getCollection("users") },
                logColl        : { logMongo.getCollection('user_award_logs') },
                queryWithId    : { $$('_id': user_id, 'bag.points.count': [$gte: credits]) },
                update         : { $$($inc, $$('bag.points.count', 0 - credits)) },
                successCallBack: {
                    sign_params.put('_id', _id)
                    sign_params.put('user_id', user_id)
                    duiba_points_logs().save($$(sign_params), writeConcern)
                    return true
                },
                rollBack       : { $$($inc, $$('bag.points.count', credits)) }
        ] as TwoTableCommit)

        if (succ) {
            result['status'] = 'ok'
            result['bizId'] = logWithId['_id']
            out.print(JSONUtil.beanToJson(result))
            return
        }
        out.print(JSONUtil.beanToJson(result))
    }

    //兑换结果通知,如果兑换失败回退对应的积分
    def exchange_notify(HttpServletRequest req, HttpServletResponse resp) {
        logger.debug('exchange_notify:' + req.getParameterMap())
        def appKey = req.getParameter('appKey') //接口appKey，应用的唯一标识码
        def timestamp = req.getParameter('timestamp') //时间戳
        def uid = req.getParameter('uid') //用户的id
        def success = req.getParameter('success') //兑换是否成功，状态是true和false
        def errorMessage = req.getParameter('errorMessage') //出错原因(带中文，请用utf-8进行解码)
        def orderNum = req.getParameter('orderNum') //兑吧订单号
        def bizId = req.getParameter('bizId') //开发者的订单号
        def sign = req.getParameter('sign') //签名

        def out = resp.getWriter()

        //校验sign
        def params = [appKey: appKey, uid: uid, success: success, errorMessage: errorMessage, orderNum: orderNum,
                      bizId: bizId, timestamp: timestamp] as TreeMap
        if (sign != createSign(getAllParam(req))) {
            logger.debug('sign error.remote:' + sign + ';local=' + createSign(params))
            out.print('fail')
            return
        }
        //判断是否重复
        def points = duiba_points_logs().findOne($$(orderNum: orderNum))
        if (points != null && points['status'] != 0) {
            out.print('ok')
            return
        }
        def _id = points['_id']
        def user_id = points['uid'] as Integer
        if ('true' == success) {
            if (1 == duiba_points_logs().update($$(_id: _id), $$($set: [status: 2]), false, false, writeConcern).getN()) {
                out.print('ok')
                return
            }
        }
        if ('false' == success) {
            def credits = points['credits'] as Integer
            def logWithId = Web.awardLog(user_id, UserAwardType.兑吧扣积分, [points: credits])
            boolean succ = Crud.doTwoTableCommit(logWithId, [
                    main           : { mainMongo.getCollection("users") },
                    logColl        : { logMongo.getCollection('user_award_logs') },
                    queryWithId    : { $$('_id': user_id) },
                    update         : { $$($inc, $$('bag.points.count', credits)) },
                    successCallBack: {
                        if (1 == duiba_points_logs().update($$(_id: _id), $$($set: [status: 3]), false, false, writeConcern).getN()) {
                            return true
                        }
                        return false
                    },
                    rollBack       : { $$($inc, $$('bag.points.count', 0 - credits)) }
            ] as TwoTableCommit)
            if (succ) {
                out.print('ok')
                return
            }
        }
        out.print('fail')
    }

    public static String createSign(Map param) {
        StringBuffer sb = new StringBuffer('')
        def sortedMap = new TreeMap()

        for(Map.Entry<String, String> entry : param.entrySet()) {
            if (entry.getKey() == 'sign' || entry.getValue() == null) {
                continue
            }
            sortedMap.put(entry.getKey(), entry.getValue())
        }
        sortedMap.put('appSecret', APPSECRET)
        for (Map.Entry<String, Object> entry : sortedMap.entrySet()) {
            String v = String.valueOf(entry.getValue())
            if (StringUtils.isNotBlank(v)) {
                sb.append(v)
            }
        }
        return MsgDigestUtil.MD5.digest2HEX("${sb.toString()}".toString())
    }

    static Map getAllParam(HttpServletRequest req) {
        Map<String, Object> params = req.getParameterMap()
        Map result = new HashMap()
        for(String key : params.keySet()) {
            result.put(key, req.getParameter(key))
        }
        return result
    }

}
