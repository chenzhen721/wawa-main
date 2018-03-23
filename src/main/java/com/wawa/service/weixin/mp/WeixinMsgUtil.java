package com.wawa.service.weixin.mp;

import me.chanjar.weixin.common.api.WxConsts;
import me.chanjar.weixin.common.session.WxSessionManager;
import me.chanjar.weixin.mp.api.WxMpInMemoryConfigStorage;
import me.chanjar.weixin.mp.api.WxMpMessageHandler;
import me.chanjar.weixin.mp.api.WxMpMessageRouter;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl;
import me.chanjar.weixin.mp.bean.kefu.WxMpKefuMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutTextMessage;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 微信公众号发送客服消息
 *
 * 详细DOC 参考:
 * https://github.com/Wechat-Group/weixin-java-tools/wiki/%E5%85%AC%E4%BC%97%E5%8F%B7%E5%BC%80%E5%8F%91%E6%96%87%E6%A1%A3
 */
public class WeixinMsgUtil {

    static WxMpInMemoryConfigStorage config = new WxMpInMemoryConfigStorage();
    static WxMpService wxService = new WxMpServiceImpl();

    public static void main(String args[])throws Exception {
        config.setAppId("wx45d43a50adf5a470"); // 设置微信公众号的appid
        config.setSecret("40e8dc2daac9f04bfbac32a64eb6dfff"); // 设置微信公众号的app corpSecret
        //config.setToken("5_J4UpE0-PmcNulfMwcVHg08t2cqaw0SRPRRBX17zWfH6fZ6nS2BuikPnkqKv3cA8stDxrZyTDEkNbDrN8MO9f7m4WRbz1F52-vnJSZ3hDMRQwF-bs7aUbFMXMSRj3J9r0ZzyROBVO_ag5s1spLJWfAFARPC"); // 设置微信公众号的token
        wxService.setWxMpConfigStorage(config);

        //sendTxt(openid, "hi");

        WxMpKefuMessage.WxArticle article = new WxMpKefuMessage.WxArticle();
        article.setUrl("http://www.17laihou.com");
        article.setPicUrl("https://mmbiz.qpic.cn/mmbiz_jpg/kGE3RectqDy33pZv86iciaoNL692P8FdaNKDVyMz09MsZw4v3gSIBzAvTUYvPMicPwickic6JeAwATxkZ717IuG0ekA/0?wx_fmt=jpeg");
        article.setDescription("你的好友抓中了个大娃娃很开心，1个红包送给你");
        article.setTitle("周泽新送你1大个红包");
        sendImg("ok2Ip1hYtZOjKzacYzr06gskuNcs",article);
        //sendImg("ok2Ip1ke1-QxuicU9gpRqZ1tA10A",article);
        //sendImg("ok2Ip1pFWWphfjGld52014oXmEbc",article);
        //sendImg("ok2Ip1rVmmkypvPCHwGS3p6FDPUA",article);
    }

    /**
     * 发送文本
     * @param openid 微信OPENID
     * @param text
     * @throws Exception
     */
    public static void sendTxt(String openid, String text)throws Exception {
        WxMpKefuMessage message = WxMpKefuMessage.TEXT().toUser(openid).content(text).build();
        wxService.getKefuService().sendKefuMessage(message);
    }

    /**
     * 发送图文
     * @param openid 微信OPENID
     * @param article 图文信息
     * @throws Exception
     */
    public static void sendImg(String openid, WxMpKefuMessage.WxArticle article)throws Exception {
        WxMpKefuMessage message = WxMpKefuMessage.NEWS().toUser(openid).addArticle(article).build();
        wxService.getKefuService().sendKefuMessage(message);
    }

    /**
     * 微信消息路由
     * https://github.com/Wechat-Group/weixin-java-tools/wiki/MP_%E5%BE%AE%E4%BF%A1%E6%B6%88%E6%81%AF%E8%B7%AF%E7%94%B1%E5%99%A8
     */
    public static void msgRouter(HttpServletRequest req) throws Exception {
        WxMpXmlMessage message = WxMpXmlMessage.fromXml(req.getInputStream());

        WxMpMessageHandler subscribeHandler = new WxMpMessageHandler() {
            @Override
            public WxMpXmlOutMessage handle(WxMpXmlMessage wxMessage, Map<String, Object> context, WxMpService wxMpService, WxSessionManager sessionManager) {
                WxMpXmlOutTextMessage m = WxMpXmlOutMessage.TEXT().content("测试加密消息").build();
                //TODO
                return m;
            }
        };

        WxMpMessageHandler unsubscribeHandler = new WxMpMessageHandler() {
            @Override
            public WxMpXmlOutMessage handle(WxMpXmlMessage wxMessage, Map<String, Object> context, WxMpService wxMpService, WxSessionManager sessionManager) {
                WxMpXmlOutTextMessage m = WxMpXmlOutMessage.TEXT().content("测试加密消息").build();
                //TODO
                return m;
            }
        };

        WxMpMessageRouter  wxMpMessageRouter = new WxMpMessageRouter(wxService);
        wxMpMessageRouter.rule()
                                .event(WxConsts.EventType.SUBSCRIBE)
                                .handler(subscribeHandler)
                                .end()
                                .rule()
                                .event(WxConsts.EventType.UNSUBSCRIBE)
                                .handler(unsubscribeHandler)
                                .end()
                                .route(message);
    }
}
