package com.wawa.web.weixin

import com.wawa.base.BaseController
import com.wawa.base.anno.Rest
import me.chanjar.weixin.mp.api.WxMpInMemoryConfigStorage
import me.chanjar.weixin.mp.api.WxMpService
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 微信公众号基础类
 */
@Rest
class WeixinBaseController extends BaseController {

    public static final Logger logger = LoggerFactory.getLogger(WeixinBaseController.class)

    private static final String TOKEN = "a6e4a254c34436d8b3ae48fae66387e0"
    private static final String AES_KEY = "9CdJafY1EkU1OWrBWxhRByhgYTKbgVvpM4A4EoeVZiz"
    private static final String APP_ID = "wx45d43a50adf5a470"
    private static final String APP_KEY = "40e8dc2daac9f04bfbac32a64eb6dfff"

    protected static WxMpInMemoryConfigStorage wxMpConfigStorage = new WxMpInMemoryConfigStorage();
    protected static WxMpService wxMpService = new WxMpServiceImpl();

    static {
        wxMpConfigStorage.setAppId(APP_ID); // 设置微信公众号的appid
        wxMpConfigStorage.setSecret(APP_KEY); // 设置微信公众号的app corpSecret
        wxMpConfigStorage.setToken(TOKEN)
        wxMpConfigStorage.setAesKey(AES_KEY)
        wxMpService.setWxMpConfigStorage(wxMpConfigStorage);
    }

    

}