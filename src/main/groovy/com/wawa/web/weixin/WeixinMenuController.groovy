package com.wawa.web.weixin

import com.wawa.base.anno.Rest
import me.chanjar.weixin.common.api.WxConsts
import me.chanjar.weixin.common.bean.menu.WxMenu
import me.chanjar.weixin.common.bean.menu.WxMenuButton
import me.chanjar.weixin.mp.bean.menu.WxMpMenu
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * 微信公众号菜单相关
 * 参考
 * https://github.com/Wechat-Group/weixin-java-tools/wiki/MP_%E8%87%AA%E5%AE%9A%E4%B9%89%E8%8F%9C%E5%8D%95%E7%AE%A1%E7%90%86
 */
@Rest
class WeixinMenuController extends WeixinBaseController {

    public static final Logger logger = LoggerFactory.getLogger(WeixinMenuController.class)

    /**
     * 创建菜单
     */
    def create(HttpServletRequest request, HttpServletResponse response) {
        WxMenu wxMenu = new WxMenu();
        WxMenu menu = new WxMenu();
        WxMenuButton button1 = new WxMenuButton();
        button1.setType(WxConsts.MenuButtonType.CLICK);
        button1.setName(">抓娃娃<");
        button1.setUrl("http://www.17laihou.com/?union_id=wawa_kuailai_gzh")

/*        WxMenuButton button2 = new WxMenuButton();
        button2.setType(WxConsts.MenuButtonType.MINIPROGRAM);
        button2.setName("小程序");
        button2.setAppId("wx286b93c14bbf93aa");
        button2.setPagePath("pages/lunar/index.html");
        button2.setUrl("http://mp.weixin.qq.com");
        menu.getButtons().add(button2);
*/
        WxMenuButton button2 = new WxMenuButton();
        button2.setName("更多钻石");
        button2.setType(WxConsts.MenuButtonType.SCANCODE_PUSH)

        WxMenuButton button3 = new WxMenuButton();
        button3.setName("常见问题");

        WxMenuButton button31 = new WxMenuButton();
        button31.setType(WxConsts.MenuButtonType.VIEW);
        button31.setName("常见问题");

        WxMenuButton button32 = new WxMenuButton();
        button32.setType(WxConsts.MenuButtonType.VIEW);
        button32.setName("充值优惠");

        WxMenuButton button33 = new WxMenuButton();
        button33.setType(WxConsts.MenuButtonType.CLICK);
        button33.setName("抓娃娃玩法");

        button3.getSubButtons().add(button31);
        button3.getSubButtons().add(button32);
        button3.getSubButtons().add(button33);

        menu.getButtons().add(button1);
        menu.getButtons().add(button2);
        menu.getButtons().add(button3);
        wxMpService.getMenuService().menuCreate(wxMenu);
    }

    def get(HttpServletRequest request){
        WxMpMenu wxMenu = wxMpService.getMenuService().menuGet();
        System.out.println(wxMenu.toJson());
    }
}