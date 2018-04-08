package com.wawa.web

import com.mongodb.BasicDBObject
import com.mongodb.ReadPreference
import com.wawa.base.BaseController
import com.wawa.base.anno.Rest
import com.wawa.common.util.MsgDigestUtil
import com.wawa.common.doc.Result
import com.wawa.common.util.BusiExecutor
import com.wawa.common.util.CoreExecutor
import com.wawa.common.util.DateUtil
import com.wawa.common.util.DelayQueueRedis
import com.wawa.common.util.KeyUtils
import com.wawa.common.util.MsgExecutor
import com.wawa.model.Finance
import com.wawa.model.UserType
import com.wawa.api.Web
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest

/**
 *
 * 程序之间 集成 胶水代码
 *
 * date: 13-2-25 下午4:02
 * @author: yangyang.cong@ttpod.com
 */
@Rest
class JavaController extends BaseController{

    private final static String VERSION = "3.6.0";
    /**
     * 后台变更了用户类型之后 回调
     *
     * @param request
     * @return
     */
    static final  Logger logger = LoggerFactory.getLogger(JavaController.class)

     def flushuser(HttpServletRequest request){

        Integer uid = Web.firstNumber(request)

        String id2token = KeyUtils.USER.token(uid)

         String access_token = request.getParameter("access_token")

         if(StringUtils.isBlank(access_token))
             access_token =  userRedis.opsForValue().get(id2token)

        if (access_token!=null){
            def user = users().findOne(uid,new BasicDBObject(finance:1,nick_name:1,priv:1,"star.room_id":1,status:1,mm_no:1))
            Integer priv = user.get("priv") as Integer
            def hashOp  = userRedis.opsForHash()
            String token_key = KeyUtils.accessToken(access_token)
            Map<String, String> session = hashOp.entries(token_key)
            logger.debug('session is {}',session)
            logger.debug('token_key is {}',token_key)
            if (session.isEmpty())
               session = new HashMap<String, String>();

            session.put("_id", uid.toString());
            session.put("priv",priv.toString())
            session.put("nick_name",user.get("nick_name").toString())
            session.put("status", Boolean.TRUE.equals(user.get("status"))?"1":"0")
            session.put("mm_no", String.valueOf(user.get("mm_no")));
            Map finance = (Map) user.get(Finance.finance);
            if (null != finance) {
                Number coin_spend = (Number) finance.get(Finance.coin_spend_total);
                if (null != coin_spend) {
                    session.put("spend", coin_spend.toString());
                }
            }

            if (priv==UserType.主播.ordinal()){
                Number room_id = ((Map) user.get("star"))?.get("room_id") as Number
                if (room_id!=null)
                    session.put("room_id",String.valueOf(room_id.intValue()))
            }

            hashOp.putAll(token_key,(Map)session)

            //mainRedis.opsForHash().putAll(token_key, (Map)session);
            //mainRedis.expire(token_key, THREE_DAY_SECONDS, TimeUnit.SECONDS);
            userRedis.delete(token_key)
            //OAuth2SimpleInterceptor.setSession(session)
            /*def userId = Web.getCurrentUserId();
            logger.info("userId----->:{}",userId)*/
            return  Result.success
        }
        [code: 0]
    }


    /**
     * 刷新礼物缓存
     */
    def refresh_gift_cache(){
        mainRedis.delete(KeyUtils.all_gifts())
        [code:1]
    }

    /**
     * 推送用户
     * @param req
     */
    def push_user_validate(HttpServletRequest req){
        Integer userId = req.getParameter('user_id') as Integer
        if(isTest){
            //publish(KeyUtils.CHANNEL.user(userId), [action: "user.live_check", data_d: [id:userId,'t':System.currentTimeMillis(), msg:'hello', geetest:Boolean.TRUE]])
        }
    }

    def prizepool(){
        [code:1,data:mainRedis.opsForValue().get(KeyUtils.LUCK.prizePool())]
    }


    //监控转发消息线程池数
    def msg_pool_size(HttpServletRequest req)
    {
        def poolSize =   MsgExecutor.poolSize()
        def activeCount =  MsgExecutor.activeCount()
        [code: 1,data:[poolSize:poolSize, activeCount:activeCount, info:MsgExecutor.threadPoolInfoDetail(), server: req.getRemoteAddr()]]
    }
    //监控相关业务并行处理时线程池数
    def business_pool_size(HttpServletRequest req)
    {
        def poolSize =   BusiExecutor.poolSize()
        def activeCount =  BusiExecutor.activeCount()
        [code: 1,data:[poolSize:poolSize, activeCount:activeCount, info:BusiExecutor.threadPoolInfoDetail(), server: req.getRemoteAddr()]]
    }
    //监控相关业务并行处理时线程池数
    def core_pool_size(HttpServletRequest req)
    {
        [code: 1,data:[exeInfo: CoreExecutor.threadPoolInfoDetail(), server: req.getRemoteAddr()]]
    }

    //监控redis指令执行时间
    def redis_time()
    {
        Long l = System.nanoTime()

        String gift_key = KeyUtils.all_gifts()
        mainRedis.opsForValue().get(gift_key)
        double gift_time = (System.nanoTime() - l)/1000000d

        l = System.nanoTime()
        Integer room_id = 2518308
        userRedis.opsForSet().members(KeyUtils.ROOM.users(room_id))
        double view_time = (System.nanoTime() - l)/1000000d

        [code: 1,data: [redis_gift_time:gift_time,redis_view_time:view_time,unit:'us']]
    }

    //监控定时执行时间 crontab 的执行时间
    def timer_info()
    {
       def timerLogs =  rankMongo.getCollection("timer_logs")
               .find(new BasicDBObject(timestamp:[$gt:new Date().clearTime().getTime()]),
                     new BasicDBObject(_id:0,timer_name:1,cat:1,cost_total:1,unit:1))
               .toArray()

      // def threshold = [cat_minute:10*1000L,cat_hour:1*60*1000L,cat_day:8*60L*1000]

       [code: 1,data:timerLogs]
    }

    def version(){
        [code: 1,data:[version:VERSION]]
    }

    def mongo_sec(){
        def timerLogs = rankMongo.getCollection("timer_logs")
        timerLogs.setReadPreference(ReadPreference.secondary())
        [code: 1,data:timerLogs.find(new BasicDBObject(timestamp:[$gt:new Date().clearTime().getTime()]),
                new BasicDBObject(_id:0,timer_name:1,cat:1,cost_total:1,unit:1))
                .toArray()]
    }

    def ip(HttpServletRequest req){
        [code :1, data:Web.getClientIp(req)]
    }

    def header_info(HttpServletRequest request){
        [code :1, data:Web.getHeaderInfo(request)]
    }

    def md5(HttpServletRequest req){
        [code :1, data:[md5:MsgDigestUtil.MD5.digest2HEX(req.getParameter('data') as String ?: '')]]
    }

    /**
     * 聊天机器人消息添加
     * @param req
     */
    def add_msg_to_robot(HttpServletRequest req){
        String key = 'chat:msg:list'
        String msgs = req.getParameter('msgs')
        if(StringUtils.isNotBlank(msgs)){
            String[] msgList = msgs.trim().replace('，',',',).split(',')
            if(msgList != null || msgList.length > 0) {
                msgList.each { String msg ->
                    userRedis.opsForSet().add(key, msg)
                }
            }
        }
        [code : 1, data:[userRedis.opsForSet().members(key)]]
    }

    /**
     * 聊天机器人消息添加
     * @param req
     */
    def del_msg_to_robot(HttpServletRequest req){
        String key = 'chat:msg:list'
        String msg = req.getParameter('msg')
        if(StringUtils.isNotBlank(msg)){
            userRedis.opsForSet().remove(key, msg)
        }
        [code : 1, data:[userRedis.opsForSet().members(key)]]
    }



    def test (){
        DelayQueueRedis testQueue = DelayQueueRedis.generateQueue("test");
        testQueue.offer(new DelayQueueRedis.Task(UUID.randomUUID().toString(), 10*1000, 10*1000+"后执行"));
        testQueue.addListner(new DelayQueueRedis.DelayQueueJobListener(){
            public void doJob(DelayQueueRedis.Task task){
                logger.debug(task.toString() + " 已经从延时队列中转至队列" + "当前时间:" + DateUtil.getFormatDate(DateUtil.DFMT, System.currentTimeMillis()));
                testQueue.offer(new DelayQueueRedis.Task(UUID.randomUUID().toString(), 10*1000, 10*1000+"后执行"));
            }
        })
        //testQueue.clean();
    }
}