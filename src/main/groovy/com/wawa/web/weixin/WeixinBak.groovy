package com.wawa.web.weixin

import com.mongodb.BasicDBObject
import com.mongodb.DBCollection
import com.mongodb.DBObject
import com.wawa.AppProperties
import com.wawa.base.anno.Rest
import com.wawa.common.util.JSONUtil
import com.wawa.common.util.RedisLock
import com.wawa.base.BaseController
import com.wawa.api.UserWebApi
import com.wawa.service.ipay.HttpUtils
import com.wawa.service.weixin.entity.Article
import com.wawa.service.weixin.entity.News
import com.wawa.service.weixin.entity.WeXinMenu
import com.wawa.service.weixin.util.WXUtil

/*import com.weixin.entity.Article
import com.weixin.entity.News
import com.weixin.entity.WeXinMenu
import com.weixin.util.WXUtil*/
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.concurrent.TimeUnit

import static com.wawa.common.util.WebUtils.$$
import static com.wawa.common.doc.MongoKey.*

/**
 * 微信相关 (公众号)
 */
@Rest
class WeixinBak extends BaseController {

    public static final Logger logger = LoggerFactory.getLogger(WeixinBak.class)
    public static final boolean isTest = AppProperties.get("api.domain").contains("test.");

    private static final String WEIXIN_URL = "https://api.weixin.qq.com/cgi-bin/";
    private static final String REDIRECT_URL = h5_SHOW_URL

    private static final String PIC_URL = 'http://img.sumeme.com/40/0/1474426822440.jpg'
    private static final Long TWO_DAY_MILLIS = 172800000L
    private static final String TITLE = '【xiaoღ阳】MM开播啦，美了美了~~~'
    private static final String WELCOME_CONTENT = '我在这里等你，赶紧来与我互动吧~~'
    private static final String TOKEN = "a6e4a254c34066d8b3ae48fae66387e0"
    private static final String SHOWQRCODE_URL = 'https://mp.weixin.qq.com/cgi-bin/showqrcode?ticket='
    private static final Integer SCAN = 1
    private static final Integer SUBSCRIBE = 2
    private static final Integer CLICK = 3
    private static final Integer VIEW = 4
    private static final Integer TEXT = 5

    private static Integer RETRY = 3
    private static Map errorCode = initErrorCode()
    private static Map<String, Integer> eventMap = ['SCAN': 1, 'subscribe': 2, 'CLICK': 3, 'VIEW': 4, 'TEXT': 5]

    def DBCollection weixin_event_logs() { return unionMongo.getCollection('weixin_event_logs') }

    def DBCollection channel() { return adminMongo.getCollection('channels') }

    /**
     * 微信事件回调接口(公众后台配置)
     * @param req
     */
    def event_callback(HttpServletRequest req, HttpServletResponse resp) {
        RETRY = 0
        logger.debug("Recv event_callback params : {}", req.getParameterMap())
        String appId = req['app_id']
        String defaultUnion = req['default_union']
        String appSecret = getAppSecret(appId)
        Boolean flag = this.validationReferer(req, resp)
        if (!flag || appSecret == '') {
            return [code: 0]
        }
        def body = req.getReader().getText()
        if (StringUtils.isNotEmpty(body)) {
            Map<String, String> data = WXUtil.parseXml(body);
            if (data != null && data.size() > 0) {
                data.put('app_id', appId)
                data.put('app_secret', appSecret)
                data.put('default_union', defaultUnion)
                logger.debug('验证通过 data is {}', data)
                Integer event = eventMap.get(data['Event'])
                String text = data['MsgType']
                if (text && text == 'text') {
                    event = 5
                }
                switch (event) {
                    case SCAN:
                        this.scan(data)
                        break
                    case SUBSCRIBE:
                        this.subscribe(data)
                        break
                    case CLICK:
                        break
                    case VIEW:
                        this.view(data)
                        break
                    case TEXT:
                        this.text(data)
                        break
                    default:
                        logger.debug('event is {},eventMap is {}', event, eventMap)
                        break
                }
            }
            saveOpenId(data)
        }
    }

    /**
     * 创建场景二维码
     * @param req
     * @return
     */
    def qrcode(HttpServletRequest req) {
        RETRY = 0
        logger.debug("Recv qrcode params : {}", req.getParameterMap())
        String sceneId = req['sence_id']
        String appId = req['app_id']
        String appSecret = getAppSecret(appId)
        if (appSecret == '') {
            logger.debug('app secret is not found in DB')
            return [code: 0, data: 'app secret is empty ...']
        }
        String access_token = getAccessToken(appId, appSecret)
        logger.debug('access_token is {}', access_token)
        String requestUrl = WEIXIN_URL + "qrcode/create?access_token=${access_token}".toString()
        Map obj = new HashMap();
        obj.put('action_name', 'QR_LIMIT_SCENE')
        obj.put('action_info', ["scene": ["scene_id": sceneId]])
        Map params = new HashMap()
        params.put('sendObj', obj)
        Map respMap = postWX('POST', requestUrl, params, appId, appSecret)
        String ticket = respMap['ticket']
        logger.debug('ticket is {}', ticket)
        return [code: 1, data: [img_url: SHOWQRCODE_URL + URLEncoder.encode(ticket, "utf-8")]]
    }

    /**
     * 扫描
     */
    private void scan(Map<String, String> data) {
        // 暂时没业务,是否需要统计所有扫描人数

    }

    /**
     * 关注
     * @param data
     */
    private void subscribe(Map<String, String> data) {
        String senceId = data['EventKey'].replace('qrscene_', '')
        String openId = data['FromUserName']
        Long timestamp = System.currentTimeMillis()
        BasicDBObject expression = new BasicDBObject()
        expression.put('_id', openId)
        expression.put('timestamp', timestamp)
        String union = ''
        if (StringUtils.isBlank(senceId)) {
            union = data['default_union']
        } else {
            union = channel().findOne($$(sence_id: senceId as Integer), $$(_id: 1))?.get(_id)
        }
        expression.put('union', union)

        Integer flag = weixin_event_logs().save(expression).getN()
        if (flag == 1) {
            this.sendCustomImageText(data, WELCOME_CONTENT)
        } else {
            logger.debug('the ticket was not save in mongodb,please try again ....')
        }
    }

    /**
     * 菜单跳转
     * @param data
     */
    private void view(Map<String, String> data) {
        //静默授权 获取微信账号信息 跳转h5
    }

    private void text(Map<String, String> data) {
        String input = data['Content']
        String out
        if (input == '猫') {
            out = '屌丝求欲女（吊死球鱼女）你猜到了吗?'
        }
        if (input == '润滑油') {
            out = '这是润滑油的广告,图中应该有5个柱子,女孩坐在中间的柱子上,你看懂了吗?'
        }
        if (out) {
            this.sendCustomText(data, out)
        }
    }

    /**
     * 发送文本信息
     * @param data
     */
    private void sendCustomText(Map<String, String> data, String content) {
        String appId = data['app_id']
        String appSecret = data['app_secret']
        String requestUrl = WEIXIN_URL + 'message/custom/send?access_token='.concat(getAccessToken(appId, appSecret))
        Map map = new HashMap()
        map.put('touser', data['FromUserName'])
        map.put('msgtype', 'text')
        map.put('text', ['content': content])
        Map params = new HashMap()
        params.put('sendObj', map)
        Map respMap = this.postWX('POST', requestUrl, params, appId, appSecret)
        Integer errcode = respMap['errcode'] == null ? 0 : respMap['errcode'] as Integer
        if (errcode != 0) {
            logger.error('appId is {}', appId)
            logger.error('secret is {}', appSecret)
            logger.error('errcode is {}', errcode)
            logger.error('msg is {}', errorCode.get(errcode))
        }
    }

    /**
     * 发送图文信息
     * https://mp.weixin.qq.com/wiki?t=resource/res_main&id=mp1421140547&token=&lang=zh_CN
     * @param data
     */
    private void sendCustomImageText(Map<String, String> data, String content) {
        String appId = data['app_id']
        String appSecret = data['app_secret']
        //https://open.weixin.qq.com/connect/oauth2/authorize?appid=wxa25358916dea6f30&redirect_uri=http://api.memeyule.com/weixin/redirect?appid=wxa25358916dea6f30&response_type=code&scope=snsapi_userinfo&state=&connect_redirect=1#wechat_redirect
        String redirectUrl = "https://open.weixin.qq.com/connect/oauth2/authorize?appid=${appId}&redirect_uri=${AppProperties.get('api.domain')}/weixin/redirect?appid=${appId}&response_type=code&scope=snsapi_userinfo&state=&connect_redirect=1#wechat_redirect"
        String requestUrl = WEIXIN_URL + 'message/custom/send?access_token='.concat(getAccessToken(appId, appSecret))
        logger.debug('redirect url is {}', redirectUrl)
        List articleList = new ArrayList()
        Article article = new Article(TITLE, content, PIC_URL, redirectUrl)
        articleList.add(article)
        Map map = new HashMap()
        map.put('articles', articleList)
        News news = new News('news', data['FromUserName'], map)
        Map params = new HashMap()
        params.put('sendObj', news)
        Map respMap = this.postWX('POST', requestUrl, params, appId, appSecret)
        Integer errcode = respMap['errcode'] == null ? 0 : respMap['errcode'] as Integer
        if (errcode != 0) {
            logger.error('appId is {}', appId)
            logger.error('secret is {}', appSecret)
            logger.error('errcode is {}', errcode)
            logger.error('msg is {}', errorCode.get(errcode))
        }
    }

    def get_token(){
        [code : 1, data:getAccessToken('wx45d43a50adf5a470', '40e8dc2daac9f04bfbac32a64eb6dfff')]
    }
    /**
     * 获取token
     * 微信每日调用accessToken次数有限(2000次/日,有效7200秒)
     * @param req
     */
    private String getAccessToken(String appId, String appSecret) {
        String redis_key_token = 'weixin:' + appId + ':token'
        RedisLock redisLock = new RedisLock(redis_key_token);
        String access_token = "";
        try{
            redisLock.lock();
            access_token = mainRedis.opsForValue().get(redis_key_token)
            if (StringUtils.isBlank(access_token) || access_token.equals('null')) {
                Map params = new HashMap();
                params.put('grant_type', 'client_credential')
                params.put('appid', appId)
                params.put('secret', appSecret)
                String requestUrl = WEIXIN_URL + 'token'
                Map respMap = this.postWX('GET', requestUrl, params, appId, appSecret)
                access_token = respMap['access_token']
                Long expires = (respMap['expires_in'] ?:0) as Long
                Integer errcode = respMap['errcode'] == null ? 0 : respMap['errcode'] as Integer
                if (errcode != 0 || expires <= 0 || StringUtils.isEmpty(access_token)) {
                    logger.error('getAccessToken appId:{}, secret:{} errcode:{}, expire: {}', appId, appSecret,errcode,expires)
                    logger.error('getAccessToken access_token is {}', access_token)
                    logger.error('msg is {}', errorCode.get(errcode))
                    return access_token
                }
                mainRedis.opsForValue().set(redis_key_token, access_token, (expires-60), TimeUnit.SECONDS)
            }
            logger.debug('access_token is {}', access_token)
        }catch (Exception e){
            logger.error("getAccessToken Exception : {}", e);
            logger.error('appId : {}, secret: {}', appId, appSecret)
        }finally {
            redisLock.unlock();
        }
        return access_token;
    }

    /**
     * 刷新token
     */
    def refreshAccessToken(HttpServletRequest req){
        String appId = req['app_id']
        String appSecret = getAppSecret(appId)
        String key = 'weixin:' + appId + ':token'
        mainRedis.delete(key)
        this.getAccessToken(appId,appSecret)
    }

    /**
     * 验证来源是否来自微信服务器
     * @return
     */
    private boolean validationReferer(HttpServletRequest req, HttpServletResponse resp) {
        Boolean flag = true;
        String signature = req['signature']
        String timestamp = req['timestamp']
        String nonce = req['nonce']
        String echostr = req['echostr']

        List<String> checkList = new ArrayList<String>()
        checkList.add(nonce)
        checkList.add(timestamp)
        checkList.add(TOKEN)
        // 字典排序
        Collections.sort(checkList, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        })
        String checkStr = checkList.get(0) + checkList.get(1) + checkList.get(2)
        if (signature != DigestUtils.shaHex(checkStr)) {
            logger.debug('signature was wrong ,toekn is {},checkStr is {},signature is {},DigestUtils.shaHex(checkStr) is {}', TOKEN, checkStr, signature, DigestUtils.shaHex(checkStr))
            flag = false
        } else {
            // 供后台配置token验证回调使用
            if (StringUtils.isNotBlank(echostr)) {
                resp.getWriter().print(echostr)
            }
        }

        return flag
    }

    /**
     * 请求微信服务端
     * @param postMethod
     * @param params
     * @param requestUrl
     * @param msg
     * @return
     */
    private Map postWX(String postMethod, String requestUrl, Map params, String appId, String appSecret) {
        String resp, sendContent
        String key = 'weixin:' + appId + ':token'
        if (postMethod == 'POST') {
            Object sendObj = params['sendObj']
            sendContent = JSONUtil.beanToJson(sendObj)
            logger.debug('the POST params string is ' + sendContent)
            resp = HttpUtils.sentPost(requestUrl, sendContent)
        } else {
            logger.debug('the GET params map value is ' + params)
            resp = HttpUtils.sendGet(requestUrl, params)
        }
        if (StringUtils.isNotBlank(resp)) {
            Map map = JSONUtil.jsonToMap(resp)
            Integer errcode = map.get('errcode') == null ? 0 : map.get('errcode') as Integer
            // 对accessToken失效的情况进行容错,重新请求3次
            if (errcode == 40001) {
                if (RETRY == 3) {
                    RETRY++
                    logger.debug('redis token is not equals wx token ...')
                    mainRedis.delete(key)
                    requestUrl = requestUrl.substring(0, requestUrl.indexOf('access_token'))
                    requestUrl = requestUrl.concat('access_token=' + getAccessToken(appId, appSecret))
                    logger.debug('after rebuild token, the url is {}', requestUrl)
                    return postWX(postMethod, requestUrl, params, appId, appSecret)
                }
            }
        }
        logger.debug('received WX response is {} ', resp);
        return JSONUtil.jsonToMap(resp)
    }

    /**
     * 创建微信自定义菜单
     * http://test.api.memeyule.com/weixin/createMenu?url=https://open.weixin.qq.com/connect/oauth2/authorize?appid=wx719ebd95f287f861%26redirect_uri=http%3a%2f%2ftest.user.memeyule.com%2fthirdlogin%2fweixin_gz%3furl%3dhttp%3a%2f%2ftest.m.imeme.tv%2flogin.html%3ftoken%3d%7baccess_token%7d%26response_type=code%26scope=snsapi_userinfo%26state=%26connect_redirect=1&name=美女直播&type=view
     * @param req
     */
    def create_menu(HttpServletRequest req) {
        RETRY = 0
        logger.debug("Recv create_menu params : {}", req.getParameterMap())
        String url = req['url']
        String type = req['type']
        String name = req['name']
        String appId = req['app_id']
        String appSecret = getAppSecret(appId)
        url = URLDecoder.decode(url, "utf-8");

        String requestUrl = WEIXIN_URL + 'menu/create?access_token='.concat(getAccessToken(appId, appSecret))
        List list = new ArrayList()
        WeXinMenu menu = new WeXinMenu(name, type, url)
        list.add(menu)
        Map build = new HashMap()
        build.put('button', list)
        Map params = new HashMap()
        params.put('sendObj', build)
        Map respMap = this.postWX('POST', requestUrl, params, appId, appSecret)
        Integer errcode = respMap['errcode'] == null ? 0 : respMap['errcode'] as Integer
        if (errcode != 0) {
            logger.error('appId is {}', appId)
            logger.error('secret is {}', appSecret)
            logger.error('errcode is {}', errcode)
            logger.error('msg is {}', errorCode.get(errcode))
            return [code: 0, data: respMap]
        }
        return [code: 1, data: 'success']
    }

    /**
     * 删除微信自定义菜单
     * @param req
     */
    def delete_menu(HttpServletRequest req) {
        RETRY = 0
        logger.debug("Recv delete_menu params : {}", req.getParameterMap())

        String appId = req['app_id']
        String appSecret = getAppSecret(appId)
        String requestUrl = WEIXIN_URL + 'menu/delete?access_token='.concat(getAccessToken(appId, appSecret))
        Map respMap = this.postWX('GET', requestUrl, new HashMap(), appId, appSecret)
        Integer errcode = respMap['errcode'] == null ? 0 : respMap['errcode'] as Integer
        if (errcode != 0) {
            logger.error('appId is {}', appId)
            logger.error('secret is {}', appSecret)
            logger.error('errcode is {}', errcode)
            logger.error('msg is {}', errorCode.get(errcode))
            return [code: 0, data: respMap]
        }
        return [code: 1, data: 'success']
    }

    /**
     * 查询菜单
     * @param req
     */
    def search_menu(HttpServletRequest req) {
        RETRY = 0
        logger.debug("Recv search_menu params : {}", req.getParameterMap())

        String appId = req['app_id']
        String appSecret = getAppSecret(appId)
        String requestUrl = WEIXIN_URL + 'menu/get?access_token='.concat(getAccessToken(appId, appSecret))
        Map respMap = this.postWX('GET', requestUrl, new HashMap(), appId, appSecret)
        Integer errcode = respMap['errcode'] == null ? 0 : respMap['errcode'] as Integer
        if (errcode != 0) {
            logger.error('appId is {}', appId)
            logger.error('secret is {}', appSecret)
            logger.error('errcode is {}', errcode)
            logger.error('msg is {}', errorCode.get(errcode))
            return [code: 0, data: respMap]
        }
        return [code: 1, data: 'success']
    }

    /**
     * 微信菜单重定向转跳么么页面
     * @param req
     * @param response
     * @return
     */
    def redirect(HttpServletRequest req, HttpServletResponse response) {
        logger.debug("Recv redirect params: {}", req.getParameterMap())
        String code = req['code']
        String appid = req['appid']

        String appSecret = getAppSecret(appid)
        logger.debug("appid :{}, appSecret:{}", appid, appSecret)
        Map result = UserWebApi.getTokenByWeixinCode(code, appid, appSecret)
        if (result == null)
            return [code: 0]
        if (((Number) result.get("code")).intValue() != 1) {
            return [code: result.get("code")]
        }
        final Map data = (Map) result.get("data");
        String access_token = data?.get("token") as String;
        String openId = data?.get("openid") as String;
        logger.debug("redirect access_token: {}", access_token)
        logger.debug("redirect openId: {}", openId)
        String union = weixin_event_logs().findOne($$(_id: openId), $$(union: 1))?.get('union')
        logger.debug("redirect union: {}", union)
        //重定向到首页面
        response.sendRedirect("${REDIRECT_URL}login.html?token=${access_token}&union=${union}&ckey=CK1323874108342")
    }

    /**
     * 记录openId,计算过期时间
     * @param data
     */
    private void saveOpenId(Map data) {
        def weixinDB = unionMongo.getCollection('weixin_customer')
        String openId = data['FromUserName']
        Long now = System.currentTimeMillis()
        Long expires = now + TWO_DAY_MILLIS
        BasicDBObject saveExpression = new BasicDBObject()
        saveExpression.put('_id', openId)
        saveExpression.put('expires', expires)
        saveExpression.put('timestamp', now)
        saveExpression.put('appId', data['app_id'])
        saveExpression.put('appSecret', data['app_secret'])
        weixinDB.save(saveExpression)
    }

    /**
     * 根据appId获取appSecret
     * @param appId
     * @return
     */
    private String getAppSecret(String appId) {
        String appSecret = ''
        BasicDBObject searchExpression = new BasicDBObject('app_id': appId)
        DBObject channel = channel().findOne(searchExpression)
        if (channel) {
            appSecret = channel['app_secret']
        } else {
            logger.warn('app secret was not in DB,app id is {}', appId)
        }
        return appSecret
    }

    /**
     * 我写这段代码的时候心里想疯
     * @return
     */
    static Map initErrorCode() {
        Map map = new HashMap()
        map.put(-1, '系统繁忙，此时请开发者稍候再试')
        map.put(0, '请求成功')
        map.put(40001, '获取access_token时AppSecret错误，或者access_token无效。请开发者认真比对 AppSecret的正确性，或查看是否正在为恰当的公众号调用接口')
        map.put(40002, '不合法的凭证类型')
        map.put(40003, '不合法的OpenID，请开发者确认OpenID(该用户)是否已关注公众号，或是否是其他公众 号的OpenID')
        map.put(40004, '不合法的媒体文件类型')
        map.put(40005, '不合法的文件类型')
        map.put(40006, '不合法的文件大小')
        map.put(40007, '不合法的媒体文件id')
        map.put(40008, '不合法的消息类型')
        map.put(40009, '不合法的图片文件大小')
        map.put(40010, '不合法的语音文件大小')
        map.put(40011, '不合法的视频文件大小')
        map.put(40012, '不合法的缩略图文件大小')
        map.put(40013, '不合法的AppID，请开发者检查AppID的正确性，避免异常字符，注意大小写')
        map.put(40014, '不合法的access_token，请开发者认真比对access_token的有效性(如是否过期)，或查 看是否正在为恰当的公众号调用接口')
        map.put(40015, '不合法的菜单类型')
        map.put(40016, '不合法的按钮个数')
        map.put(40017, '不合法的按钮个数')
        map.put(40018, '不合法的按钮名字长度')
        map.put(40019, '不合法的按钮KEY长度')
        map.put(40020, '不合法的按钮URL长度')
        map.put(40021, '不合法的菜单版本号')
        map.put(40022, '不合法的子菜单级数')
        map.put(40023, '不合法的子菜单按钮个数')
        map.put(40024, '不合法的子菜单按钮类型')
        map.put(40025, '不合法的子菜单按钮名字长度')
        map.put(40026, '不合法的子菜单按钮KEY长度')
        map.put(40027, '不合法的子菜单按钮URL长度')
        map.put(40028, '不合法的自定义菜单使用用户')
        map.put(40029, '不合法的oauth_code')
        map.put(40030, '不合法的refresh_token')
        map.put(40031, '不合法的openid列表')
        map.put(40032, '不合法的openid列表长度')
        map.put(40033, '不合法的请求字符，不能包含\\uxxxx格式的字符')
        map.put(40035, '不合法的参数')
        map.put(40038, '不合法的请求格式')
        map.put(40039, '不合法的URL长度')
        map.put(40050, '不合法的分组id')
        map.put(40051, '分组名字不合法')
        map.put(41001, '缺少access_token参数')
        map.put(41002, '缺少appid参数')
        map.put(41003, '缺少refresh_token参数')
        map.put(41004, '缺少secret参数')
        map.put(41005, '缺少多媒体文件数据')
        map.put(41006, '缺少media_id参数')
        map.put(41007, '缺少子菜单数据')
        map.put(41008, '缺少oauth code')
        map.put(41009, '缺少openid')
        map.put(42001, 'access_token超时，请检查access_token的有效期，请参考基础支持-获取access_token 中，对access_token的详细机制说明')
        map.put(42002, 'refresh_token超时')
        map.put(42003, 'oauth_code超时')
        map.put(43001, '需要GET请求')
        map.put(43002, '需要POST请求')
        map.put(43003, '需要HTTPS请求')
        map.put(43004, '需要接收者关注')
        map.put(43005, '需要好友关系')
        map.put(44001, '多媒体文件为空')
        map.put(44002, 'POST的数据包为空')
        map.put(44003, '图文消息内容为空')
        map.put(44004, '文本消息内容为空')
        map.put(45001, '多媒体文件大小超过限制')
        map.put(45002, '消息内容超过限制')
        map.put(45003, '标题字段超过限制')

        map.put(45004, '描述字段超过限制')
        map.put(45005, '链接字段超过限制')
        map.put(45006, '图片链接字段超过限制')

        map.put(45007, '语音播放时间超过限制')
        map.put(45008, '图文消息超过限制')
        map.put(45009, '接口调用超过限制')
        map.put(45010, '创建菜单个数超过限制')
        map.put(45015, '回复时间超过限制')
        map.put(45016, '系统分组，不允许修改')
        map.put(45017, '分组名字过长')
        map.put(45018, '分组数量超过上限')
        map.put(46001, '不存在媒体数据')
        map.put(46002, '不存在的菜单版本')
        map.put(46003, '不存在的菜单数据')
        map.put(46004, '不存在的用户')
        map.put(47001, '解析JSON/XML内容错误')
        map.put(48001, 'api功能未授权，请确认公众号已获得该接口，可以在公众平台官网-开发者中心页中查看 接口权限')
        map.put(50001, '用户未授权该api')
        map.put(61451, '参数错误(invalid parameter)')
        map.put(61452, '无效客服账号(invalid kf_account)')
        map.put(61453, '客服帐号已存在(kf_account exsited)')
        map.put(61454, '客服帐号名长度超过限制(仅允许10个英文字符，不包括@及@后的公众号的微信 号)(invalid kf_acount length)')
        map.put(61455, '客服帐号名包含非法字符(仅允许英文+数字)(illegal character in kf_account)')
        map.put(61456, '客服帐号个数超过限制(10个客服账号)(kf_account count exceeded)')
        map.put(61457, '无效头像文件类型(invalid file type)')
        map.put(61450, '系统错误(system error)')
        return map
    }

}