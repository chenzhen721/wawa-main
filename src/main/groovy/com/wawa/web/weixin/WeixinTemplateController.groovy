package com.wawa.web.weixin

import com.wawa.base.anno.Rest
import me.chanjar.weixin.mp.bean.template.WxMpTemplateData
import me.chanjar.weixin.mp.bean.template.WxMpTemplateMessage
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 微信公众号模板消息相关
 */
@Rest
class WeixinTemplateController extends WeixinBaseController {

    public static final Logger logger = LoggerFactory.getLogger(WeixinTemplateController.class)

    /**
     * 发送模板消息
     * @param openid 微信OPENID
     * @param text
     * @throws Exception
     */
    public void send(String openid, String templateId)throws Exception{
        WxMpTemplateMessage templateMessage = WxMpTemplateMessage.builder()
                                                .toUser(openid)
                                                    .templateId(templateId).build();
        templateMessage.addWxMpTemplateData(
                new WxMpTemplateData("first", '', "#FF00FF"));
        templateMessage.addWxMpTemplateData(
                new WxMpTemplateData("remark", '', "#FF00FF"));
        templateMessage.setUrl(" ");
        String msgId = wxMpService.getTemplateMsgService().sendTemplateMsg(templateMessage);
    }
}