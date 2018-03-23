package com.wawa.web.weixin

import com.wawa.base.anno.Rest
import me.chanjar.weixin.common.api.WxConsts
import me.chanjar.weixin.common.session.WxSessionManager
import me.chanjar.weixin.mp.api.WxMpMessageHandler
import me.chanjar.weixin.mp.api.WxMpMessageRouter
import me.chanjar.weixin.mp.api.WxMpService
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutTextMessage
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.annotation.PostConstruct
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * 微信公众号消息相关
 */
@Rest
class WeixinRouterController extends WeixinBaseController {

    public static final Logger logger = LoggerFactory.getLogger(WeixinRouterController.class)
    WxMpMessageRouter  wxMpMessageRouter;

    @PostConstruct
    public void init() {
        //消息路由
        WxMpMessageRouter  wxMpMessageRouter = new WxMpMessageRouter(wxMpService);
        wxMpMessageRouter.rule()
                .event(WxConsts.EventType.SUBSCRIBE)
                .handler(subscribeHandler)
                .end()
                .rule()
                .event(WxConsts.EventType.UNSUBSCRIBE)
                .handler(unsubscribeHandler)
                .end();
    }
    /**
     * 微信事件回调接口(公众后台配置)
     * TODO 启用回调，则后台配置的菜单和自动回复都失效， 必须自行实现
     * @param req
     */
    def event_callback(HttpServletRequest request, HttpServletResponse response) {
        String signature = request.getParameter("signature");
        String nonce = request.getParameter("nonce");
        String timestamp = request.getParameter("timestamp");

        response.setContentType("text/html;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);

        if (!wxMpService.checkSignature(timestamp, nonce, signature)) {
            // 消息签名不正确，说明不是公众平台发过来的消息
            response.getWriter().println("非法请求");
            return;
        }

        String echostr = request.getParameter("echostr");
        if (StringUtils.isNotBlank(echostr)) {
            // 说明是一个仅仅用来验证的请求，回显echostr
            response.getWriter().println(echostr);
            return;
        }

        String encryptType = StringUtils.isBlank(request.getParameter("encrypt_type")) ?
                "raw" :
                request.getParameter("encrypt_type");

        WxMpXmlMessage inMessage = null;

        if ("raw".equals(encryptType)) {
            // 明文传输的消息
            inMessage = WxMpXmlMessage.fromXml(request.getInputStream());
        } else if ("aes".equals(encryptType)) {
            // 是aes加密的消息
            String msgSignature = request.getParameter("msg_signature");
            inMessage = WxMpXmlMessage.fromEncryptedXml(request.getInputStream(), wxMpConfigStorage, timestamp, nonce, msgSignature);
        } else {
            response.getWriter().println("不可识别的加密类型");
            return;
        }

        WxMpXmlOutMessage outMessage = wxMpMessageRouter.route(inMessage);

        if (outMessage != null) {
            if ("raw".equals(encryptType)) {
                response.getWriter().write(outMessage.toXml());
            } else if ("aes".equals(encryptType)) {
                response.getWriter().write(outMessage.toEncryptedXml(wxMpConfigStorage));
            }
            return;
        }
    }

    WxMpMessageHandler subscribeHandler = new WxMpMessageHandler() {
        @Override public WxMpXmlOutMessage handle(WxMpXmlMessage wxMessage, Map<String, Object> context, WxMpService wxMpService, WxSessionManager sessionManager) {
            WxMpXmlOutTextMessage m = WxMpXmlOutMessage.TEXT().content("测试加密消息").build();
            //TODO
            return m;
        }
    };

    WxMpMessageHandler unsubscribeHandler = new WxMpMessageHandler() {
        @Override public WxMpXmlOutMessage handle(WxMpXmlMessage wxMessage, Map<String, Object> context, WxMpService wxMpService, WxSessionManager sessionManager) {
            WxMpXmlOutTextMessage m = WxMpXmlOutMessage.TEXT().content("测试加密消息").build();
            //TODO
            return m;
        }
    };
}