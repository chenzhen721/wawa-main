package com.wawa.api.event;

/**
 * @author: jiao.li@ttpod.com
 * Date: 14-4-22 下午6:23
 */
public interface GiftSendListener {
    public abstract void fireEvent(Integer room_id, Integer userId, Integer starId, Integer toId, Integer gift_id, Integer cost, Integer count, Long timestamp);
}
