package com.wawa.common.util;

import com.wawa.base.StaticSpring;
import groovy.transform.CompileStatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 *  redis延迟队列
 *
 *  Usage:
 *
 *  //初始化一个队列
 *  DelayQueueRedis testQueue = DelayQueueRedis.generateQueue("test");
 *
 *  //新建任务
 *  Task task = new DelayQueueRedis.Task(UUID.randomUUID().toString(), 10*1000, 10*1000+"后执行")
 *
    //添加任务到延迟队列中
    testQueue.offer(task);

    //订阅延迟通知， 执行任务
    testQueue.addListner(new DelayQueueRedis.DelayQueueJobListener(){
         public void doJob(DelayQueueRedis.Task task){
             logger.debug(task.toString() + " 已经从延时队列中转至队列" + "当前时间:" + DateUtil.getFormatDate(DateUtil.DFMT, System.currentTimeMillis()));
             //DO SOMETHING...
        }
    })

 */
@CompileStatic
public class DelayQueueRedis {

    public static final Logger logger = LoggerFactory.getLogger(DelayQueueRedis.class);

    public static final StringRedisTemplate mainRedis = (StringRedisTemplate) StaticSpring.get("mainRedis");

    private final static String QUEUE_PREFIX = "delay:queue:";
    private final String queue_key;
    private final static Map<String, DelayQueueRedis> queues = new HashMap<>();

    /**
     * 队列业务名称
     * @param queueName
     * @return
     */
    public static synchronized DelayQueueRedis generateQueue(String queueName){
        if(queues.containsKey(queueName))
            return queues.get(queueName);
        DelayQueueRedis queue = new DelayQueueRedis(queueName);
        queues.put(queueName, queue);
        return queue;
    }

    private DelayQueueRedis(String queueName) {
        this.queue_key = QUEUE_PREFIX + queueName;
        transferFromDelayQueue();
    }

    private List<DelayQueueJobListener> listenerList = new ArrayList<>();

    public void addListner(DelayQueueJobListener delayQueueJobListener){
        listenerList.add(delayQueueJobListener);
    }

    // 任务处理队列
    private void addToTaskQue(final Task task){
        try {
            for (final DelayQueueJobListener listener : listenerList) {
                BusiExecutor.execute(new Runnable() {
                    public void run() {
                        listener.doJob(task);
                    }
                });
            }
        } catch (Exception e) {
            logger.error("addToTaskQue Exception : {}", e);
        }
    }

    //添加task到延迟队列
    public void offer(Task task){
        mainRedis.opsForZSet().add(queue_key, JSONUtil.beanToJson(task), System.currentTimeMillis() + task.getDelayTime());
        logger.debug(task.toString() + " >>>>>加入延时队列 " + DateUtil.getFormatDate(DateUtil.DFMT, System.currentTimeMillis()));
    }

    public void clean(){
        mainRedis.delete(queue_key);
    }

    private void transferFromDelayQueue(){
        BusiExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try{
                    while(true){
                        Set<ZSetOperations.TypedTuple<String>> items = mainRedis.opsForZSet().rangeWithScores(queue_key, 0, 0);
                        if(items != null && !items.isEmpty()){
                            ZSetOperations.TypedTuple<String> item = items.iterator().next();
                            //logger.debug("time = "+ System.currentTimeMillis() +" "+ item.getScore());
                            //logger.debug("time = "+ (System.currentTimeMillis() - item.getScore()));
                            if(System.currentTimeMillis() >= item.getScore().longValue()){
                                String task = item.getValue();
                                // 从延时队列中移除
                                if(mainRedis.opsForZSet().remove(queue_key, task) == 1){
                                    addToTaskQue(JSONUtil.jsonToBean(item.getValue(),Task.class)); //任务推入延时队列
                                }
                            }
                        }
                        Thread.sleep(100);
                    }
                }catch (Exception e){
                    logger.error("transferFromDelayQueue error : {}", e);
                }
            }
        });
        logger.debug("延时队列ready.......");
    }

    public static abstract class DelayQueueJobListener{
        public abstract void doJob(Task task);
    }

    public static class Task{
        // 任务id
        private String id ;
        // 延迟时间
        private long delayTime;
        // 描述
        private String desc;

        public Task(){}

        /**
         * @param id 任务ID
         * @param delayTime 延迟执行的时间(毫秒)
         * @param desc 任务描述
         */
        public Task(String id, long delayTime, String desc){
            this.id = id;
            this.delayTime = delayTime;
            this.desc = desc;
        }

        public String getId() {
            return id;
        }
        public long getDelayTime() {
            return delayTime;
        }
        public String getDesc() {
            return desc;
        }

        @Override
        public String toString() {
            return "Task [id=" + getId() + ", time=" + getDelayTime() + ", desc=" + getDesc() + "]";
        }
    }

    public static void main(String[] args) {
        DelayQueueRedis testQueue = new DelayQueueRedis("test");

        //新建任务
        Task task = new DelayQueueRedis.Task(UUID.randomUUID().toString(), 10*1000, "10秒后执行");

        //添加任务到延迟队列中
        testQueue.offer(task);

        //订阅延迟通知， 执行任务
        testQueue.addListner(new DelayQueueRedis.DelayQueueJobListener(){
            public void doJob(DelayQueueRedis.Task task){
                logger.debug(task.toString() + " 已经从延时队列中转至队列" + "当前时间:" + DateUtil.getFormatDate(DateUtil.DFMT, System.currentTimeMillis()));
                //DO SOMETHING...
            }
        });
    }

}
