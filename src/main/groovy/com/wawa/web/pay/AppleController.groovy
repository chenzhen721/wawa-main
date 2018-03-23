package com.wawa.web.pay

import com.mongodb.BasicDBObject
import com.mongodb.DBCollection
import com.wawa.api.Web
import com.wawa.base.BaseController
import com.wawa.base.anno.RestWithSession
import com.wawa.common.doc.IMessageCode
import com.wawa.common.util.JSONUtil
import com.wawa.common.util.MsgDigestUtil
import com.wawa.common.doc.Result
import com.wawa.common.util.KeyUtils
import com.wawa.service.ipay.HttpUtils
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest
import java.math.MathContext

import static com.wawa.common.util.WebUtils.$$
import static com.wawa.common.doc.MongoKey.*

/**
 *
 * 苹果相关的支付内容
 */
@RestWithSession
class AppleController extends BaseController {

    static final Logger logger = LoggerFactory.getLogger(AppleController.class)
    static final String UTF8 = "UTF8";
    private final static String KEY = "DGYQc^n3vFjp6BJ*Ys2dTzeaJpeOp7@HA";
    private static final Integer TIME_OUT = 10 * 1000;
    private static final Set<String> BOUND_PAGE_LIST = ['com.xingai.aiwan01', 'com.xingai.aiwan02', 'com.xingai.aiwan03', 'com.xingai.aiwan04', 'com.xingai.aiwan05',
                                                        'com.xingai.memesimi', 'com.xingai.laizhibo', 'com.memezhibo.ios068', 'com.memezhibo.ios048', 'com.memezhibo.ios045',
                                                        'com.xingai.LaiHou'] as Set

    static final String VERIFY_RECEIPT = isTest ?
            "https://sandbox.itunes.apple.com/verifyReceipt" : "https://buy.itunes.apple.com/verifyReceipt";

    //苹果审核用 用户id
    private static final Integer AUDIT_USER_ID = 1202123;

    @Resource
    PayController payController
    //苹果内购支付平台回调地址
    /**
     *
     破解IAP绕过苹果扣款
     重复使用receipt-data
     利用信用卡黑卡
     利用外币卡折扣赚取差价
     利用苹果对小额消费不做验证规则的"36技术"
     * @param req
     * @return
     */
    def ipn(HttpServletRequest req) {
        def user_id = Web.getCurrentUserId()
        Integer test = (req.getParameter("test") ?: 0) as Integer
        def url = new URL(VERIFY_RECEIPT)
        //TODO 苹果iap审核二次校验地址？ 造成正式服充值漏洞  非测试环境下 指定唯一用户ID 用于苹果审核
        String via = 'itunes';
        String remark = '';
        if (!isTest && test == 1 && AUDIT_USER_ID.equals(user_id)) {
            url = new URL("https://sandbox.itunes.apple.com/verifyReceipt") //temp for apple verify
            via = 'Admin'
            remark = '苹果测试充值加币'
        }
        def receiptData = req.getReader().getText()
//        logger.debug("recevie  receiptData :{}",receiptData)

        //记录本次交易日志
        String orderId = payController.createOrder(user_id, user_id)
        logMongo.getCollection("trade_logs").insert($$(
                _id: orderId,
                uid: user_id,
                ip: Web.getClientIp(req),
                via: "itunes",
                reqs: receiptData,
                test: test,
                time: System.currentTimeMillis()
        ))

        //验证 receiptData有效性
        Map<String, Object> resultMap = null;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection()
            conn.setRequestMethod("POST")
            conn.setDoOutput(true)
            conn.getOutputStream().write(receiptData.getBytes())
            conn.setConnectTimeout(TIME_OUT);
            conn.setReadTimeout(TIME_OUT);
            conn.connect()
            resultMap = JSONUtil.jsonToMap(conn.getInputStream().getText(UTF8))
        } catch (Exception e) {
            logger.debug("ipn Connection exception : {}", e)
            return Result.error;
        } finally {
            if (conn != null) {
                conn.disconnect();
                conn = null;
            }
        }
        logger.debug("resultMap :{}", resultMap)
        //记录苹果验证结果
        logMongo.getCollection("trade_logs").update($$(_id: orderId), $$($set: [resp: resultMap]))

        //增加柠檬
        if (resultMap != null && 0 == ((Number) resultMap.get("status")).intValue()) {
            def receipt = (Map<String, String>) resultMap.get("receipt")
            logger.debug("receipt :{}", receipt)

            //判断包名
            def bid = receipt['bid'] as String
            if (!BOUND_PAGE_LIST.contains(bid)) {
                logger.debug("bid is illegal : {}", bid)
                return [code : 0, msg: 'bid is illegal']
            }

            def transaction_id = receipt['transaction_id']
            if (adminMongo.getCollection('finance_log').count(new BasicDBObject('transactionId', transaction_id)) == 1) {
                return IMessageCode.OK
            }
            /*   def quantity = Long.valueOf(receipt['quantity'])*/
            def product_id = receipt['product_id']
            def coinPrice = Integer.valueOf(product_id.substring(product_id.lastIndexOf('coin') + 4))
            Long diamond = (coinPrice * 1L) as Long
            Double cny = (diamond / 70).round(new MathContext(3)).doubleValue()
            if (payController.addDiamond(orderId, cny, diamond, via, transaction_id, '', remark, receipt)) {
                //测试加币自动关联到运营账户
                if(via.equals('Admin')){
                    saveAddCoinOps(orderId, user_id, diamond, System.currentTimeMillis())
                }
                return [code: Result.success.code, data: [coin: diamond]]
            }

        }
        return Result.error;
    }

    private static saveAddCoinOps(String order_id, Integer userId, Long coin, Long timestamp){
        timestamp = timestamp == null ? System.currentTimeMillis() : timestamp
        def seesion = $$("nick_name": "夏克腾",
                "_id": "12791",
                "name": "xiaketeng",
                "ip": "210.22.151.242")

        def data = $$("user_id":userId,
                order_id:order_id,
                "coin": coin,
                "remark": "苹果测试充值加币")
        def opsInfo = $$("_id":timestamp,"type": "finance_add", session:seesion, data:data,timestamp:timestamp)
        Web.adminMongo.getCollection('ops').save(opsInfo)
    }
    /**
     * "receipt":{"original_purchase_date_pst":"2013-08-20 19:54:48 America/Los_Angeles",
     * "purchase_date_ms":"1377053688261",
     * "unique_identifier":"78e483de6d1e25c90e7baaee30d1d934345ee666",
     * "original_transaction_id":"1000000084699535",
     * "bvrs":"1.0",
     * "transaction_id":"1000000084699535",
     * "quantity":"1",
     * "unique_vendor_identifier":"21A38536-C756-4694-9650-097D5B638171",
     * "item_id":"681028739",
     * "product_id":"com.taotao.product.rmb100",
     * "purchase_date":"2013-08-21 02:54:48 Etc/GMT",
     * "original_purchase_date":"2013-08-21 02:54:48 Etc/GMT",
     * "purchase_date_pst":"2013-08-20 19:54:48 America/Los_Angeles",
     * "bid":"com.taotao.inAppPurchaseTesting",
     * "original_purchase_date_ms":"1377053688261"},
     * "status":0}
     */

    /**
     *
     * quantity     购买商品的数量。对应SKPayment对象中的quantity属性
     product_id    商品的标识，对应SKPayment对象的 productIdentifier属性。
     transaction_id        交易的标识，对应 SKPaymentTransaction的transactionIdentifier属性
     purchase_date    交易的日期，对 应SKPaymentTransaction的transactionDate属性
     original_-transaction_id    对 于恢复的transaction对象，该键对应了原始的transaction标识
     original_purchase_-date    对于 恢复的transaction对象，该键对应了原始的交易日期
     app_item_id    App Store用来标识程序的字符串。一个服务器可能需要支持多个server的支付功能，可以用这个标识来区分程序。链接sandbox用来测试的程序的不 到这个值，因此该键不存在。
     version_external_-identifier    用来标识程序修订数。该键在sandbox环境下 不存在
     bid    iPhone程序的bundle标识
     bvrs    iPhone程序的版本号

     */


    DBCollection devices() { logMongo.getCollection("apple_devices") }

    //注册设备（iphone）
    def register(HttpServletRequest req) {
        def deviceToken = req['device_token'] as String
        if (StringUtils.isNotBlank(deviceToken) && deviceToken.length() > 20) {
            String uid = Web.currentUserId()
            mainRedis.opsForSet().add(KeyUtils.PUBLIC.appleUsers(), uid)
            devices().remove($$('uid', Integer.valueOf(uid)))
            [code: devices().save($$(_id, deviceToken).append('uid', Integer.valueOf(uid))).getN()]
        } else {
            [code: 0]
        }
    }

    DBCollection apple_idfa() { logMongo.getCollection("apple_idfa") }
    //注册（idfa）
    def idfa(HttpServletRequest req) {
        logger.debug('Recv idfa params: {}',req.getParameterMap())
        def idfa = req['idfa']
        def call_back = req['call_back']
        def from = req['from']
        def user_id = Web.getCurrentUserId()
        if (StringUtils.isEmpty(idfa) || StringUtils.isEmpty(req['s']))
            return Result.丢失必需参数;
        //验证签名
        String sign = MsgDigestUtil.MD5.digest2HEX("${idfa}${user_id}${KEY}", true)
        if (!sign.equals(req['s'])) {
            return Result.丢失必需参数;
        }

        /**
         * 如果有回调地址,并且count=0,则请求回调地址
         */
        if (StringUtils.isNotBlank(call_back)) {
            call_back = URLDecoder.decode(call_back, 'utf-8')
            BasicDBObject countExpression = new BasicDBObject(_id: idfa)
            Long count = apple_idfa().count(countExpression)
            if (count == 0) {
                // count=0 代表是新增,发送给嗨吧
                try {
                    HttpUtils.sendGet(call_back, new HashMap())
                    logMongo.getCollection('ad_logs').insert($$(idfa: idfa, from: from, timestamp: System.currentTimeMillis(), call_back: call_back, status: 0), writeConcern)
                } catch (Exception e) {
                    logger.debug('request error {}', e.printStackTrace())
                }
            }
        }

        [code: apple_idfa().update($$(_id, idfa), $$($set, [timestamp: System.currentTimeMillis(), ip: Web.getClientId(req)])
                .append($addToSet, $$("uids", user_id)), true, false).getN()]

    }

}
