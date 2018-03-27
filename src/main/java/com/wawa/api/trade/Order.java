package com.wawa.api.trade;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;
import com.wawa.base.StaticSpring;
import com.wawa.model.OrderStatus;
import com.wawa.model.OrderVia;
import groovy.transform.CompileStatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

import static com.wawa.common.doc.MongoKey._id;
import static com.wawa.common.doc.MongoKey.timestamp;
import static com.wawa.common.util.WebUtils.$$;


/**
 * 充值订单处理
 * 用户支持漏单，补单，延迟等问题
 */
@CompileStatic
public abstract class Order {

    final static Logger logger = LoggerFactory.getLogger(Order.class) ;

    @Resource
    public static WriteConcern writeConcern;

    public static final MongoTemplate logMongo = (MongoTemplate) StaticSpring.get("logMongo");

    private final static Long MINUTES_MILLS = 60 * 1000l;

    private static DBCollection orderLogs(){
        return logMongo.getCollection("order_logs");
    }
    /**
     * 预处理充值订单
     * @param order_id
     * @param orderVia
     */
    public static void prepayOrder(String order_id, OrderVia orderVia){
        prepayOrder(order_id, orderVia, null);
    }

    /**
     * 预处理充值订单
     * @param order_id
     * @param orderVia
     */
    public static void prepayOrder(String order_id, OrderVia orderVia, Map ext){
        try{
            BasicDBObject orderInfo = $$(_id, order_id);
            Long times = System.currentTimeMillis();
            orderInfo.put(timestamp, times);
            orderInfo.put("checkpoint",(times + (5 * MINUTES_MILLS)));
            orderInfo.put("checkcount",0);
            orderInfo.put("via", orderVia.getId());
            orderInfo.put("status", OrderStatus.支付开始.ordinal());
            orderInfo.put("ext", ext);
            orderLogs().insert(orderInfo) ;
        }catch (Exception e){
            logger.error("prepayOrder Exception : {}", e);
        }
    }

    public static DBObject getOrderById(String order_id) {
        return orderLogs().findOne($$(_id, order_id));
    }

    /**
     * 充值支付完成
     * @param order_id
     */
    public static void completeOrder(String order_id){
        try{
            /*BasicDBObject orderInfo = $$("modify_time", System.currentTimeMillis());
            orderInfo.append("status", OrderStatus.支付完成.ordinal());
            orderLogs().update($$(_id, order_id), $$($set, orderInfo)) ;*/
            orderLogs().remove($$(_id, order_id));
        }catch (Exception e){
            logger.error("completeOrder Exception : {}", e);
        }
    }

    public static final Map<String,OrderProcess> orderProcessList = new HashMap<>();
    static {
        orderProcessList.put(OrderVia.微信H5.getId(), new WeiXinOrderProcess());
        orderProcessList.put(OrderVia.微信WAP.getId(), new WeiXinOrderProcess());
        orderProcessList.put(OrderVia.微信手机.getId(), new WeiXinOrderProcess());
        /*orderProcessList.put(OrderVia.支付宝WAP.getId(), new AliPayOrderProcess());
        orderProcessList.put(OrderVia.支付宝手机.getId(), new AliPayOrderProcess());
        orderProcessList.put(OrderVia.支付宝PC.getId(), new AliPayOrderProcess());*/
    }

    public static DelayOrdeInfo findDelayOrder(String order_id, String via){
        OrderProcess orderProcess = orderProcessList.get(via);
        if(orderProcess == null) return null;
        try{
            return orderProcess.doFixOrder(order_id);
        }catch (Exception e){
            logger.error("findDelayOrder order_id : {}. Exception : {}", order_id, e);
        }
        return null;
    }



}

interface OrderProcess{
    public DelayOrdeInfo doFixOrder(String order_id);
}

/**
 * 微信查询订单
 */
class WeiXinOrderProcess implements OrderProcess{

    public DelayOrdeInfo doFixOrder(String order_id){
        Map<String, String> res = WeixinOrderQuery.query(order_id);
        DelayOrdeInfo delayOrdeInfo = new DelayOrdeInfo(100);
        if(res == null) {
            return null;
        }
        if ("SUCCESS".equals(res.get("return_code"))) {
            String resultCode = res.get("result_code");
            String trade_state = res.get("trade_state");
            if ("SUCCESS".equals(resultCode) && "SUCCESS".equals(trade_state)) {
                String out_trade_no = res.get("out_trade_no");//商户订单号
                if(out_trade_no.equals(order_id)){
                    String total_fee = res.get("total_fee");
                    String fee_type = res.get("fee_type");
                    String transaction_id = res.get("transaction_id");
                    String attach = res.get("attach");
                    Double cny = Integer.valueOf(total_fee).doubleValue() / 100;
                    delayOrdeInfo.setOrderId(order_id);
                    delayOrdeInfo.setCny(cny);
                    delayOrdeInfo.setTradeNo(transaction_id);
                    delayOrdeInfo.setAttach(attach);
                    delayOrdeInfo.setFeeType(fee_type);
                    return delayOrdeInfo;
                }
            }
        }
        return null;
    }
}

/**
 * 支付宝
 */
class AliPayOrderProcess implements OrderProcess{

    public DelayOrdeInfo doFixOrder(String order_id){
        Map<String, String> res = AliOrderQuery.query(order_id);
        if(res == null || res.size() == 0){
            return null;
        }
        String status = res.get("trade_status");
        if ("TRADE_FINISHED".equals(status) || "TRADE_SUCCESS".equals(status)) {//交易成功
            String out_trade_no = res.get("out_trade_no");
            String total_fee = res.get("total_fee");
            String trade_no = res.get("trade_no");
            String buyer_email = res.get("buyer_email");
            if(out_trade_no.equals(order_id)){
                DelayOrdeInfo delayOrdeInfo = new DelayOrdeInfo(100);
                delayOrdeInfo.setOrderId(order_id);
                delayOrdeInfo.setCny(Double.parseDouble(total_fee));
                delayOrdeInfo.setTradeNo(trade_no);
                delayOrdeInfo.setAttach(buyer_email);
                return delayOrdeInfo;
            }
        }
        return null;

    }
}