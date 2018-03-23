package com.wawa.web.weixin

import com.wawa.base.anno.Rest
import me.chanjar.weixin.mp.bean.kefu.WxMpKefuMessage
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 微信公众号客服消息相关
 */
@Rest
class WeixinMsgController extends WeixinBaseController {

    public static final Logger logger = LoggerFactory.getLogger(WeixinMsgController.class)

    /**
     * 客服消息-发送文本
     * @param openid 微信OPENID
     * @param text
     * @throws Exception
     */
    public void sendTxt(String openid, String text)throws Exception{
        WxMpKefuMessage message = WxMpKefuMessage.TEXT().toUser(openid).content(text).build();
        wxMpService.getKefuService().sendKefuMessage(message);
    }

    /**
     * 客服消息-发送图文
     * @param openid 微信OPENID
     * @param article 图文信息
     * @throws Exception
     */
    public void sendImg(String openid, WxMpKefuMessage.WxArticle article)throws Exception{
        WxMpKefuMessage message = WxMpKefuMessage.NEWS().toUser(openid).addArticle(article).build();
        wxMpService.getKefuService().sendKefuMessage(message);
    }
}