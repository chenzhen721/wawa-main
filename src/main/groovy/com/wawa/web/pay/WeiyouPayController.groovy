package com.wawa.web.pay

import com.mongodb.DBCollection
import com.wawa.base.BaseController
import com.wawa.base.anno.Rest
import com.wawa.common.util.MsgDigestUtil
import com.wawa.common.util.JSONUtil
import com.wawa.model.OrderVia
import groovy.json.JsonSlurper
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.ServletRequestUtils

import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * 微游支付回调
 */
@Rest
class WeiyouPayController extends BaseController {
    static final Logger logger = LoggerFactory.getLogger(WeiyouPayController.class)
    // 货币类型
    static final String FEE_TYPE = 'CNY'
    static final String APP_KEY = "hZ6dAhBEs4"
    static final String APP_SECRET = "y3K2yja2sP"

    @Resource
    PayController payController

    DBCollection shop() {
        adminMongo.getCollection('shop')
    }

    private JsonSlurper jsonSlurper = new JsonSlurper()

    /**
     * 支付回调 //TODO 支付回调
     * @param req
     */
    def callback(HttpServletRequest req, HttpServletResponse resp) {

        def userID = ServletRequestUtils.getStringParameter(req, 'userID') //微游互动id
        def gameID = ServletRequestUtils.getStringParameter(req, 'gameID') //游戏id
        def openOrderID = ServletRequestUtils.getStringParameter(req, 'openOrderID') //cp方生成的订单号
        def openExtend = ServletRequestUtils.getStringParameter(req, 'openExtend') //开发者扩展信息
        def orderID = ServletRequestUtils.getStringParameter(req, 'orderID') //微游互动订单号
        def orderTime = ServletRequestUtils.getStringParameter(req, 'orderTime') //微游互动订单创建时间
        def payTime = ServletRequestUtils.getStringParameter(req, 'payTime') //用户ID
        def amount = ServletRequestUtils.getStringParameter(req, 'amount') //支付金额 单位元
        def payType = ServletRequestUtils.getStringParameter(req, 'payType') //支付类型
        def cache = ServletRequestUtils.getStringParameter(req, 'cache') //任意字符串，保证每次请求内容不同
        def sign = ServletRequestUtils.getStringParameter(req, 'sign') //签名
        logger.debug("Received weiyou_callback params is {}", req.getParameterMap())

        //验签
        def params = new TreeMap()
        params.putAll([userID   : userID, gameID: gameID, openOrderID: openOrderID, openExtend: openExtend, orderID: orderID,
                       orderTime: orderTime, payTime: payTime, amount: amount, payType: payType, cache: cache])
        def localSign = createSign(params)
        def resp_param = ["openOrderAmount": amount, "openOrderID": openOrderID,
                          "openOrderTime"  : new Date().format('yyyy-MM-dd HH:mm:ss'), "orderID": orderID,
                          "cache"          : cache] as TreeMap
        def out = resp.getWriter()
        if (!localSign.equals(sign)) {
            logger.debug("Recv h5_callback: 签名验证失败 ${sign}, 本地解析： ${localSign}")
            resp_param.put('openOrderResult', 99)
            resp_param.put('sign', createSign(resp_param))
            out.print(JSONUtil.beanToJson(resp_param))
            return
        }

        def orderId = openOrderID
        //def total_fee = new BigDecimal(amount)
        Double cny = Double.parseDouble(amount)
        def attachStr = openExtend
        if (StringUtils.isBlank(attachStr)) {
            logger.error("Recv h5_callback: attach Exception : {}", attachStr)
        }
        def attach = jsonSlurper.parseText(attachStr)
        Long diamond = attach['count'] as Long ?: 0
        Long award = attach['award'] as Long ?: 0
        Integer itemId = attach['item_id'] as Integer ?: null
        String exten = attach['ext'] as String ?: ''

        def transactionId = orderID
        def remark = JSONUtil.beanToJson(params)
        def ext = [award: award, item_id: itemId, ext: exten]
        if (payController.addDiamond(orderId, cny, diamond, OrderVia.微游.id, transactionId, FEE_TYPE, remark, ext)) {
            resp_param.put('openOrderResult', 0)
            resp_param.put('sign', createSign(resp_param))
            out.print(JSONUtil.beanToJson(resp_param))
            return
        }
        resp_param.put('openOrderResult', 99)
        resp_param.put('sign', createSign(resp_param))
        out.print(JSONUtil.beanToJson(resp_param))
    }

    public static String createSign(SortedMap<String, Object> params) {
        StringBuffer sb = new StringBuffer('&')
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String k = entry.getKey()
            if (entry.getValue() == null) {
                continue
            }
            String v = String.valueOf(entry.getValue())
            sb.append(k).append('=').append(v).append('&')
        }
        return MsgDigestUtil.MD5.digest2HEX("${APP_KEY}${sb.toString()}${APP_SECRET}".toString())
    }

}
