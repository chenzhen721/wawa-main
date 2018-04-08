package com.wawa.web.api

import com.mongodb.BasicDBObject
import com.mongodb.DBCollection
import com.mongodb.DBObject
import com.mongodb.QueryBuilder
import com.mongodb.connection.Base64Codec
import com.wawa.api.Audit
import com.wawa.api.DoCost
import com.wawa.api.UserWebApi
import com.wawa.api.Web
import com.wawa.api.interceptor.OAuth2SimpleInterceptor
import com.wawa.api.interceptor.UserFrom
import com.wawa.base.BaseController
import com.wawa.base.Crud
import com.wawa.base.anno.RestWithSession
import com.wawa.common.doc.ParamKey
import com.wawa.common.doc.Param
import com.wawa.common.doc.Result
import com.wawa.common.util.AuthCode
import com.wawa.common.util.Base64
import com.wawa.common.util.DateUtil
import com.wawa.common.util.KeyUtils
import com.wawa.model.Mission
import com.wawa.model.PicType
import com.wawa.model.PlatformType
import com.wawa.model.UserType
import com.wawa.api.event.AfterBuildUserListener
import com.wawa.api.event.BuildUserObserver
import com.wawa.web.friend.FriendController
import com.wawa.web.mobile.WeixinJSController
import org.apache.commons.lang.StringUtils
import org.apache.commons.lang.math.RandomUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.util.FileCopyUtils
import org.springframework.web.bind.ServletRequestUtils
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.commons.CommonsMultipartResolver
import org.springframework.web.util.HtmlUtils

import javax.annotation.Resource
import javax.imageio.ImageIO
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.awt.image.BufferedImage
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern

import static com.wawa.common.util.WebUtils.$$
import static com.wawa.common.doc.MongoKey.*

/**
 * 用户个人信息
 */
@RestWithSession
class UserController extends BaseController {

    @Resource
    MongoTemplate rankMongo
    @Resource
    MongoTemplate adminMongo

    /*@Resource
    FriendController friendController*/

    @Resource
    MissionController missionController

    @Resource
    WeixinJSController weixinJSController

    static final Logger logger = LoggerFactory.getLogger(UserController.class)

    DBCollection sign_logs() { logMongo.getCollection('sign_logs')}

    DBCollection invitor_logs() { logMongo.getCollection('invitor_logs')}

    DBCollection goods() {
        return adminMongo.getCollection('goods')
    }

    private static final HELLO = '通过好友分享成功加为好友啦，我已经给你带来了一个好友红包哦';

    static
    final BasicDBObject user_info_field = $$('_id': 1, 'nick_name': 1, 'finance': 1, exp : 1, level : 1, 'pic': 1, 'priv': 1,
            mobile_bind:1,bag:1,'location': 1, 'sex':1, 'birthday':1, 'signature':1,status:1, weixin_focus:1, mission:1, qd: 1)

    static
    final DBObject user_bind_info_field = new BasicDBObject(tuid: 1, pic: 1, nick_name: 1, via: 1, mobile_bind: 1, mission: 1, email: 1)

    public static
    final BasicDBObject user_info_core_field = new BasicDBObject(pic: 1, nick_name: 1, mm_no: 1, level : 1, birthday: 1)

    public static final int invite_total = 0

    /**
     * 用户注册完成事件 随机添加发送好友请求
     */
    public UserController() {
        BuildUserObserver.addAfterBuildUserListner(new AfterBuildUserListener() {
            public void fireEvent(DBObject user) {
                Integer userId = user?.get(_id) as Integer
                logger.debug("After Build User userId : {}", userId);
                //随机添加机器人好友请求
                //addApplyFriend(userId)

                //骑兵用户，给对应邀请人发放奖励
                Boolean is_register = user?.get('is_register') as Boolean
                if (is_register) {
                    addNewRegister(userId)
                    addSignlog(user)
                }

            }

            final static Integer FRIEND_SIZE = 2

            private void addApplyFriend(Integer userId) {
                def robot_ids = Web.getRoBotIdList();
                (RandomUtils.nextInt(FRIEND_SIZE) as Integer).each {
                    Integer robotId = robot_ids[RandomUtils.nextInt(robot_ids.size())]
                    //friendController.addApply(userId, robotId, 'Hi')
                    logger.debug("After Build User addApplyFriend robotId: {}", robotId);
                }
            }

            private void addNewRegister(Integer userId) {
                //被邀请用户标识
                mainRedis.opsForValue().set(KeyUtils.USER.fresh(userId), '' + userId, 60l, TimeUnit.SECONDS)
                //首次抓中用户标识
                mainRedis.opsForValue().set(KeyUtils.USER.first(userId), '1')
                //前三抓标识，抓中后
                mainRedis.opsForValue().set(KeyUtils.USER.first_doll(userId), '3', 7 * DAY_MILLON, TimeUnit.MILLISECONDS)
            }

            private void addSignlog(DBObject user) {//奖励统计
                Integer userId = user?.get(_id) as Integer
                def finance = user?.get('finance') as Map
                def diamond = finance?.get('diamond_count') as Integer
                missionController.new_award_notify(userId, diamond, true)
            }
        })
    }

    /**
     * 用户核心信息  nodejs 端使用
     * @return
     */
    def core_info() {
        [code: 1, data: users().findOne(Web.getCurrentUserId(), user_info_core_field)]
    }


    private Map fragment_info(String field) {
        [code: 1, data: users().findOne(Web.getCurrentUserId(), new BasicDBObject(field, 1))]
    }


    /**
     *获取用户社交信息(关注，粉丝，好友)
     *
     */
    def social_info() {
        Integer userId = Web.getCurrentUserId();
        Integer followers = mainRedis.opsForSet().size(KeyUtils.USER.followers(userId)) ?: 0
        Integer followings = mainRedis.opsForSet().size(KeyUtils.USER.following(userId)) ?: 0
        Integer friends = mainRedis.opsForSet().size(KeyUtils.USER.friends(userId)) ?: 0
        [code: 1, data: [followers: followers, followings: followings, friends: friends]]
    }

    /**
     * 需要时启用,需要补充逻辑
     */
    def following_list() {
        Integer userId = Web.getCurrentUserId()
        List<Integer> ids = Web.getFollowing(userId);
        if (ids == null || ids.isEmpty()) {
            return [code: 1, msg: 'ok', data: [:]]
        }
        def roomList = []
        [code: 1, data: [rooms: roomList, count: roomList.size()]]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup User
     * @apiName is_following
     * @api {post} user/is_following/:access_token?fid=:fid  是否已关注娃娃机
     * @apiDescription
     * 是否已关注娃娃机
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Integer} [fid]  娃娃机ID
     *
     * @apiSuccessExample {json} Success-Response:
     * {
     *     "code" : 1,
     *     "data" : {
     *         "following" : 是否关注 true 已关注 false未关注
     *     }
     * }
     */
    def is_following(HttpServletRequest req) {
        Integer room_id = ServletRequestUtils.getIntParameter(req, 'fid')
        Integer currentId = Web.getCurrentUserId()
        [code: 1, data: [following: isFollowing(currentId, room_id)]]
    }

    public Boolean isFollowing(Integer user_id, Integer room_id) {
        if (user_id == null || user_id <= 0) {
            return false
        }
        return mainRedis.opsForSet().isMember(KeyUtils.USER.following(user_id), room_id.toString())
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup User
     * @apiName add_following
     * @api {post} user/add_following/:access_token/:fid  关注娃娃机
     * @apiDescription
     * 用户关注关注娃娃机
     *
     * @apiUse USER_COMMEN_PARAM
     *
     */
    def add_following(HttpServletRequest req) {
        following(req, true)
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup User
     * @apiName del_following
     * @api {post} user/del_following/:access_token/:fid  取消关注娃娃机
     * @apiDescription
     * 用户取消关注娃娃机
     *
     * @apiUse USER_COMMEN_PARAM
     *
     */
    def del_following(HttpServletRequest req) {
        following(req, false)
    }

    DBCollection catch_rooms() {
        return catchMongo.getCollection('catch_room')
    }

    /**
     *
     * 设置是否关注
     * @param req
     * @param add
     * @return
     */
    private following(HttpServletRequest req, boolean add) {
        logger.debug('Received following params is {}', req.getParameterMap())
        Integer room_id = Web.firstNumber(req)
        Integer currentId = Web.getCurrentUserId()
        if (room_id == currentId) {
            return [code: 0, msg: "不能添加/删除自己"]
        }

        def count_query = $$('_id': room_id)
        if (add && goods().count(count_query) == 0) {
            return [code: 0, msg: "家族房间不存在"]
        }
        followerOps(room_id, currentId, add)
        if (add) {
            Boolean first_time = mainRedis.opsForSet().add(KeyUtils.USER.historyFollowers(room_id), currentId.toString())
            saveFollowerLogs(room_id, currentId, first_time)
        }
        Result.success
    }

    /**
     * 关注
     * @param star_id
     * @param currentId
     * @param add
     * @return
     */
    def followerOps(Integer room_id, Integer currentId, boolean add) {
        def setsOp = mainRedis.opsForSet()
        if (add) {
            setsOp.add(KeyUtils.USER.following(currentId), room_id.toString())
            setsOp.add(KeyUtils.USER.followers(room_id), currentId.toString())
            saveFollowerDay(room_id, 1)
        } else {
            removeFollow(currentId, room_id)
        }
        Integer inc_followers = (catch_rooms().findOne($$(_id: room_id), $$(inc_followers: 1))?.get('inc_followers') ?: 0) as Integer
        catch_rooms().update($$(_id: room_id), $$($set: [followers: setsOp.size(KeyUtils.USER.followers(room_id)) + inc_followers]))
    }

    /**
     * 清除关注管理
     */
    private void removeFollow(Integer currentId, Integer room_id) {
        mainRedis.opsForSet().remove(KeyUtils.USER.following(currentId), room_id.toString())
        mainRedis.opsForSet().remove(KeyUtils.USER.followers(room_id), currentId.toString())
        saveFollowerDay(room_id, -1)
    }

    private boolean saveFollowerDay(Integer room_id, Integer count) {
        def incObject = new BasicDBObject('num': count)
        def sId = room_id + "_" + new Date().format("yyyyMMdd")
        def tmp = System.currentTimeMillis()
        if (logMongo.getCollection("room_follower_day").
                findAndModify(new BasicDBObject(_id, sId), null, null, false,
                        new BasicDBObject($inc: incObject,
                                $set: new BasicDBObject(cat: "day", room_id: room_id, timestamp: tmp)),
                        true, true).get('timestamp').equals(tmp)) {
            return true
        }
        return false
    }

    private void saveFollowerLogs(Integer room_id, Integer userId, Boolean first_time) {
        def time = System.currentTimeMillis()
        def _id = room_id + "_" + userId + "_" + time
        logMongo.getCollection("follower_logs").insert($$(_id: _id, session: Web.getSession(),
                room_id: room_id, user_id: userId, timestamp: time, first_time: first_time))
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup User
     * @apiName info
     * @api {get} user/info  获取用户信息
     * @apiDescription
     * 获取用户信息详细信息
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/user/info/

     * @apiSuccessExample {json} Success-Response:
     *
     *     {
     *       '_id': 用户ID,
     *       'nick_name': 昵称,
     *       'finance.coin_count': 金币数量,
     *       'finance.diamond_count': 钻石数量-直接开卡,
     *       'finance.cash_count': 现金数量 单位：分,
     *       'bag.points.count': 积分数量,
     *       'exp':1000 用户当前经验值,
     *       'next_level_exp':1000 用户下级所需经验值,
     *       'level':5 用户等级-通过经验升级,
     *       'pic': 头像,
     *       'priv': 用户身份,
     *       'location': 地址,
     *       'sex': 性别,
     *       'birthday': 生日,
     *       'signature': 签名,
     *       'mobile_bind':是否绑定手机
     *       'mission':{
     *          invite:剩余邀请人数,
     *          invite_total: 总数,
     *          money100:是否有首冲特权 true是 false否
     *          unlimit:是否有抓必中特权 true是 false否
     *          unlimit_open:是否已经开启无限抓 有字段开启，无字段不开启
     *          unlimit_cost:剩余消费金额
     *        }
     *   }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def info(HttpServletRequest req, HttpServletResponse res) {
        def userId = Web.getCurrentUserId();
        def query = $$('_id': userId)
        def field = $$(user_info_field)
        //field.append('qd', 1)
        def user = users().findOne(query, field) as BasicDBObject

        if (user == null) {
            logger.warn('user was disabled,userId is {}', userId)
            return [code: 30405, msg: "user was disabled"]
        }
        if (Boolean.TRUE != user.get('status')) {
            logout(req)
            return [code: 30418, msg: "user冻结", unfreeze_time: user?.get("unfreeze_time")]
        }
        //Integer level = (user['level'] ?:0) as Integer
        //user['next_level_exp'] = Level.userLevelUpExp(level+1)
        def valueOp = mainRedis.opsForValue()
        if(valueOp.setIfAbsent(KeyUtils.USER.login(userId),"1")){
            mainRedis.expireAt(KeyUtils.USER.login(userId), (new Date()+1).clearTime())
            Web.day_login(req, userId)
        }
        def money100Key = KeyUtils.MISSION.mission_users(Mission.首充100.id)
        //是否要显示 邀请数量 首冲特权 抓必中
        //def mission = inviteCount(user)
        //mission.put('money100', !mainRedis.opsForHash().hasKey(money100Key, '' + userId))
        //未抓中过且未领取
        /*String key = KeyUtils.USER.unlimit(userId)
        mission.put('unlimit', missionController.isFirst(userId) || mainRedis.hasKey(key))
        if (mainRedis.hasKey(key)) {
            def unlimit_open = '1' == mainRedis.opsForHash().get(key, 'unlimit_flag')
            mission.put('unlimit_open', unlimit_open)
            if (!unlimit_open) {
                mission.put('unlimit_cost', mainRedis.opsForHash().get(key, 'unlimit_diamond_cost'))
            }
        }
        user.put('mission', mission)*/
        return [code: 1, data: user]
    }

    private Map inviteCount(BasicDBObject user) {
        if (user['mission'] == null || user['mission']['invite'] == null) {
            users().update($$(_id: user['_id'] as Integer, $or: [[mission: [$exists: false]], ['mission.invite': [$exists: false]]]), $$($set: [mission: ['invite': invite_total, 'invite_total': invite_total]]), false, false, writeConcern)
            return [invite: invite_total, invite_total: invite_total]
        }
        return user['mission'] as Map
    }

    @Value("#{application['pic.domain']}")
    String pic_domain = "https://aiimg.sumeme.com/"

    private static final String[] int_user_field = ['sex', 'constellation', 'stature'];
    private static final String[] str_user_field = ['location', 'pic', 'coordinate.x', 'coordinate.y', 'address',
                                                    'birthday.year', 'birthday.month', 'birthday.day', 'signature', 'tag_ids'];

    /**
     * @apiVersion 0.0.1
     * @apiGroup User
     * @apiName editUserInfo
     * @api {post} user/edit/:access_token  编辑用户信息
     * @apiDescription
     * 编辑用户信息
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiParam {String} [nick_name]  昵称
     * @apiParam {String} [pic]  头像
     * @apiParam {Number={0:女,1:男}} [sex]  性别
     * @apiParam {Object} [birthday] 生日
     * @apiParam {String} birthday.year  生日-年 (birthday.year='1998')
     * @apiParam {String} birthday.month  生日-月 (birthday.month='01')
     * @apiParam {String} birthday.day  生日-日 (birthday.day='21')
     * @apiParam {String} [signature]  个人签名
     * @apiParam {String} [tag_ids] 标签ID，逗号(,)分隔
     * @apiParam {String} [location] 城市
     *
     */
    def edit(HttpServletRequest req) {
        logger.debug('Received edit params is {}', req.getParameterMap())
        Integer platform = ServletRequestUtils.getIntParameter(req, "p", PlatformType.android.ordinal());
        def update = new HashMap()
        def roomupdate = new HashMap()
        final Integer userId = Web.getCurrentUserId()
        for (String field : str_user_field) {
            String v = req.getParameter(field)
            if (StringUtils.isNotBlank(v)) {
                if ('pic'.equals(field)) {
                    if (v.startsWith(pic_domain)) {//只允许设置pic为这个域名下的头像
                        update.put(field, v)
                        Web.putUserInfoToSession(req, "pic", v)
                        roomupdate.put("pic", v)
                        //保存至待审核
                        Audit.picToAudit(v, userId, PicType.用户头像)
                    }
                } else if ('tag_ids'.equals(field)) {
                    update.put(field, v.split(',').collect { it as Integer})
                } else {
                    update.put(field, v)
                }
            }

        }
        for (String field : int_user_field) {
            Integer v = ServletRequestUtils.getIntParameter(req, field)
            if (null != v) {
                update.put(field, v)
            }
        }
        String nick_name = req.getParameter("nick_name")
        if (StringUtils.isNotBlank(StringUtils.trim(nick_name)) && StringUtils.trim(nick_name).length() >= 2) {
            //去除特殊符号会车 制表 换行符
            Matcher m = special_pattern.matcher(nick_name);
            nick_name = m.replaceAll("");
            if (!validateNickName(nick_name) || Audit.IsIllegalNickName(nick_name, userId)) {
                return [code: 30451]
            }
            nick_name = HtmlUtils.htmlEscape(nick_name)
            update.put("nick_name", nick_name)
            roomupdate.put("nick_name", nick_name)
            Web.putUserInfoToSession(req, "nick_name", nick_name)
        }


        if (update.size() > 0) {
            def user = users().update(new BasicDBObject(_id, userId), new BasicDBObject($set, update), false, false, writeConcern)
            if (1 == user.getN()) {
                Integer priv = users().findOne($$(_id, userId), $$(priv: 1)).get("priv") as Integer
                if (UserType.主播.ordinal() == priv && roomupdate.size() > 0) {
                    rooms().update(new BasicDBObject(_id, userId), new BasicDBObject($set, $$(roomupdate)), false, false, writeConcern)
                }
                return Result.success
            }
        }
        return [code: 0]

    }

    private static Pattern special_pattern = Pattern.compile("\\s*|\t|\r|\n");

    private Boolean validateNickName(String nick_name) {
        Boolean flag = nick_name.length() <= 16 &&
                !nick_name.contains(" ") &&
                !nick_name.equals(Web.currentUserNick()) &&
                adminMongo.getCollection('blackwords').count(new BasicDBObject(_id: nick_name)) == 0 &&
                !isContainForbiddenChar(nick_name)
        return flag
    }

    private final static List FORBIDDEN_CHARS = ['200f', '200e', '200f', '200d', '200c', '202a', '202d', '202e', '202b'
                                                 , '206a', '206b', '206c', '206e', '206e', '206f']

    public static boolean isContainForbiddenChar(String s) {
        for (int i = 0; i < s.length(); i++) {
            int ch = (int) s.charAt(i);
            if (ch == null) continue
            String str = Integer.toHexString(ch);
            if (FORBIDDEN_CHARS.contains(str)) {
                return true
            }
        }
        return false
    }

    //图片处理
    File pic_folder

    @Value("#{application['pic.folder']}")
    void setPicFolder(String folder) {
        pic_folder = new File(folder)
        pic_folder.mkdirs()
        println "初始化图片上传目录 : ${folder}"
    }

    private static final List<String> NEED_AUDIT_PIC_TYPES = ['0', '1']
    /**
     * @apiVersion 0.0.1
     * @apiGroup User
     * @apiName upload_pic_form
     * @api {post} user/upload_pic_form/:token/:type  图片上传(multipart)
     * @apiDescription
     * 通过 multipart/form-data 方式上传图片
     *
     * @apiUse USER_COMMEN_PARAM

     * @apiParam {Number=0:用户头像,1:家族头像,2:家族封面} type  图片类型
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/user/upload_pic_form/:token/:type
     */
    def upload_pic_form(HttpServletRequest request) {
        def parse = new CommonsMultipartResolver()
        def req = parse.resolveMultipart(request)
        try {
            Integer id = Web.getCurrentUserId()
            String type = ServletRequestUtils.getIntParameter(request, Param.first, 0)

            String filePath = "${id & 63}/${id & 7}/${id}_${type}.jpg"
            File target = new File(pic_folder, filePath)

            String pic_url = "${pic_domain}${filePath}?v=${System.currentTimeMillis()}".toString();
            for (Map.Entry<String, MultipartFile> entry : req.getFileMap().entrySet()) {
                MultipartFile file = entry.getValue()
                target.getParentFile().mkdirs()
                file.transferTo(target)
                break
            }
            if (NEED_AUDIT_PIC_TYPES.contains(type) && Audit.identifyIsIllegalPic(pic_url)) {
                try {
                    target.delete()
                } catch (Exception e) {
                    logger.error("Exception : {}", e)
                }
                return Result.非法图片;
            }
            logger.debug("upload_pic pic_url : {}", pic_url)
            return [code: 1, data: [pic_url: pic_url]]
        } catch (Exception e){
            logger.error("Exception {}", e)
        } finally{
            parse.cleanupMultipart(req)
        }
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup User
     * @apiName upload_pic
     * @api {get} user/upload_pic  图片上传
     * @apiDescription
     * 直接通过InputStream 流的方式上传接口
     *
     * @apiUse USER_COMMEN_PARAM

     * @apiParam {Number=0:用户头像,1:家族头像,2:家族封面} type  图片类型
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/user/upload_pic/:token/:type
     */
    def upload_pic(HttpServletRequest request) {
        Integer id = Web.getCurrentUserId()
        String type = ServletRequestUtils.getIntParameter(request, Param.first, 0)
        logger.debug("type:{}", type)
        String filePath = "${id & 63}/${id & 7}/${id}_${type}.jpg"
        String pic_url = "${pic_domain}${filePath}?v=${System.currentTimeMillis()}".toString();
        def target = new File(pic_folder, filePath)
        target.getParentFile().mkdirs()
        FileCopyUtils.copy(request.getInputStream(), new FileOutputStream(target))
        /*if (Audit.identifyIsIllegalPic(pic_url)) {
            try {
                target.delete()
            } catch (Exception e) {
                logger.error("Exception : {}", e)
            }
            return [code: 30431];
        }*/
        [code: 1, data: [pic_url: pic_url]]
    }

    /**
     * 上传身份证
     * @param request
     * @return
     */
    def upload_sfz(HttpServletRequest request) {
        def parse = new CommonsMultipartResolver()
        def req = parse.resolveMultipart(request)
        try {
            Integer id = Web.getCurrentUserId()
            Integer type = req.getParameter(Param.first) as Integer //1:正面 0:反面 2:手持
            Integer renew = (Web.secondNumber(req) ?: 0) as Integer //1:更新身份信息
            logger.debug("type {} id {}", type, id)
            if (type != 0 && type != 1 && type != 2) {
                return Result.丢失必需参数
            }
            String filePath = "sfz/${id}_${type}.jpg"
            if (renew.equals(1)) {
                filePath = "sfz/${id}_${type}_renew.jpg"
            }
            for (Map.Entry<String, MultipartFile> entry : req.getFileMap().entrySet()) {
                MultipartFile file = entry.getValue()
                def target = new File(pic_folder, filePath)
                target.getParentFile().mkdirs()
                file.transferTo(target)
                break
            }
            return [code: 1]
        } catch (Exception e) {
            logger.error("upload_sfz Exception:{}", e)
            return [code: 0]
        }
        finally {
            parse.cleanupMultipart(req)
        }
    }

    def cut_pic(HttpServletRequest request) {
        String allow_url = request.getParameter('url')
        //是否生成新图 1为生成新图
        String isNew = request.getParameter('new')
        if (!allow_url.startsWith(pic_domain)) {
            return [code: 0, msg: "${allow_url} is NOT allowed."]
        }
        int x = Integer.parseInt(request.getParameter('x'))
        int y = Integer.parseInt(request.getParameter('y'))
        int w = Integer.parseInt(request.getParameter('w'))
        int h = Integer.parseInt(request.getParameter('h'))
        int rw = ServletRequestUtils.getIntParameter(request, 'rw', 400)
        int rh = ServletRequestUtils.getIntParameter(request, 'rh', 300)
        def url = new URL(allow_url)
        BufferedImage img = ImageIO.read(url)
        String fpath = url.getPath().replace('.jpg', '').substring(1) + "_${rw}${rh}.jpg"
        if (StringUtils.isNotEmpty(isNew) && isNew.equals("1")) {
            fpath = url.getPath().replace('.jpg', '').substring(1) + "_${rw}${rh}_${System.currentTimeMillis()}.jpg"
        }
        File file = new File(pic_folder, fpath)
        def cutImg = AuthCode.cutImage(img, x, y, w, h)
        file.getParentFile().mkdirs()
        AuthCode.writeJpeg(AuthCode.compressImage(cutImg, rw, rh), new FileOutputStream(file))
        [code: 1, data:
                [pic_url: "${pic_domain}${fpath}?v=${w}_${h}_${System.currentTimeMillis()}".toString(),
                 path   : fpath]]

    }

    /**
     * 使用新的压缩算法
     * @param request
     * @return
     */
    def cut_pic_new(HttpServletRequest request) {
        String allow_url = request.getParameter('url')
        //是否生成新图 1为生成新图
        String isNew = request.getParameter('new')
        if (!allow_url.startsWith(pic_domain)) {
            return [code: 0, msg: "${allow_url} is NOT allowed."]
        }
        int x = Integer.parseInt(request.getParameter('x'))
        int y = Integer.parseInt(request.getParameter('y'))
        int w = Integer.parseInt(request.getParameter('w'))
        int h = Integer.parseInt(request.getParameter('h'))
        int rw = ServletRequestUtils.getIntParameter(request, 'rw', 400)
        int rh = ServletRequestUtils.getIntParameter(request, 'rh', 300)
        def url = new URL(allow_url)
        BufferedImage img = ImageIO.read(url)
        String fpath = url.getPath().replace('.jpg', '').substring(1) + "_${rw}${rh}.jpg"
        if (StringUtils.isNotEmpty(isNew) && isNew.equals("1")) {
            fpath = url.getPath().replace('.jpg', '').substring(1) + "_${rw}${rh}_${System.currentTimeMillis()}.jpg"
        }
        File file = new File(pic_folder, fpath)
        def cutImg = AuthCode.cutImage(img, x, y, w, h)
        file.getParentFile().mkdirs()
        AuthCode.compressImageNew(cutImg, rw, rh, file)
        [code: 1, data:
                [pic_url: "${pic_domain}${fpath}?v=${w}_${h}_${System.currentTimeMillis()}".toString(),
                 path   : fpath]]

    }


    private final
    static Map<String, String> PAY_DESC = ["ali_pc": "支付宝", "Ali": "支付宝", "itunes": "苹果商店", "ali_m": "支付宝", "ali_wap": "支付宝",
                                           "Admin" : "系统赠送", "weixin_m": "微信支付"]


    /**
     * @apiVersion 0.0.1
     * @apiGroup User
     * @apiName pay_log
     * @api {get} user/pay_log/:token  用户充值记录
     * @apiDescription
     * 用户充值记录
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/user/pay_log/:token
     *
     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *
     * }
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def pay_log(HttpServletRequest req) {
        def query = Web.fillTimeBetween(req).and('user_id').is(Web.getCurrentUserId())
        Crud.list(req, adminMongo.getCollection('finance_log'), query.get(), ALL_FIELD, SJ_DESC) { List<BasicDBObject> data ->
            def users = users()
            for (Map obj : data) {
                Integer to_id = obj.get('to_id') as Integer
                if (to_id) {
                    obj.put('to_name', users.findOne(to_id, new BasicDBObject('nick_name', 1))?.get('nick_name'))
                }
                String via = obj.get('via') as String
                obj.put('via_desc', PAY_DESC[via] ?: via)
            }
        }
    }

    /**
     * 用户当日充值总额
     */
    def today_charge_total() {
        def user_id = Web.getCurrentUserId();
        String currDate = new Date().format("yyyyMMdd")
        Long begin = new Date().clearTime().getTime()
        String redis_key = KeyUtils.charge_award_daily(currDate)
        [code: 1, data: [total_coin: getUserChargeTotalCoins(Web.getCurrentUserId(), begin, System.currentTimeMillis()),
                         is_award  : mainRedis.opsForSet().isMember(redis_key, user_id.toString())]]

    }
    /**
     * 用户当月充值总额
     */
    def curr_month_charge_total() {
        def user_id = Web.getCurrentUserId();
        //每月只能领取一次
        String currMonth = new Date().format("yyyyMM")
        String redis_key = KeyUtils.charge_award_monthly(currMonth)
        [code: 1, data: [total_coin: getUserChargeTotalCoins(Web.getCurrentUserId(), DateUtil.firstDayOfMonthTimestamp(), System.currentTimeMillis()),
                         is_award  : mainRedis.opsForSet().isMember(redis_key, user_id.toString())]]
    }

    /**
     * 获取用户时间范围内充值总额
     * @param userId
     * @param starTimestamp
     * @param endTimestamp
     * @return
     */
    public static Long getUserChargeTotalCoins(Integer userId, Long starTimestamp, Long endTimestamp) {
        Long total_coin = 0l
        def query = $$(to_id: userId, timestamp: [$gte: starTimestamp, $lt: endTimestamp], 'via': [$ne: 'Admin'])
        def iter = Web.adminMongo.getCollection('finance_log').aggregate(
                $$($match, query),
                $$($project, $$('coin', '$coin')),
                $$($group, [_id: null, total_coin: [$sum: '$coin']])
        ).results().iterator()
        if (iter.hasNext()) {
            total_coin = ((Number) iter.next().get('total_coin')).longValue()
        }
        return total_coin;
    }

    //已优化
    def cost_log(HttpServletRequest req) {
        def type = req.getParameter('type')
        QueryBuilder query = QueryBuilder.start();
        Date stime = this.getTime(req, "stime")
        Date etime = this.getTime(req, "etime")
        String sEnd = req.getParameter("lend")
        String sBegin = req.getParameter("lbegin")
        DBObject sort = new BasicDBObject(timestamp, -1)
        if (stime != null || etime != null) {
            query.and("timestamp")
            long lbegin = 0L
            if (StringUtils.isNotEmpty(sBegin)) {
                lbegin = Long.parseLong(sBegin)
                sort = new BasicDBObject(timestamp, 1)
            }
            if (lbegin == 0L && stime != null)
                lbegin = stime.getTime()
            if (lbegin > 0L)
                query.greaterThan(lbegin)

            long lend = 0L
            if (StringUtils.isNotEmpty(sEnd))
                lend = Long.parseLong(sEnd)
            if (lend == 0L && etime != null)
                lend = etime.getTime()
            if (lend > 0L)
                query.lessThan(lend)
        }
        query.and('session._id').is(Web.currentUserId()).and('type').is(type)

        int size = ServletRequestUtils.getIntParameter(req, ParamKey.In.size, 48)
        if (size < 0 || size > 200) {
            size = 48;
        }
        def room_db = logMongo.getCollection('room_cost')
        List<DBObject> data = room_db.find(query.get(), ALL_FIELD).sort(sort).limit(size).toArray()
        List<DBObject> result = new ArrayList<DBObject>(10)
        if (StringUtils.isNotEmpty(sBegin) && data.size() > 9) {
            for (int i = 9; i >= 0; i--)
                result.add(data.get(i))
            data = result;
        }
        [code: 1, data: data, all_page: 2, count: size]
    }

    def cost_log_history(HttpServletRequest req) {
        QueryBuilder q = Web.fillTimeBetween(req)
        q.and('user_id').is(Web.getCurrentUserId())
        String type = req.getParameter("type")
        if (StringUtils.isNotEmpty(type))
            q.and('type').is(type)

        def room_db = logMongo.getCollection('room_cost_day_usr')

        Crud.list(req, room_db, q.get(), ALL_FIELD, SJ_DESC)
    }



    private Date getTime(HttpServletRequest request, String key) {
        String str = request.getParameter(key)
        if (StringUtils.isNotBlank(str)) {
            try {
                return new SimpleDateFormat("yyyy-MM-dd").parse(str);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        return null;
    }


    def day_login(HttpServletRequest req) {
        def user = Web.day_login(req, Web.getCurrentUserId());
        [code: 1]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup User
     * @apiName kick_ttl
     * @api {get} user/kick_ttl/:token/:roomId  用户踢出剩余时间
     * @apiDescription
     * 获取用户被踢出房间的剩余时间
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Number} roomId  家族房间ID
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/user/kick_ttl/9e9a0d008ff62e6a5230f2eed9cb8299/1202303
     *
     * @apiSuccessExample {json} Success-Response:
     *
     *     {
                "code": 1,"data": {"ttl": 100  剩余时间(秒)}
            }
     *
     */
    def kick_ttl(HttpServletRequest req) {
        [code: 1, data: [ttl: userRedis.getExpire(KeyUtils.ROOM.kick(Web.roomId(req), Web.currentUserId()))]]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup User
     * @apiName shutup_ttl
     * @api {get} user/shutup_ttl/:token/:roomId  用户禁言剩余时间
     * @apiDescription
     * 获取用户在房间内被禁言剩余时间
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Number} roomId  家族房间ID
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/user/shutup_ttl/9e9a0d008ff62e6a5230f2eed9cb8299/1202303
     *
     * @apiSuccessExample {json} Success-Response:
     *
     *     {
     "code": 1,"data": {"ttl": 100  剩余时间(秒)}
     }
     *
     */
    def shutup_ttl(HttpServletRequest req) {
        [code: 1, data: [ttl: userRedis.getExpire(KeyUtils.ROOM.shutup(Web.roomId(req), Web.currentUserId()))]]
    }


    def logout(HttpServletRequest req) {
        def token = OAuth2SimpleInterceptor.parseToken(req)
        if (token) {
            userRedis.delete(KeyUtils.accessToken(token))
            userRedis.delete(KeyUtils.USER.token(Web.getCurrentUserId()))
        }
        def userId = Web.getCurrentUserId();
        Long now = System.currentTimeMillis()
        String id = "${userId}_${now}"
        logMongo.getCollection("day_logout").insert($$(_id: id, user_id: userId, ip: Web.getClientId(req), timestamp: now))
        [code: 1]
    }

    private static final Integer ACCUSE_COST = 0

    private static final String[] TYPES = ["教唆传销", "低俗色情", "政治违规", "暴力犯罪", "外站拉人"]

    def accuse(HttpServletRequest req) {
        Integer uid = Web.getCurrentUserId()
        Integer roomId = Web.firstNumber(req)
        if (roomId == null) {
            return Result.丢失必需参数
        }
        def type = ServletRequestUtils.getIntParameter(req, 'type', 1);
        def desc = req.getParameter('desc')
        String[] pics = req.getParameterValues("pics[]") //上传至又拍云 保存地址
        String snapshot = req.getParameter("snapshot") //自动截图flash 直接保存base64字符串
        def curr = System.currentTimeMillis()
        if (1 == users().update(
                new BasicDBObject(_id: uid, 'finance.coin_count': [$gte: ACCUSE_COST]),
                new BasicDBObject($inc: ['finance.coin_count': -ACCUSE_COST]),
                false, false, writeConcern
        ).getN()) {
            adminMongo.getCollection("accuse").save($$([_id      : curr, uid: uid, type: type, desc: desc,
                                                                                            status   : 1, roomId: roomId, snapshot: snapshot,
                                                                                            pics     : pics,
                                                                                            timestamp: curr
            ]));
            logMongo.getCollection("accuse_logs").insert($$(_id: uid + "_" + curr,
                    uid: uid,
                    aid: curr,
                    type: 'cost',
                    coin: ACCUSE_COST,
                    timestamp: curr))
            return [code: 1]
        }
        return [code: 30412]
    }

    def accuse_log(HttpServletRequest req) {
        def query = Web.fillTimeBetween(req).and('uid').is(Web.getCurrentUserId())
        Crud.list(req, adminMongo.getCollection("accuse"), query.get(), ALL_FIELD, SJ_DESC) { List<BasicDBObject> data ->
            /*def users = users()
            for(Map obj : data){
                Integer to_id = obj.get('to_id') as Integer
                if(to_id){
                    obj.put('to_name',users.findOne(to_id,new BasicDBObject('nick_name',1))?.get('nick_name'))
                }
                String via = obj.get('via') as String
                obj.put('via_desc', PAY_DESC[via])
            }*/
        }
    }

    /**
     * 修改密码
     * @param req
     * @return
     */
    def change_pwd(HttpServletRequest req) {
        String oldpwd = req.getParameter('oldpwd')
        String newpwd = req.getParameter('newpwd')
        String token = OAuth2SimpleInterceptor.parseToken(req)
        String token_key = KeyUtils.accessToken(token)
        Map result = UserWebApi.changePwd(token, oldpwd, newpwd)
        if (result == null)
            return [code: 0]
        if (((Number) result.get("code")).intValue() != 1) {
            return [code: result.get("code")]
        }
        final Map data = (Map) result.get("data");
        String newToken = (String) data.get("token");
        String new_token_key = KeyUtils.accessToken(newToken)
        if (userRedis.hasKey(token_key))
            userRedis.rename(token_key, new_token_key)
        [code: 1, data: [token: newToken]]
    }

    /**
     * 用户信息是否完善
     * @param req
     */
    def info_complete(HttpServletRequest req) {
        def user = (DBObject) users().findOne(Web.getCurrentUserId(), user_bind_info_field)
        if (null == user) {
            logger.error(":user---------->:is null")
            //logout(req)
            return [code: 30405, msg: "user用户信息为Null"]
        }
        def via = (user['via'] ?: 'union') as String
        Boolean mobile_bind = (user['mobile_bind'] ?: false) as Boolean
        Boolean uname_bind = (user['uname_bind'] ?: false) as Boolean
        [code: 1, data: [complete: user_complete(user), mobile_bind: mobile_bind, uname_bind: uname_bind, via: via]]
    }

    public Boolean user_complete(DBObject user) {
        //已完善
        def complete = Boolean.FALSE
        if (null == user) {
            return complete
        }
        def via = (user['via'] ?: 'union') as String
        Boolean mobile_bind = (user['mobile_bind'] ?: false) as Boolean
        Boolean uname_bind = (user['uname_bind'] ?: false) as Boolean

        //本地手机注册的 或者绑定用户名密码的
        if ((via.equals('local') && mobile_bind) || uname_bind) {
            complete = Boolean.TRUE
        }
        return complete
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup User
     * @apiName mobile
     * @api {get} user/mobile/:token  用户手机号
     * @apiDescription
     * 用户获取绑定手机号
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/user/mobile/031928b93e543825298982e06a00796c
     */
    def mobile(HttpServletRequest req) {
        String access_token = OAuth2SimpleInterceptor.parseToken(req)
        Map _jsonMap = UserWebApi.fetchUser(access_token);
        final Map data = (Map) _jsonMap.get("data");
        String mobile = (String) data.get("mobile") ?: "";
        //mobile = StringUtils.substring(mobile, 0, 3) + "****" + StringUtils.substring(mobile, -4)
        [code: 1, data: [mobile: mobile]];
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup User
     * @apiName bind_mobile
     * @api {get} user/bind_mobile/:token  绑定手机号
     * @apiDescription
     * 用户绑定手机号
     *
     * @apiUse USER_COMMEN_PARAM

     * @apiParam {String} mobile  用户手机号
     * @apiParam {String} sms_code  短信验证码
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/user/bind_mobile/031928b93e543825298982e06a00796c
     */
    def bind_mobile(HttpServletRequest req) {
        String token = OAuth2SimpleInterceptor.parseToken(req)
        String mobile = req.getParameter('mobile')
        String sms_code = req.getParameter('sms_code')
        String pwd = req.getParameter('pwd')
        def user = users().findOne(Web.getCurrentUserId(), user_bind_info_field) as BasicDBObject
        if (user == null) {
            return Result.丢失必需参数
        }
        Map result = UserWebApi.bindMobile(user['tuid'] as String, token, mobile, sms_code, pwd)
        if (result == null)
            return  Result.error
        if (((Number) result.get("code")).intValue() != 1) {
            return [code: result.get("code")]
        }
        final Integer uid = Web.getCurrentUserId()

        def query = $$('_id', uid)
        def update = $$($set: $$('mobile_bind': true))
        if (users().update(query, update, false, false, writeConcern).getN() == 1) {
            return Result.success
        }
        return Result.error
    }

    /**
     * api {get} user/user_info/:token/:user_id?room_id=:room_id  查看用户资料卡
     * 查看用户资料卡
     */
    def user_info(HttpServletRequest req){
        def user_info_field = new BasicDBObject(
                user_name:1,sex:1,nick_name:1,priv:1,pic:1,stature:1,location:1,constellation:1,
                bag:1,timestamp:1,mm_no:1, bg_url:1, 'birthday':1, 'signature':1,level:1
        )
        def uid = Web.firstNumber(req)
        def currentUid = Web.currentUserId
        def user = users().findOne(uid,user_info_field)
        if(null == user)
            user = users().findOne($$('mm_no':uid),user_info_field)

        if(null == user)
            return [code: 0]

        def friend = [friend: false] as Map
        if (uid != currentUid) {
            //friend = friendController.is_friend(req)['data'] as Map
        }
        user.putAll(friend)

        /*List<DBObject> items = items().find($$(status:1), $$(status:0)).toArray()
        def userBag = users().findOne(currentUid, $$(bag: 1))?.get('bag') as Map
        items.each {DBObject item ->
            Map bag = userBag?.get(item[_id] as String) as Map
            item['count']=(bag?.get('count') ?: 0) as Integer
        }
        user.put('items', items)*/
        /*def count = 1
        if (user['pics'] != null) {
            count = count + (user.get('pics') as List).size()
        }
        user.put('pic_count', count)
        //标签
        def tags = [] as List
        if (user['tag_ids'] != null) {
            user['tag_ids'].each {
                def cat = adminMongo.getCollection('tags').findOne($$(_id: it as Integer), $$(cat: 1))?.get('cat')
                tags.add(cat ?: '')
            }
        }
        user.put('tags', tags)*/

        [code: 1 ,data:user]
    }

    public static final int MIN_BONUS_DIAMOND = 10
    public static final int MAX_BONUS_DIAMOND = 20

    def test_invite(HttpServletRequest req) {
        if (!isTest) return
        Integer userId = Web.getCurrentUserId()
        mainRedis.opsForValue().set(KeyUtils.USER.fresh(userId), '' + userId, 60l, TimeUnit.SECONDS)

        return invite(req)
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup User
     * @apiName invite
     * @api {get} user/invite/:token/:invite_code  填写邀请码赚积分
     * @apiDescription
     * 填写邀请码赚积分
     *
     * @apiUse USER_COMMEN_PARAM

     * @apiParam {String} invite_code  用户填写的邀请码
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/user/invite/031928b93e543825298982e06a00796c/1201085
     *
     * @apiSuccessExample {json} Success-Response:
     * {
     *     code: 1
     * }
     */
    def invite(HttpServletRequest req) {
        return
        /*Integer userId = currentUserId//invitee
        Integer invitor = Web.firstParam(req) as Integer//invitor
        if (invitor == userId) {
            return Result.邀请码无效
        }
        if (!mainRedis.hasKey(KeyUtils.USER.fresh(userId))) {
            return Result.邀请码无效
        }
        if (users().findOne($$(_id: invitor)) == null) {
            return Result.邀请码无效
        }
        def currentUser = users().findOne($$(_id: invitor), $$(mission: 1))
        if (currentUser['mission'] == null || currentUser['mission']['invite'] == null) {
            return Result.邀请码无效
        }
        def diamond_count = currentUser['mission']['invite'] as Integer
        def update = $$(_id: userId, user_id: userId, session: Web.getSession(), invitor: invitor, timestamp: System.currentTimeMillis())
        //签到数量调整
        RedisLock lock = new RedisLock("${userId}:invite".toString())
        try {
            lock.lock()
            if (invitor_logs().count($$(_id: userId)) > 0l) {
                return Result.已添加邀请码
            }
            if (diamond_count > 0 && 1 == users().update($$(_id: invitor, 'mission.invite': [$gt: 0]), $$($inc: ['mission.invite': -1]), false, false, writeConcern).getN()) {
                //def count = RandomExtUtils.randomBetweenMinAndMax(MIN_BONUS_DIAMOND, MAX_BONUS_DIAMOND)
                def count = MAX_BONUS_DIAMOND
                update.put('diamond_count', count)
                update.put('beyond_toplimit', false)
                update.put('is_used', false)
                if (invitor_logs().save(update, writeConcern)) {
                    def log = Web.awardLog(invitor, UserAwardType.邀请钻石, [diamond: count])
                    def succ = addDiamond(invitor, Long.parseLong('' + count), log)
                    if (succ) {
                        return Result.success
                    }
                }
            } else {
                update.put('beyond_toplimit', true)
                if (invitor_logs().count($$(_id: userId)) == 0l && invitor_logs().save(update, writeConcern)) {
                    return Result.success
                }
            }
            return Result.已添加邀请码
        } finally {
            lock.unlock()
        }*/
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup User
     * @apiName invite_list
     * @api {get} user/invite_list/:token  邀请到的用户列表
     * @apiDescription
     * 邀请到的用户列表
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/user/invite_list/031928b93e543825298982e06a00796c
     *
     * @apiSuccessExample {json} Success-Response:
     * {
     *     code: 1,
     *     data: [{
     *         _id: 记录ID,
     *         user_id: 邀请到的用户ID,
     *         diamond_count: 奖励钻石,
     *         points_count: 奖励积分,
     *         timestamp: 时间戳,
     *         session: { //邀请到的用户信息
     *             {
     *             	"level" : "2",
     *             	"pic" : "https://aiimg.sumeme.com/49/1/1500964546481.png",
     *             	"nick_name" : "萌新436907",
     *             	"_id" : "1203357",
     *             	"priv" : "3"
     *             }
     *         }
     *     }],
     *     all_page: 10
     *     count: 123
     * }
     *
     */
    def invite_list(HttpServletRequest req) {
        def invitor = Web.getCurrentUserId()
        if (invitor == null) {
            return Result.权限不足
        }
        def query = $$(invitor: invitor, beyond_toplimit: [$ne: true])
        Crud.list(req, invitor_logs(), query, ALL_FIELD, SJ_DESC)
    }

    def weixin_follow_url = '1'

    /**
     * @apiVersion 0.0.1
     * @apiGroup User
     * @apiName follow_weixin
     * @api {get} user/follow_weixin/:token  微信关注提示
     * @apiDescription
     * 微信关注提示
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/user/follow_weixin/031928b93e543825298982e06a00796c
     *
     * @apiSuccessExample {json} Success-Response:
     * {
     *     code: 1,
     *     data: {
     *          url: 'http://',
     *          diamond: 38
     *     }
     * }
     *
     */
    def follow_weixin(HttpServletRequest req) {
        def userId = Web.getCurrentUserId()
        def diamond = missionController.award_follow_weixin
        //今天是否需要提醒,查询数据库是否有关注记录
        def user = users().findOne($$(_id: userId), $$(weixin_focus: 1, weixin_next: 1))
        if (user['weixin_focus'] == 1 && (user['weixin_next'] as Long) >= System.currentTimeMillis()) {
            return Result.success
        }
        def openid = weixinJSController.openid(userId) as String
        if (StringUtils.isBlank(openid)) {
            return [code: 1, data: [url: weixin_follow_url, diamond: diamond]]
        }
        // 查询该openid对应的用户是否已关注公众号
        def info = weixinJSController.weixin_user_info(openid) as Map
        //已关注 save db
        if (info != null && (info['subscribe'] as Integer) != 0) {
            def next_time = new Date().clearTime().getTime() + DAY_MILLON
            /*if (1 == users().update($$(_id: userId, weixin_focus: [$ne: 1]), $$($set: [weixin_focus: 1, weixin_next: next_time, weixin_focus_timestamp: System.currentTimeMillis()]),
                    false, false, writeConcern).getN()) {
                //首次关注 奖励钻石
                missionController.award_diamond_follow_weixin(userId)
            } else {
                users().update($$(_id: userId), $$($set: [weixin_next: next_time]), false, false, writeConcern)
            }*/
            //设置备注
            weixinJSController.weixin_set_remark(openid, '' + userId)
            return [code: 1]
        }
        //未关注
        return [code: 1, data: [url: weixin_follow_url, diamond: diamond]]

    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup User
     * @apiName share_pic
     * @api {get} user/share_pic/:token  用户头像base64值
     * @apiDescription
     * 用户头像base64值
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/user/share_pic/031928b93e543825298982e06a00796c
     *
     * @apiSuccessExample {json} Success-Response:
     * {
     *     code: 1,
     *     data: 'base64 img'
     * }
     *
     */
    def share_pic(HttpServletRequest req) {
        def user = users().findOne(Web.getCurrentUserId())
        def pic = user.get('pic') as String
        URL url = new URL(pic)
        //打开链接
        HttpURLConnection conn = (HttpURLConnection)url.openConnection()
        //设置请求方式为"GET"
        conn.setRequestMethod("GET")
        //超时响应时间为5秒
        conn.setConnectTimeout(5 * 1000)
        //通过输入流获取图片数据
        InputStream inStream = conn.getInputStream()

        ByteArrayOutputStream outStream = new ByteArrayOutputStream()
        //创建一个Buffer字符串
        byte[] buffer = new byte[1024]
        //每次读取的字符串长度，如果为-1，代表全部读取完毕
        int len = 0
        //使用一个输入流从buffer里把数据读取出来
        while( (len=inStream.read(buffer)) != -1 ) {
            //用输出流往buffer里写入数据，中间参数代表从哪个位置开始读，len代表读取的长度
            outStream.write(buffer, 0, len)
        }
        //关闭输入流
        inStream.close()
        outStream.close()
        //把outStream里的数据写入内存
        byte[] bytes = outStream.toByteArray()
        return [code: 1, data: Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP)]
    }


    /**
     * api {get} user/edit_pics/:token/?origin=:origin&pic=:pic  修改用户图片
     */
    def edit_pics(HttpServletRequest req) {
        def origin = ServletRequestUtils.getStringParameter(req, 'origin')
        def pic = ServletRequestUtils.getStringParameter(req, 'pic')
        if (StringUtils.isBlank(origin) && StringUtils.isBlank(pic)) {
            return Result.丢失必需参数
        }
        def userId = Web.currentUserId
        def pics = (users().findOne(userId, $$(pics: 1))?.get('pics') ?: []) as List
        def update = new BasicDBObject()
        //新增
        if (StringUtils.isBlank(origin) && StringUtils.isNotBlank(pic)) {
            pics.add(pic)
        }
        //替换
        if (StringUtils.isNotBlank(origin) && StringUtils.isNotBlank(pic)) {
            if (pics.contains(origin)) {
                pics.set(pics.indexOf(origin), pic)
            }
        }
        //删除
        if (StringUtils.isNotBlank(origin) && StringUtils.isBlank(pic)) {
            if (pics.contains(origin)) {
                pics.remove(pics.indexOf(origin))
            }
        }
        update.put('pics', pics)
        if (users().update(new BasicDBObject(_id, userId), new BasicDBObject($set, update), false, false, writeConcern).getN() == 1) {
            return Result.success
        }
        return Result.error
    }
/*

    */
/**
     * 是否绑定微信
     * @param req
     *//*

    def is_bind_openId(HttpServletRequest req) {
        logger.debug('Received is bind openid params is  {}', req.getParameterMap())
        Integer userId = Web.getCurrentUserId()
        def user = users().findOne($$('_id': userId), $$('account': 1))
        def account = user.containsField('account') ? user['account'] as Map : [:]
        if (!account.containsKey('open_id')) {
            return [code: ResultCode.提交成功.code, data: [is_bind_openId: Boolean.FALSE]]
        }

        return [code: ResultCode.提交成功.code, data: ['is_bind_openId': Boolean.TRUE]]
    }

    */
/**
     * 绑定提现账号
     *//*

    def bind_withdraw_account(HttpServletRequest req) {
        def open_id = ServletRequestUtils.getStringParameter(req, 'open_id', '')
        if (StringUtils.is(open_id)) {
            Result.丢失必需参数
        }
        Integer userId = Web.getCurrentUserId()
        def query = $$('_id': userId, 'status': Boolean.TRUE)
        def user = users().findOne(query, $$('account': 1))
        def account = user.containsField('account') ? user['account'] as Map : [:]
        def user_open_id = account.containsKey('open_id') ? account['open_id'] as String : ''
        // 如果该用户已经绑定过 或者 绑定的openId相同
        if (StringUtils.isNotBlank(user_open_id) || user_open_id == open_id) {
            return [code: ResultCode.重复绑定.code]
        }
        account.put('open_id', open_id)
        def update = $$('$set': $$('account': account))
        if (users().update(query, update, false, false, writeConcern).getN() == 0) {
            return [code: ResultCode.系统错误.code]
        }

        return [code: ResultCode.提交成功.code]
    }

    def bind_open_id(HttpServletRequest req){
        logger.debug('Received bind open id params is {}',req.getParameterMap())
        def openId = req.getParameter('open_id')
        Integer userId = Web.getCurrentUserId()
        logger.debug('userId is {}',userId)
        def query = $$('_id':userId,'status':Boolean.TRUE)
        def field = $$('account':1)
        def user = users().findOne(query,field)
        logger.debug('user is {}',user)

        if(user ==null){
            return [code: ResultCode.数据库没有此对象.code]
        }

        def account = user.containsField('account') ? user['account'] as Map : [:]
        if(account.containsKey('open_id')){
            return [code: ResultCode.重复绑定.code]

        }

        account.put('open_id',openId)
        def update = $$('$set',$$('account':account))
        if(users().update(query,update,false,false,writeConcern).getN() != 1){
            logger.error('update mongodb error')
            return [code: ResultCode.系统错误.code]
        }

        return [code: ResultCode.提交成功.code]
    }
*/

    private final static Integer UNBIND_MOBILE_COIN = 500
    /**
     * 解绑手机号
     * @param req
     * @return
     */
    def unbind_mobile(HttpServletRequest req) {
        final String token = OAuth2SimpleInterceptor.parseToken(req)
        final String sms_code = req.getParameter('sms_code')
        final Integer userId = Web.getCurrentUserId()
        def user = (BasicDBObject) users().findOne(userId, user_bind_info_field)
        if (null == user) {
            return [code: 30405, msg: "user用户信息为Null"]
        }
        final tuid = user['tuid'] as String
        def success = costCoin(userId, UNBIND_MOBILE_COIN, [
                costSuccess: {
                    Map result = UserWebApi.unbindMobile(tuid, token, sms_code)
                    if (result == null)
                        return false
                    if (((Number) result.get("code")).intValue() != 1) {
                        return false
                    }
                    return users().update($$(_id, userId), $$($set, $$(mobile_bind: false)), false, false, writeConcern).getN() == 1
                },
                costLog    : {
                    Web.logCost("unbind_mobile", UNBIND_MOBILE_COIN, null, null)
                }
        ] as DoCost)
        return success ? Result.success : Result.余额不足
    }

    /**
     * 绑定用户名
     * @param req
     */
    def bind_userName(HttpServletRequest req) {
        String token = OAuth2SimpleInterceptor.parseToken(req)
        String userName = req.getParameter('userName')
        String pwd = req.getParameter('pwd')
        def user = (BasicDBObject) users().findOne(Web.getCurrentUserId(), user_bind_info_field)
        if (null == user) {
            return [code: 30405, msg: "user用户信息为Null"]
        }
        def via = (user['via'] ?: 'union') as String
        Boolean uname_bind = (user['uname_bind'] ?: false) as Boolean
        if (uname_bind) {
            return [code: 30425, msg: "已绑定用户名"]
        }
        Map result = null;
        if (token.charAt(2) == UserFrom.FLAG && via.equals('union')) {
            //注册联运账户
            result = UserWebApi.bindUnionUserName(user['tuid'] as String, user['via'] as String, userName, pwd,
                    user['nick_name'] as String, user['pic'] as String, user['_id'] as String)
        } else {
            result = UserWebApi.bindUserName(token, userName, pwd)
        }
        if (result == null)
            return [code: 0]
        if (((Number) result.get("code")).intValue() != 1) {
            return [code: result.get("code")]
        }
        Integer uid = Web.getCurrentUserId()
        if (users().update($$(_id, uid), $$($set, $$(uname_bind: true)), false, false, writeConcern).getN() == 1) {
            //信息完善
            final Map data = (Map) result?.get("data");
            String newToken = (String) data?.get("token");
//            missionController.complete(Mission.用户信息完善, uid)
            return [code: 1, data: [token: newToken]]
        }
        return [code: 0]
    }

    /**
     * 绑定邮箱
     * @param req
     */
    def bind_email(HttpServletRequest req) {
        String token = OAuth2SimpleInterceptor.parseToken(req)
        String email = req.getParameter('email')
        String pwd = req.getParameter('pwd')
        def user = (BasicDBObject) users().findOne(Web.getCurrentUserId(), user_bind_info_field)
        if (null == user) {
            return [code: 30405, msg: "user用户信息为Null"]
        }
        Boolean uname_bind = (user['email'] ?: false) as Boolean
        if (uname_bind) {
            return [code: 30425, msg: "已帮定邮箱"]
        }
        Map result = null;
        /*def via = (user['via'] ?: 'union') as String
        if(token.charAt(2) == UserFrom.FLAG && via.equals('union')){
            //注册联运账户
            result = UserWebApi.bindUnionUserName(user['tuid'] as String, user['via'] as String,userName, pwd,
                    user['nick_name'] as String,user['pic'] as String,user['_id'] as String)
        }else{
            result = UserWebApi.bindUserName(token, userName, pwd)
        }*/
        result = UserWebApi.bindEmail(token, email, pwd)
        if (result == null)
            return [code: 0]
        if (((Number) result.get("code")).intValue() != 1) {
            return [code: result.get("code")]
        }
        Integer uid = Web.getCurrentUserId()
        if (users().update($$(_id, uid), $$($set, $$(email: email)), false, false, writeConcern).getN() == 1) {
            //信息完善
            final Map data = (Map) result?.get("data");
            String newToken = (String) data?.get("token");
//            missionController.complete(Mission.用户信息完善, uid)
            return [code: 1, data: [token: newToken]]
        }
        return [code: 0]
    }
    /**
     * 记录用户行为日志
     * @param req
     * @return
     */
    def record_event(HttpServletRequest req) {
        def userId = Web.getCurrentUserId();
        String value = Web.firstParam(req)
        if (StringUtils.isEmpty(value)) {
            return Result.丢失必需参数;
        }
        if (value.length() > 1000) {
            return Result.权限不足
        }
        logMongo.getCollection('user_event')
                .update($$(_id, userId), $$($set: ['last_charge': value]), true, false)
        [code: 1]
    }

    /**
     * 用户历史记录
     * @param req
     * @return
     */
    def event_histroy(HttpServletRequest req) {
        def userId = Web.getCurrentUserId();
        def user_event = logMongo.getCollection('user_event').findOne(userId)
        [code: 1, data: user_event]
    }



}
