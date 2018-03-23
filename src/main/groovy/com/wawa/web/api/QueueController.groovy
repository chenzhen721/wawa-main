package com.wawa.web.api

import com.wawa.api.Web
import com.wawa.base.anno.RestWithSession
import com.wawa.common.doc.Result
import com.wawa.common.util.DelayQueueRedis
import com.wawa.common.util.KeyUtils
import com.wawa.model.*
import com.wawa.base.BaseController
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest

import static com.wawa.common.doc.MongoKey.*

/**
 * 房间预约队列
 */
@RestWithSession
class QueueController extends BaseController {

    public static final Logger logger = LoggerFactory.getLogger(QueueController.class)
    private static final Integer MAX_LIMIT = 10 //队列最大长度

    //用户排队
    def take(HttpServletRequest req){
        Integer roomId = Web.roomId(req)
        Integer userId = Web.getCurrentUserId()
        //是否已经预约过(包括其他房间)
        String queueRedisKey = KeyUtils.CATCHU.room_queue(roomId)
        String isQueueRedisKey = KeyUtils.CATCHU.is_queue(userId)
        if(!liveRedis.opsForValue().setIfAbsent(isQueueRedisKey, roomId.toString())){
            return Result.用户已在其他房间排队
        }
        if(liveRedis.opsForZSet().size(queueRedisKey) >= MAX_LIMIT){
            return Result.房间排队人数已满
        }
        if(liveRedis.opsForZSet().add(queueRedisKey, userId.toString(), System.currentTimeMillis())){
            //TODO 通知房间内其他用户更新队列信息
            notifyRoom(roomId)
        }
        return Result.success
    }

    //取消排队
    def cancel(HttpServletRequest req){
        Integer roomId = Web.roomId(req)
        Integer userId = Web.getCurrentUserId()

        String queueRedisKey = KeyUtils.CATCHU.room_queue(roomId)
        String isQueueRedisKey = KeyUtils.CATCHU.is_queue(userId)
        liveRedis.delete(isQueueRedisKey)
        if(liveRedis.opsForZSet().remove(queueRedisKey, userId.toString()) > 0){
            //TODO 通知房间内其他用户更新队列信息
            notifyRoom(roomId)
        }
        return Result.success
    }

    def test_next(HttpServletRequest req){
        Integer roomId = Web.roomId(req)
        [code : 1, data: next(roomId)]
    }

    private static final long EXPIRES_TIME = 6000L

    //用户下机后，通知队列内排队用户加入到候选人
    public static void next(final Integer roomId){
        final String queueRedisKey = KeyUtils.CATCHU.room_queue(roomId)
        //队列中为空 则无人排队
        if(Web.liveRedis.opsForZSet().size(queueRedisKey) <= 0){
            return
        }
        DelayQueueRedis popUserOfRoomQueue = DelayQueueRedis.generateQueue("pop:user:room:queue:"+roomId);
        final DelayQueueRedis.Task task = new DelayQueueRedis.Task('pop_user_queue', 0, "排队用户")
        //将任务加入延迟队列中
        popUserOfRoomQueue.offer(task);
        //注册延迟任务callback
        popUserOfRoomQueue.addListner(new DelayQueueRedis.DelayQueueJobListener() {

            public void doJob(DelayQueueRedis.Task doTask) {
                //TODO 机器上是否有其他玩家

                //TODO 候选人是否为上机状态
                String queueCandidateRedisKey = KeyUtils.CATCHU.room_queue_candidate(roomId)
                String candidataUser = Web.liveRedis.opsForValue().get(queueCandidateRedisKey)
                if(candidataUser != null){
                    //TODO
                }
                //拿到队列中最早的第一名用户
                Set<String> userOfQueue = Web.liveRedis.opsForZSet().range(queueRedisKey, 0, 0)
                if (userOfQueue != null && userOfQueue.size() > 0) {
                    String userId = userOfQueue[0]
                    Web.liveRedis.delete(KeyUtils.CATCHU.is_queue(userId))
                    Web.liveRedis.opsForZSet().remove(queueRedisKey, userId)
                    //设置为候选人
                    Web.liveRedis.opsForValue().set(queueCandidateRedisKey, userId)
                    //TODO 通知候选人 等待其确定上机
                    //TODO 通知房间内其他用户更新队列信息
                    //如果队列中还有人则通知下一位用户
                    if(Web.liveRedis.opsForZSet().size(queueRedisKey) > 0){
                        popUserOfRoomQueue.offer(new DelayQueueRedis.Task('pop_user_queue', EXPIRES_TIME, "排队用户"));
                    }
                }
            }
        })

    }


    //是否为候选人 供开始抓接口调用
    public static Boolean isCandidate(Integer roomId, Integer userId){
        String queueCandidateRedisKey = KeyUtils.CATCHU.room_queue_candidate(roomId)
        String candidataUser = Web.liveRedis.opsForValue().get(queueCandidateRedisKey)
        if(candidataUser != null){
            return userId.equals(candidataUser.toString())
        }
        return Boolean.FALSE
    }

    //房间队列信息
    def info(HttpServletRequest req){
        Integer roomId = Web.roomId(req)
        Integer userId = Web.getCurrentUserId()
        String queueRedisKey = KeyUtils.CATCHU.room_queue(roomId)
        //当前用户所在队列中的位置  zset rank是index
        def position = liveRedis.opsForZSet().rank(queueRedisKey, userId.toString())  as Integer
        logger.debug("queue position : {}", position)
        //当前用户如果没在队列 则队列长度
        if(position < 0){
            position = liveRedis.opsForZSet().size(queueRedisKey)
        }
        return [code:Result.success.code, data:[position:position]]
    }

    private notifyRoom(Integer roomId){
        //TODO
    }
}
