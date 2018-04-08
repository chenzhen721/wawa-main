package com.wawa.web.pay

import com.mongodb.DBCollection
import com.wawa.AppProperties
import com.wawa.api.UserWebApi
import com.wawa.api.Web
import com.wawa.api.trade.Order
import com.wawa.base.BaseController
import com.wawa.base.anno.Rest
import com.wawa.common.util.JSONUtil
import com.wawa.common.util.http.HttpClientUtil
import com.wawa.common.doc.Result
import com.wawa.model.OrderVia
import com.wawa.service.weixin.WebRequestHandler
import com.wawa.service.weixin.WebResponseHandler
import com.wawa.service.weixin.util.WXUtil
import com.wawa.service.weixin.util.ZXingUtil
import groovy.json.JsonSlurper
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.ServletRequestUtils

import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static com.wawa.common.util.WebUtils.$$
import static com.wawa.common.doc.MongoKey.*

/**
 * 微信支付
 */
@Rest
class WxpayController extends BaseController {
    static final Logger logger = LoggerFactory.getLogger(WxpayController.class)
    /**
     * 商户平台
     */
    private static final String H5_MCH_ID = isTest ? "1497610262" : "1495650632"
    private static
    final String H5_APP_KEY = isTest ? "fbf4fd31c00a82d5cbe5161c5e699a0e" : "fbf4fd32c00a82d5cbe5161c5e699a0e"//商户号秘钥

    private static final String H5_APP_ID = isTest ? "wx87f81569b7e4b5f6" : "wxf64f0972d4922815"
    private static
    final String H5_APP_Secret = isTest ? "8421fd4781b1c29077c2e82e71ce3d2a" : "fbf4fd32c00a82d5cbe5161c5e699a0e"
    //公众号秘钥

    private static final String PROGRAM_APP_ID = "wxc209b408001d9c77"//小程序APPID
    private static final String PROGRAM_APP_Secret = "ac0308bc4b0c50ab7915d838b39f6739" //小程序秘钥

    private static final String H5_APP_URL = "https://api.weixin.qq.com/sns/oauth2/access_token"
    private static final String H5_NOTIFY_PC_URL = "${AppProperties.get('api.domain')}wxpay/h5_callback".toString()

    static String formatString(String text) { text ?: "" }
    // 货币类型
    static final String FEE_TYPE = 'CNY'

    // 成功标识符
    static final String SUCCESS = 'SUCCESS'

    // 返回微信回调成功的信息
    static final Map SUCCESS_DATA = [return_code: 'SUCCESS', return_msg: 'OK']

    // 返回微信回调失败的信息
    static final Map FAILURE_DATA = [return_code: 'FAIL', return_msg: 'valid sign failure']

    @Resource
    PayController payController

    DBCollection shop() {
        adminMongo.getCollection('shop')
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Pay
     * @apiName order_h5
     * @api{post} wxpay/order_h5?item_id=:item_id&_id=:_id  微信内H5充值
     * @apiDescription
     * 微信内H5充值
     *
     * @apiParam{Integer} item_id  商品ID
     * @apiParam{Integer} _id  用户_id
     * @apiParam{String} ext  充值扩展字段
     *
     * @apiExample{ curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/wxpay/order_h5?item_id=123&_id=123
     *
     * @apiSuccessExample{json} Success-Response:
     *
     *{
     *     "data": {
     *         "timestamp": "1509348125",
     *         "result_code": "SUCCESS",
     *         "sign": "51211867BE4282980EB337BC11B13B64",
     *         "mch_id": "1457175402",
     *         "prepay_id": "wx201710301519467dc6e633e30664458776",
     *         "return_msg": "OK",
     *         "appid": "wx45d43a50adf5a470",
     *         "nonce_str": "nrE8QqXSzH0lFP3p",
     *         "return_code": "SUCCESS",
     *         "device_info": "WEB",
     *         "pay_sign": "15E78435C5ECF1431AE2D78040C06004",
     *         "trade_type": "JSAPI"
     *},
     *     "exec": 75010,
     *     "code": 1
     *}*
     */
    def order_h5(HttpServletRequest req, HttpServletResponse resp) {
        Integer total_fee = 0
        //单位元
        /*String sTotatFee = req.getParameter("amount")
        if (StringUtils.isNotBlank(sTotatFee))
            total_fee = (Double.parseDouble(sTotatFee) * 100).toInteger()
        if (total_fee <= 0 || (!isTest && !H5_AMOUNT_AND_AWARD.containsKey(total_fee))) {
            logger.debug("Weixin order_h5:", "amount: ${total_fee} invalid ".toString())
            return Result.丢失必需参数
        }*/
        logger.debug("Weixin order_h5: params :{}", req.getParameterMap())
        logger.debug("Weixin order_h5: ip :{}", Web.getClientIp(req))

        Integer itemId = ServletRequestUtils.getIntParameter(req, "item_id")
        def item = shop().findOne(itemId)
        if (item == null || item['cost'] == null || item['count'] == null) {
            logger.error("Weixin order_h5: itemId: ${itemId} invalid")
            return Result.丢失必需参数
        }
        String ext = ServletRequestUtils.getStringParameter(req, "ext", '')
        total_fee = item['cost'] as Integer
        String code = req.getParameter('code')
        //设置请求参数
        Integer userId = ServletRequestUtils.getIntParameter(req, _id)
        Integer toId = ServletRequestUtils.getIntParameter(req, "to_id", userId)
        //用户ID检测
        if (userId == null || users().count($$('_id', userId)) <= 0) {
            logger.debug("Weixin order_h5:", "userId: ${userId} invalid ".toString())
            return Result.丢失必需参数
        }
        toId = toId ?: userId
        if (!userId.equals(toId) && users().count($$('_id', toId)) <= 0) {
            return Result.丢失必需参数
        }

        //通过user获取
        String openid
        if (StringUtils.isBlank(code)) {
            if (userId == null) {
                return Result.丢失必需参数
            }
            openid = UserWebApi.getOpenidForWeixin(userId, H5_APP_ID)
        } else {
            if (StringUtils.isBlank(code)) {
                return Result.丢失必需参数
            }
            openid = getOpenidByCode(0, code);
        }
        if (StringUtils.isEmpty(openid))
            return Result.丢失必需参数


        def privateField = JSONUtil.beanToJson(count: item['count'], award: item['award'] ?: 0, item_id: itemId, ext: ext)
        //---------------生成订单号 开始------------------------
        //订单号，此处用时间加随机数生成，商户根据自己情况调整，只要保持全局唯一就行
        String out_trade_no = payController.createOrder(userId, toId)
        //---------------生成订单号 结束------------------------

        WebRequestHandler reqHandler = new WebRequestHandler(req, resp);//生成package的请求类

        reqHandler.setKey(H5_APP_KEY);
        //设置package订单参数
        reqHandler.setParameter("appid", H5_APP_ID);//银行渠道
        reqHandler.setParameter("mch_id", H5_MCH_ID);//商户号
        reqHandler.setParameter("device_info", "WEB");//设备号
        reqHandler.setParameter("nonce_str", WXUtil.getNonceStr());
        reqHandler.setParameter("body", '钻石');//商品描述
        reqHandler.setParameter("out_trade_no", out_trade_no);//商户订单号
        reqHandler.setParameter("fee_type", 'CNY');//货币类型
        reqHandler.setParameter("total_fee", total_fee as String);//总金额
        reqHandler.setParameter("spbill_create_ip", Web.getClientIp(req));//终端IP
        //reqHandler.setParameter("spbill_create_ip", "210.22.151.242");//终端IP
        reqHandler.setParameter("notify_url", H5_NOTIFY_PC_URL);//通知地址
        reqHandler.setParameter("trade_type", 'JSAPI');//交易类型
        reqHandler.setParameter("openid", openid);//trade_type=JSAPI，此参数必传，用户在商户appid下的唯一标识
        //reqHandler.setParameter("product_id", '1234567890');//商品ID
        reqHandler.setParameter("attach", privateField)//自定义数据
        Map res = reqHandler.sendPrepay();
        logger.info('===================>order_h5:' + res)
        if (res != null) {
            logger.debug("Weixin order_h5 res : {}", res)
            def return_code = res.get('return_code') as String
            def result_code = res.get('result_code') as String
            //返回状态成功
            if ('SUCCESS'.equals(return_code) && 'SUCCESS'.equals(result_code)) {
                def timestamp = WXUtil.getTimeStamp()
                res.put('timestamp', timestamp);
                //前端jsapi签名
                def parameters = new TreeMap();
                parameters.put("appId", H5_APP_ID)
                parameters.put("timeStamp", timestamp)
                parameters.put("nonceStr", res['nonce_str'] as String)
                parameters.put("package", "prepay_id=${res['prepay_id']}".toString())
                parameters.put("signType", "MD5")
                logger.debug("Weixin order_h5 jsapi params : {}", parameters)
                res.put("pay_sign", WXUtil.createSign(parameters, H5_APP_KEY, "UTF-8"))
                logger.debug("Weixin order_h5 api return : {}", res)
                Order.prepayOrder(out_trade_no, OrderVia.微信H5);
                return [code: 1, data: res]
            }
        }
        return [code: 0]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Pay
     * @apiName order_program
     * @api{post} wxpay/order_program?item_id=:item_id&_id=:_id  微信小程序充值
     * @apiDescription
     * 微信内H5充值
     *
     * @apiParam{Integer} item_id  商品ID
     * @apiParam{Integer} _id  用户_id
     * @apiParam{String} ext  充值扩展字段
     *
     * @apiExample{ curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/wxpay/order_program?item_id=123&_id=123
     *
     * @apiSuccessExample{json} Success-Response:
     *
     *{
     *     "data": {
     *         "timestamp": "1509348125",
     *         "result_code": "SUCCESS",
     *         "sign": "51211867BE4282980EB337BC11B13B64",
     *         "mch_id": "1457175402",
     *         "prepay_id": "wx201710301519467dc6e633e30664458776",
     *         "return_msg": "OK",
     *         "appid": "wx45d43a50adf5a470",
     *         "nonce_str": "nrE8QqXSzH0lFP3p",
     *         "return_code": "SUCCESS",
     *         "device_info": "WEB",
     *         "pay_sign": "15E78435C5ECF1431AE2D78040C06004",
     *         "trade_type": "JSAPI"
     *},
     *     "exec": 75010,
     *     "code": 1
     *}*
     */
    def order_program(HttpServletRequest req, HttpServletResponse resp) {
        Integer total_fee = 0
        Integer itemId = ServletRequestUtils.getIntParameter(req, "item_id")
        def item = shop().findOne(itemId)
        if (item == null || item['cost'] == null || item['count'] == null) {
            logger.error("Weixin order_h5: itemId: ${itemId} invalid")
            return Result.丢失必需参数
        }
        String ext = ServletRequestUtils.getStringParameter(req, "ext", '')
        total_fee = item['cost'] as Integer
        String code = req.getParameter('code')
        //设置请求参数
        Integer userId = ServletRequestUtils.getIntParameter(req, _id) as Integer
        Integer toId = ServletRequestUtils.getIntParameter(req, "to_id", userId)
        //用户ID检测
        if (userId == null || users().count($$('_id', userId)) <= 0) {
            logger.debug("Weixin order_h5:", "userId: ${userId} invalid ".toString())
            return Result.丢失必需参数
        }
        toId = toId ?: userId
        if (!userId.equals(toId) && users().count($$('_id', toId)) <= 0) {
            return Result.丢失必需参数
        }

        //通过user获取
        String openid
        if (StringUtils.isBlank(code)) {
            if (userId == null) {
                return Result.丢失必需参数
            }
            openid = UserWebApi.getOpenidForWeixin(userId, PROGRAM_APP_ID)
        } else {
            if (StringUtils.isBlank(code)) {
                return Result.丢失必需参数
            }
            openid = getOpenidByCode(code, PROGRAM_APP_ID, PROGRAM_APP_Secret)
        }
        if (StringUtils.isEmpty(openid))
            return Result.丢失必需参数


        def privateField = JSONUtil.beanToJson(count: item['count'], award: item['award'] ?: 0, item_id: itemId, ext: ext)
        //---------------生成订单号 开始------------------------
        //订单号，此处用时间加随机数生成，商户根据自己情况调整，只要保持全局唯一就行
        String out_trade_no = payController.createOrder(userId, toId)
        //---------------生成订单号 结束------------------------

        WebRequestHandler reqHandler = new WebRequestHandler(req, resp);//生成package的请求类

        reqHandler.setKey(H5_APP_KEY);
        //设置package订单参数
        reqHandler.setParameter("appid", PROGRAM_APP_ID);//银行渠道
        reqHandler.setParameter("mch_id", H5_MCH_ID);//商户号
        reqHandler.setParameter("device_info", "WEB");//设备号
        reqHandler.setParameter("nonce_str", WXUtil.getNonceStr());
        reqHandler.setParameter("body", '钻石');//商品描述
        reqHandler.setParameter("out_trade_no", out_trade_no);//商户订单号
        reqHandler.setParameter("fee_type", 'CNY');//货币类型
        reqHandler.setParameter("total_fee", total_fee as String);//总金额
        //TODO reqHandler.setParameter("spbill_create_ip", Web.getClientIp(req));//终端IP
        reqHandler.setParameter("spbill_create_ip", "210.22.151.242");//终端IP
        reqHandler.setParameter("notify_url", H5_NOTIFY_PC_URL);//通知地址
        reqHandler.setParameter("trade_type", 'JSAPI');//交易类型
        reqHandler.setParameter("openid", openid);//trade_type=JSAPI，此参数必传，用户在商户appid下的唯一标识
        //reqHandler.setParameter("product_id", '1234567890');//商品ID
        reqHandler.setParameter("attach", privateField)//自定义数据
        Map res = reqHandler.sendPrepay();
        if (res != null) {
            logger.debug("Weixin order_h5 res : {}", res)
            def return_code = res.get('return_code') as String
            def result_code = res.get('result_code') as String
            //返回状态成功
            if ('SUCCESS'.equals(return_code) && 'SUCCESS'.equals(result_code)) {
                def timestamp = WXUtil.getTimeStamp()
                res.put('timestamp', timestamp);
                //前端jsapi签名
                def parameters = new TreeMap();
                parameters.put("appId", PROGRAM_APP_ID)
                parameters.put("timeStamp", timestamp)
                parameters.put("nonceStr", res['nonce_str'] as String)
                parameters.put("package", "prepay_id=${res['prepay_id']}".toString())
                parameters.put("signType", "MD5")
                logger.debug("Weixin order_h5 jsapi params : {}", parameters)
                res.put("pay_sign", WXUtil.createSign(parameters, H5_APP_KEY, "UTF-8"))
                logger.debug("Weixin order_h5 api return : {}", res)
                Order.prepayOrder(out_trade_no, OrderVia.微信H5);
                return [code: 1, data: res]
            }
        }
        return [code: 0]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Pay
     * @apiName qcode_h5
     * @api{post} wxpay/qcode_h5?item_id=:item_id&_id=:_id  微信二维码充值
     * @apiDescription
     * 微信二维码充值
     *
     * @apiParam{Integer} item_id  商品ID
     * @apiParam{Integer} _id  用户_id
     * @apiParam{String} ext  充值扩展字段
     *
     * @apiExample{ curl } Example usage:
     *     curl -i http://test-api.17laihou.com/wxpay/qcode_h5?item_id=123&_id=123
     *
     * @apiSuccessExample{json} Success-Response:
     *
     *{
     * "data": {
     *     "return_msg": "OK",
     *     "prepay_id": "wx20171124145537d69176a08f0588346648",
     *     "pay_sign": "1F8EEF197AC44286C8057BC8CE5BC40F",
     *     "appid": "wx45d43a50adf5a470",
     *     "code_url": "weixin://wxpay/bizpayurl?pr=WKlY8o4",
     *     "nonce_str": "FqmKs5KsxlpAThkX",
     *     "device_info": "WEB",
     *     "trade_type": "NATIVE",
     *     "sign": "1B986CF2C2B82156B9016D93A00A4D71",
     *     "result_code": "SUCCESS",
     *     "timestamp": "1511506539",
     *     "mch_id": "1457175402",
     *     "return_code": "SUCCESS"
     *},
     * "exec": 306,
     * "code": 1
     *
     */
    def qcode_h5(HttpServletRequest req, HttpServletResponse resp) {
        Integer itemId = ServletRequestUtils.getIntParameter(req, "item_id")
        def item = shop().findOne(itemId)
        if (item == null || item['cost'] == null || item['count'] == null) {
            logger.error("Weixin order_h5: itemId: ${itemId} invalid")
            return Result.丢失必需参数
        }
        String ext = ServletRequestUtils.getStringParameter(req, "ext", '')
        //单位元
        Integer total_fee = item['cost'] as Integer ?: 0
        //设置请求参数
        Integer userId = ServletRequestUtils.getIntParameter(req, _id)
        Integer toId = ServletRequestUtils.getIntParameter(req, "to_id", userId)
        //用户ID检测
        if (userId == null || users().count($$('_id', userId)) <= 0) {
            logger.debug("Weixin order_h5:", "userId: ${userId} invalid ".toString())
            return Result.丢失必需参数
        }
        toId = toId ?: userId
        if (!userId.equals(toId) && users().count($$('_id', toId)) <= 0) {
            return Result.丢失必需参数
        }

        def privateField = JSONUtil.beanToJson(count: item['count'], award: item['award'] ?: 0, item_id: itemId, ext: ext)
        //---------------生成订单号 开始------------------------
        //订单号，此处用时间加随机数生成，商户根据自己情况调整，只要保持全局唯一就行
        String out_trade_no = payController.createOrder(userId, toId)
        //---------------生成订单号 结束------------------------

        WebRequestHandler reqHandler = new WebRequestHandler(req, resp);//生成package的请求类

        reqHandler.setKey(H5_APP_KEY);
        //设置package订单参数
        reqHandler.setParameter("appid", H5_APP_ID);//银行渠道
        reqHandler.setParameter("mch_id", H5_MCH_ID);//商户号
        reqHandler.setParameter("device_info", "WEB");//设备号
        reqHandler.setParameter("nonce_str", WXUtil.getNonceStr());
        reqHandler.setParameter("body", '钻石');//商品描述
        reqHandler.setParameter("out_trade_no", out_trade_no);//商户订单号
        reqHandler.setParameter("fee_type", 'CNY');//货币类型
        reqHandler.setParameter("total_fee", total_fee as String);//总金额 单位分
        reqHandler.setParameter("spbill_create_ip", Web.getClientIp(req));//终端IP
        //reqHandler.setParameter("spbill_create_ip", "210.22.151.242");//终端IP
        reqHandler.setParameter("notify_url", H5_NOTIFY_PC_URL);//通知地址
        reqHandler.setParameter("trade_type", 'NATIVE');//交易类型
        //reqHandler.setParameter("openid", openid);//trade_type=JSAPI，公众号支付时此参数必传，用户在商户appid下的唯一标识
        reqHandler.setParameter("product_id", itemId.toString());//商品ID
        reqHandler.setParameter("attach", privateField)//自定义数据
        Map res = reqHandler.sendPrepay();
        if (res != null) {
            logger.debug("Weixin order_h5 res : {}", res)
            def return_code = res.get('return_code') as String
            def result_code = res.get('result_code') as String
            //返回状态成功
            if ('SUCCESS'.equals(return_code) && 'SUCCESS'.equals(result_code)) {
                def code_url = res.get('code_url') as String
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
                //生成二维码
                ZXingUtil.writeImage(code_url, 'png', outputStream)
                String imageString = new String(Base64.encodeBase64(outputStream.toByteArray()));
                outputStream.close()
                return [code: 1, data: [order_id: out_trade_no, image: "data:image/png;base64,${imageString}".toString()]]
            }
        }
        return [code: 0]
    }

    private JsonSlurper jsonSlurper = new JsonSlurper()

    /**
     * H5支付回调
     * @param req
     */
    def h5_callback(HttpServletRequest req, HttpServletResponse resp) {
        logger.debug("Recv h5_callback ...")

        def body = req.getReader().getText()
        //logger.debug("Recv h5_callback body : {}", body)
        Map<String, String> map = WXUtil.parseXml(body);
        logger.debug("Recv h5_callback map : {}", map)

        //创建支付应答对象
        WebResponseHandler respHandler = new WebResponseHandler(req, resp);
        respHandler.setKey(H5_APP_KEY);
        //判断签名
        try {
            respHandler.initParametersFromMap(map);
        } catch (Exception e) {
            logger.error("Recv h5_callback: initParametersFromMap Exception : {}", e)
        }


        if (!respHandler.isTenpaySign()) {
            logger.debug("Recv h5_callback: 签名验证失败 ${respHandler.getDebugInfo()}");
            respHandler.sendToCFT(WXUtil.mapToXml([return_code: 'FAIL', return_msg: 'valid sign failure']));
            return;
        }

        def returnCode = respHandler.getParameter("return_code")
        def resultCode = respHandler.getParameter("result_code")
        if (SUCCESS == returnCode && SUCCESS == resultCode) {
            def orderId = respHandler.getParameter("out_trade_no")
            def total_fee = new BigDecimal(respHandler.getParameter('total_fee'))
            Double cny = Double.parseDouble(total_fee.divide(new BigDecimal(100)).toString())
            def attachStr = respHandler.getParameter("attach")
            if (StringUtils.isBlank(attachStr)) {
                logger.error("Recv h5_callback: attach Exception : {}", respHandler)
            }
            def attach = jsonSlurper.parseText(attachStr)
            Long diamond = attach['count'] as Long ?: 0
            Long award = attach['award'] as Long ?: 0
            Integer itemId = attach['item_id'] as Integer ?: null
            String exten = attach['ext'] as String ?: ''
            /*if (H5_AMOUNT_AND_AWARD.containsKey(total_fee.intValue())) {
                def total = H5_AMOUNT_AND_AWARD.get(total_fee.intValue()) as Long
                award = total - diamond
                diamond = total
            }*/

            def transactionId = respHandler.getParameter("transaction_id")
            def remark = ''
            def ext = [award: award, item_id: itemId, ext: exten]
            if (payController.addDiamond(orderId, cny, diamond, OrderVia.微信H5.id, transactionId, FEE_TYPE, remark, ext)) {
                respHandler.sendToCFT(WXUtil.mapToXml(SUCCESS_DATA));
                return
            }
            def errorMsg = respHandler.getParameter("return_msg")
            logger.warn("returnCode is {},errorMsg is {}", returnCode, errorMsg);
            respHandler.sendToCFT(WXUtil.mapToXml(FAILURE_DATA));
        }
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Pay
     * @apiName order_wap
     * @api{post} wxpay/order_wap?amount=:amount&_id=:_id  微信外部浏览器H5充值
     * @apiDescription
     * 微信外部浏览器H5充值
     *
     * @apiParam{Integer} amount  充值金额amount
     * @apiParam{Integer} _id  用户_id
     * @apiParam{String} ext  充值扩展字段
     *
     * @apiExample{ curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/wxpay/order_wap?amount=123&_id=123
     *
     * @apiSuccessExample{json} Success-Response:
     *{
     "data": {
     "url": "weixin://wap/pay?appid%3Dwx45d43a50adf5a470%26noncestr%3D92262bf907af914b95a0fc33c3f33bf6%26package%3DWAP%26prepayid%3Dwx201711031131
     59cdaee1cfd30484335726%26sign%3DAF6F733D4C17E19EC2C775AF178F07A9%26timestamp%3D1509680056"
     },
     "exec": 18432,
     "code": 1
     }
     */
    def order_wap(HttpServletRequest req, HttpServletResponse rsp) {
        Integer total_fee = 0
        Integer userId = req.getParameter(_id) as Integer
        //String sTotatFee = req.getParameter("amount")
        Integer toId = ServletRequestUtils.getIntParameter(req, "to_id", userId)
        Integer schema_type = ServletRequestUtils.getIntParameter(req, 'schema_type', 0)
        logger.debug("Weixin order_wap: params :{}", req.getParameterMap())
        logger.debug("Weixin order_wap: ip :{}", Web.getClientIp(req))
        /*if (StringUtils.isNotBlank(sTotatFee))
            total_fee = (Double.parseDouble(sTotatFee) * 100).toInteger()
        if (total_fee <= 0 || (!isTest && !H5_AMOUNT_AND_AWARD.containsKey(total_fee))) {
            logger.debug("Weixin order_wap:", "amount: ${total_fee} invalid ".toString())
            return Result.丢失必需参数
        }*/
        Integer itemId = ServletRequestUtils.getIntParameter(req, "item_id")
        def item = shop().findOne(itemId)
        if (item == null || item['cost'] == null || item['count'] == null) {
            logger.error("Weixin order_wap: itemId: ${itemId} invalid")
            return Result.丢失必需参数
        }
        String ext = ServletRequestUtils.getStringParameter(req, "ext", '')
        total_fee = item['cost'] as Integer
        //用户ID检测
        if (userId == null || users().count($$('_id', userId)) <= 0) {
            logger.debug("Weixin order_wap:", "userId: ${userId} invalid ".toString())
            return Result.丢失必需参数
        }
        toId = toId ?: userId
        if (!userId.equals(toId) && users().count($$('_id', toId)) <= 0) {
            return Result.丢失必需参数
        }

        def privateField = JSONUtil.beanToJson(count: item['count'], award: item['award'] ?: 0, item_id: itemId, ext: ext)

        //---------------生成订单号 开始------------------------
        //订单号，此处用时间加随机数生成，商户根据自己情况调整，只要保持全局唯一就行
        String out_trade_no = payController.createOrder(userId, toId)  //订单号;
        //---------------生成订单号 结束------------------------

        WebRequestHandler reqHandler = new WebRequestHandler(req, rsp);//生成package的请求类

        reqHandler.setKey(H5_APP_KEY);
        //设置package订单参数
        reqHandler.setParameter("appid", H5_APP_ID);//银行渠道
        reqHandler.setParameter("mch_id", H5_MCH_ID);//商户号
        //reqHandler.setParameter("device_info", "WEB");//设备号
        String noncestr = WXUtil.getNonceStr()
        reqHandler.setParameter("nonce_str", noncestr);
        reqHandler.setParameter("body", '钻石');//商品描述
        reqHandler.setParameter("out_trade_no", out_trade_no);//商户订单号
        reqHandler.setParameter("fee_type", 'CNY');//货币类型
        reqHandler.setParameter("total_fee", total_fee as String);//总金额
        reqHandler.setParameter("spbill_create_ip", Web.getClientIp(req));//终端IP
        //reqHandler.setParameter("spbill_create_ip", "210.22.151.242");//终端IP
        reqHandler.setParameter("notify_url", H5_NOTIFY_PC_URL);//通知地址
        reqHandler.setParameter("trade_type", 'MWEB');//交易类型
        reqHandler.setParameter("attach", privateField)//自定义数据
        Map res = reqHandler.sendPrepay();
        if (res != null) {
            logger.debug("Weixin order_wap res : {}", res)
            def return_code = res.get('return_code') as String
            def result_code = res.get('result_code') as String
            //返回状态成功
            if ('SUCCESS'.equals(return_code) && 'SUCCESS'.equals(result_code)) {
                def timestamp = WXUtil.getTimeStamp()

                /**
                 * 公众账号ID 	     appid 	String(32) 	是 	wx8888888888888888 	微信分配的公众账号ID
                 商户号 	             partnerid 	String(32) 	是 	1900000109 	微信支付分配的商户号
                 预支付交易会话ID 	 prepayid 	String(32) 	是 	WX1217752501201407033233368018 	微信返回的支付交易会话ID
                 扩展字段 	         package 	String(128) 	是 	Sign=WXPay 	暂填写固定值Sign=WXPay
                 随机字符串 	         noncestr 	String(32) 	是 	5K8264ILTKCH16CQ2502SI8ZNMTM67VS 	随机字符串，不长于32位。推荐随机数生成算法
                 时间戳 	             timestamp 	String(10) 	是 	1412000000 	时间戳，请见接口规则-参数规定
                 签名 	             sign 	String(32) 	是 	C380BEC2BFD727A4B6845133519F3AD6
                 */
                String prepay_id = res['prepay_id'] as String
                def data = new TreeMap()
                data.put("appid", H5_APP_ID)
                data.put("noncestr", noncestr)
                data.put("package", "WAP")
                data.put("prepayid", prepay_id)
                data.put("timestamp", timestamp)
                Order.prepayOrder(out_trade_no, OrderVia.微信WAP);
                //MD5(appid=wx2b029c08a6232582&noncestr=a12bx7y0yr3wfiqlnoksxkp8rlebv6cw&package=WAP&prepayid=wx201604061610222e401a5c3f0005205814&timestamp=20160406161022&key=111111111)
                String sign = WXUtil.createSign(data, H5_APP_KEY, "UTF-8")
                //生成deeplink weixin://wap/pay?appid=wx2b029c08a6232582&noncestr=a12bx7y0yr3wfiqlnoksxkp8rlebv6cw&package=WAP&prepayid=wx201604061610222e401a5c3f0005205814&timestamp=20160406161022&sign=9e9e871cde8a795549f019a875b65a45
                //String encodeStr = URLEncoder.encode("appid=${H5_APP_ID}&noncestr=${noncestr}&package=WAP&prepayid=${prepay_id}&sign=${sign}&timestamp=${timestamp}".toString(), "UTF-8")
                String url = res['mweb_url']
                if (schema_type == 1) {
                    url = "weixin://wap/pay?appid=${H5_APP_ID}&noncestr=${noncestr}&package=WAP&prepayid=${prepay_id}&sign=${sign}&timestamp=${timestamp}".toString()
                }

                return [code: 1, data: [url: url]]
            }
        }
        return [code: 30411]//错误：获取prepayId失败
    }

    /**
     * 通过code/userId 获取 openid
     * 微信公众平台授权
     * https://api.weixin.qq.com/sns/oauth2/access_token?appid=APPID&secret=SECRET&code=CODE&grant_type=authorization_code
     */
    private static String getOpenidByCode(Integer userId, String code) {
        logger.debug("auth login code: {}", code)
        def token_url = H5_APP_URL + "?appid=${H5_APP_ID}&secret=${H5_APP_Secret}&code=${code}&grant_type=authorization_code"
        logger.debug("auth login token_url: {}", token_url)
        String resp = HttpClientUtil.get(token_url, null, HttpClientUtil.UTF8)
        Map respMap = JSONUtil.jsonToMap(resp)
        logger.debug("auth login token_url respMap: {}", respMap)
        String access_token = respMap['access_token'] as String
        String openId = respMap['openid'] as String
        return openId
    }

    private static String getOpenidByCode(String code, String app_id, String app_secret) {
        logger.debug("auth login code: {}", code)
        def token_url = H5_APP_URL + "?appid=${app_id}&secret=${app_secret}&code=${code}&grant_type=authorization_code"
        logger.debug("auth login token_url: {}", token_url)
        String resp = HttpClientUtil.get(token_url, null, HttpClientUtil.UTF8)
        Map respMap = JSONUtil.jsonToMap(resp)
        logger.debug("auth login token_url respMap: {}", respMap)
        String access_token = respMap['access_token'] as String
        String openId = respMap['openid'] as String
        return openId
    }

    /**
     * 记录失败日志
     * @param req
     */
    def error_log(HttpServletRequest req) {
        logger.error('wxpay error.' + req.getParameterMap())
        return Result.success
    }

}
