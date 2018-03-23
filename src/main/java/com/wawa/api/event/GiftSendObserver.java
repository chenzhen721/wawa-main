package com.wawa.api.event;

import com.wawa.common.util.CoreExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author: jiao.li@ttpod.com
 * Date: 14-4-22 下午6:13
 */
public abstract class GiftSendObserver {
    static final Logger logger = LoggerFactory.getLogger(GiftSendObserver.class);

    private static final List<GiftSendListener> listenerList = new ArrayList<>();

    /**
     *
     * @param room_id 直播间ID
     * @param userId 赠送用户ID
     * @param starId 被主播ID(非赠送给主播 则为NULL)
     * @param toId 被赠送用户ID
     * @param gift_id 礼物ID
     * @param cost 价值
     * @param count 数量
     * @param timestamp 时间
     */
    public static void fireGiftSendEvent(final Integer room_id, final Integer userId, final Integer starId, final Integer toId,
                                         final Integer gift_id, final Integer cost, final Integer count, final Long timestamp) {
        for (final GiftSendListener awardListener : listenerList) {
            CoreExecutor.execute(new Runnable() {
                public void run() {
                    awardListener.fireEvent(room_id, userId, starId, toId, gift_id, cost, count, timestamp);
                }
            });
        }
    }

    public static void addGiftSendListner(GiftSendListener giftSendListener){
        listenerList.add(giftSendListener);
        logger.info("================ add GiftSend Listener ================ ");
        logger.info("Listener size : {}", listenerList.size());
    }
}
