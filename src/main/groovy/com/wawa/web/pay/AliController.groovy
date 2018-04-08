package com.wawa.web.pay

//import com.alipay.api.internal.util.AlipaySignature
//import com.alipay.api.response.AlipayTradeWapPayResponse
import com.mongodb.DBCollection
import com.wawa.api.trade.Order
import com.wawa.base.BaseController
import com.wawa.base.anno.Rest
import com.wawa.common.doc.Result
import com.wawa.model.OrderVia
import com.wawa.service.alipay.client.trade.WapTrade2
import com.wawa.service.alipay.config.AlipayConfig
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.ServletRequestUtils

import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static com.wawa.common.doc.MongoKey.*
import static com.wawa.common.util.WebUtils.$$

/**
 *
 * https://docs.open.alipay.com/203/105285/
 * 支付宝相关的支付
 */
@Rest
class AliController extends BaseController {
    Logger logger = LoggerFactory.getLogger(AliController.class)

    static String formatString(String text) { text ?: "" }

    static String TRADE_FINISHED = 'TRADE_FINISHED'
    static String TRADE_SUCCESS = 'TRADE_SUCCESS'

    @Resource
    PayController payController

    DBCollection shop(){
        adminMongo.getCollection('shop')
    }

    private WapTrade2 wapTrade2 = new WapTrade2()

    //生产订单
    def wap_pay(HttpServletRequest req, HttpServletResponse resp) {
        Integer userId = ServletRequestUtils.getIntParameter(req, _id)
        if (userId == null || users().count($$('_id', userId)) <= 0) {
            return Result.丢失必需参数
        }
        String subject = req.getParameter("subject")
        if (StringUtils.isBlank(subject)) {
            subject = "阿喵抓娃娃-钻石充值"
        }
        String ext = ServletRequestUtils.getStringParameter(req, "ext", '')
        Integer toId = ServletRequestUtils.getIntParameter(req, "to_id", userId)
        toId = toId ?: userId
        if (!userId.equals(toId) && users().count($$('_id', toId)) <= 0) {
            return Result.丢失必需参数
        }
        String return_url = URLDecoder.decode(req.getParameter("return_url") as String, "UTF-8")
        //后台商品ID 支付宝回调无法返回
        Integer itemId = ServletRequestUtils.getIntParameter(req, "item_id")
        def item = shop().findOne(itemId)
        if (item == null || item['cost'] == null || item['count'] == null) {
            logger.error("ali_wap_pay: itemId: ${itemId} invalid")
            return Result.丢失必需参数
        }
        String total_fee = new BigDecimal(item['cost'] as String).movePointLeft(2).toString()
        String out_trade_no = payController.createOrder(userId, toId)+"_${itemId}".toString()
        def privateField = [count: item['count'], award: item['award'] ?: 0, item_id: itemId, ext: ext]
        Order.prepayOrder(out_trade_no, OrderVia.支付宝WAP, privateField)
//todo        AlipayTradeWapPayResponse ali_resp = wapTrade2.pay(subject, out_trade_no, total_fee, return_url)
//        resp.setContentType("text/html; charset=utf-8")
//        resp.getWriter().write(ali_resp.body)
//        resp.getWriter().flush()
    }

    //支付宝回调
    def wap_notify(HttpServletRequest req, HttpServletResponse resp) {
        //获取支付宝POST过来反馈信息
        Map<String, String> params = new HashMap<String, String>()
        logger.debug("ali_wap_notify params :{}",req.getParameterMap())
        Map requestParams = req.getParameterMap()
        for (Iterator iter = requestParams.keySet().iterator(); iter.hasNext();) {
            String name = (String) iter.next()
            String[] values = (String[]) requestParams.get(name)
            String valueStr = ""
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i]
                        : valueStr + values[i] + ","
            }
            //乱码解决，这段代码在出现乱码时使用。如果mysign和sign不相等也可以使用这段代码转化
            //valueStr = new String(valueStr.getBytes("ISO-8859-1"), "gbk");
            params.put(name, valueStr)
        }
        //获取支付宝的通知返回参数，可参考技术文档中页面跳转同步通知参数列表(以下仅供参考)//
        //商户订单号
        String out_trade_no = new String(req.getParameter("out_trade_no").getBytes("ISO-8859-1"), "UTF-8");
        //支付宝交易号
        String trade_no = new String(req.getParameter("trade_no").getBytes("ISO-8859-1"), "UTF-8");
        //交易状态
        String trade_status = new String(req.getParameter("trade_status").getBytes("ISO-8859-1"), "UTF-8");
        //交易金额
        String total_amount = new String(req.getParameter("total_amount").getBytes("ISO-8859-1"), "UTF-8");

        //计算得出通知验证结果
//        boolean verify_result = AlipaySignature.rsaCheckV1(params, AlipayConfig.ALIPAY_PUBLIC_KEY, AlipayConfig.CHARSET, AlipayConfig.SIGNTYPE)
        def out = resp.getWriter()
//        if (verify_result) {//验证成功
//            if (trade_status == TRADE_FINISHED || trade_status == TRADE_SUCCESS) {
//                // 通过商品ID获取返币
//                logger.debug("TRADE_FINISHED : order_id {}", out_trade_no)
//                def record = Order.getOrderById(out_trade_no)
//                def attach = record?.get('ext') ?: [:]
//                Long diamond = attach['count'] as Long ?: 0
//                Long award = attach['award'] as Long ?: 0
//                Integer itemId = attach['item_id'] as Integer ?: null
//                String exten = attach['ext'] as String ?: ''
//
//                def ext = [award: award, item_id: itemId, ext: exten]
//                if (payController.addDiamond(out_trade_no, Double.parseDouble(total_amount), diamond, OrderVia.支付宝WAP.id, trade_no, 'CNY', '', ext)) {
//                    out.print("success")
//                    return
//                }
//            }
//        } else {//验证失败
//            out.print("fail")
//        }
        out.print("success")
    }


}
