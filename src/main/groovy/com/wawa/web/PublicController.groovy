package com.wawa.web

import com.mongodb.BasicDBObject
import com.mongodb.DBCollection
import com.mongodb.DBObject
import com.mongodb.QueryBuilder
import com.wawa.api.notify.RoomMsgPublish
import com.wawa.base.BaseController
import com.wawa.base.Crud
import com.wawa.base.anno.Rest
import com.wawa.common.doc.IMessageCode
import com.wawa.common.doc.ParamKey
import com.wawa.common.util.JSONUtil
import com.wawa.common.util.WebUtils
import com.wawa.common.doc.Param
import com.wawa.common.doc.Result
import com.wawa.common.util.KeyUtils
import com.wawa.common.util.RandomExtUtils
import com.wawa.model.RoomType
import com.wawa.web.api.UserController
import com.wawa.api.UserWebApi
import com.wawa.api.Web
import org.apache.commons.lang.StringUtils
import org.apache.commons.lang.math.NumberUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.CollectionUtils
import org.springframework.web.bind.ServletRequestUtils
import org.springframework.web.util.HtmlUtils

import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

import static com.wawa.common.util.WebUtils.$$
import static com.wawa.common.doc.MongoKey.*

/**
 * 公共信息接口
 */
@Rest
class PublicController extends BaseController {

    Logger logger = LoggerFactory.getLogger(PublicController.class)
    @Resource
    RankController rankController

    DBCollection card_award_logs() {logMongo.getCollection("user_award_logs")}

    public static
    final BasicDBObject FAMILY_ROOM_QUERY = $$(type: RoomType.家族.ordinal(), pic_url: [$exists: true], test: [$ne: true])
    static
    final DBObject ROOM_FIELD = $$(xy_star_id : 1, family_id: 1, visiter_count: 1, found_time: 1, name: 1, pic_url: 1,app_pic_url:1,family_notice:1,follwers:1,mic_first:1,mic_sec:1)

    public static
    final BasicDBObject ROOM_LIST_FIELD = $$(xy_star_id : 1, family_id: 1, visiter_count: 1, member_count:1, found_time: 1, badge:1, name: 1, pic_url: 1, type: 1,app_pic_url:1, family_notice: 1)

    public static
    final BasicDBObject ROOM_DESC = $$('visiter_count': -1, 'member_count': -1,  'timestamp': -1)
    public static final Integer VISITOR_RATIO = 1

    //活动公告
    def inform(HttpServletRequest req) {
        int size = ServletRequestUtils.getIntParameter(req, ParamKey.In.size, 1)
        int type = ServletRequestUtils.getIntParameter(req, "type", 1)
        if (size < 0 || size > 200) {
            size = 48;
        }
        def curr = System.currentTimeMillis();
        mongoData('informs', $$([type: type, stime: [$lte: curr], etime: [$gt: curr]]), ALL_FIELD, size)
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Public
     * @apiName notice
     * @api { get } public/notice/:type?size=20  获取公告/帮助
     * @apiDescription
     * 获取0:公告  4:帮助  5:房间公告
     *
     * @apiParam
     *{Number=0:公告  4:帮助 5:房间公告} [type=0] 类型
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/public/notice/0
     *
     */
    def notice(HttpServletRequest req) {
        Integer type = ServletRequestUtils.getIntParameter(req, Param.first, 0);
        int size = ServletRequestUtils.getIntParameter(req, ParamKey.In.size, 48)
        if (size < 0 || size > 200) {
            size = 48;
        }
        def query = new BasicDBObject('status', Boolean.TRUE).append('type', type)
        /*TODO 待启用 有效期 开始和截止时间
        def curr = System.currentTimeMillis();
        query.append('etime',$$($gte,curr)).append('stime',$$($lte,curr))
        */
        def fields = $$(content: 0)
        if (type == 5) {
            fields = ALL_FIELD
        }
        mongoData('notices', query, fields, size)
    }

    def notice_one(HttpServletRequest req) {
        Integer id = ServletRequestUtils.getIntParameter(req, Param.first, 0);
        def notice = adminMongo.getCollection("notices").findOne($$(_id, id));
        if (notice == null) return [code: 0];
        [code: 1, data: notice]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Public
     * @apiName poster
     * @api { get } public/poster/:type  获取海报信息
     * @apiDescription
     * 获取 0:海报 1:网站预设背景 2:安卓广告页面 3:ios广告页面 4:ios启动页面 5:首页活动 6:个人主页背景 7:安卓启动页 8:搜索栏目 9:h5 Banner
     *
     * @apiParam
     *{Number=0:海报 1:网站预设背景 2:安卓广告页面 3:ios广告页面 4:ios启动页面 5:首页活动 6:个人主页背景 7:安卓启动页 8:搜索栏目 9:h5 Banner} [type=0] 类型
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/public/poster/0
     *
     */
    def poster(HttpServletRequest req) {
        Integer type = ServletRequestUtils.getIntParameter(req, Param.first, 0);
        def query = $$('status', Boolean.TRUE).append('type', type)
        mongoData('posters', query, ALL_FIELD, 20)
    }

    def poster_one(HttpServletRequest req) {
        Integer id = ServletRequestUtils.getIntParameter(req, Param.first, 0);
        [code: 1, data: adminMongo.getCollection("posters").findOne($$(_id, id))]
    }

    private mongoData(String collectionName, DBObject query, DBObject fields, int limit) {
        [code: 1, data: adminMongo.getCollection(collectionName).find(query, fields)
                .sort(new BasicDBObject('order', -1)).limit(limit).toArray()]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Public
     * @apiName room_viewer
     * @api {get} public/room_viewer  房间观众列表
     * @apiDescription
     * 家族房间内观众列表
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/public/room_viewer/12012233?page=1&size=20

     * @apiSuccessExample {json} Success-Response:
     *
     *     {
     *      code: 1,
     *      data{
     *      count:1
     *      list:[
     *              {
     *               'nick_name': 昵称,
                     'pic': 头像,
                     'priv': 身份,
                     'family': 家族相关信息
     *              }
     *          ]
     *      }
     *
     *     }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def room_viewer(HttpServletRequest req) {
        int page = Web.getPage(req)
        int pageSize = Web.getPageSize(req)
        Integer room_id = Web.roomId(req)
        //这个观众列表可以从im系统中访问获取
//        Set<String> viewers = userRedis.opsForSet().members(KeyUtils.ROOM.users(room_id))
        List<String> viewers = RoomMsgPublish.room_users("" + room_id)
        /*Set<String> robots = userRedis.opsForSet().members(KeyUtils.ROOM.robots(room_id))
        if (robots != null && robots.size() > 0) {
            viewers.addAll(robots)
        }*/

        int iViewSize = viewers.size()
        int count = 0
        def userList = Collections.emptyList()

        if (iViewSize > 0) {
            def uids = this.getUids(viewers)
            def query = $$('_id': [$in: uids])
            def field = $$('nick_name': 1, 'pic': 1, 'priv': 1, 'family': 1, 'vip_level': 1, 'level': 1, 'birthday': 1)
            def sort = $$('family.family_priv':1, 'level': -1)
            userList = users().find(query, field).sort(sort).skip((page - 1) * pageSize).limit(pageSize).toArray()
            count = viewers.size() * VISITOR_RATIO
            /*userList.each { DBObject user ->
                user['rb'] = Web.isRobot(user[_id] as Integer) ? 1 : 0
            }*/
        }
        return [code: 1, data: [count: count, list: userList]]
    }


    private getUids(List<String> viewers) {
        def uids = viewers.collect {
            try {
                String id = (String) it
                if ("NaN".equals(id))
                    id = 0
                Integer.valueOf(id)
            }
            catch (Exception ex) {
                ex.printStackTrace()
            }
        }
        return uids
    }

    /**
     * 直播间管理员
     * @param req
     * @return
     */
    def room_admin(HttpServletRequest req) {
        Integer room_id = Web.roomId(req)

        String adminKey = KeyUtils.ROOM.admin(room_id)
        String json = mainRedis.opsForValue().get(adminKey)

        if (StringUtils.isBlank(json)) {
            def result = [code: 1, data: roomAdmins(room_id)]
            json = JSONUtil.beanToJson(result)
            mainRedis.opsForValue().set(adminKey, json, 30, TimeUnit.SECONDS)
            return result
        }
        return JSONUtil.jsonToMap(json)
    }


    Map roomAdmins(Integer roomId) {
        List<Integer> admin_ids = rooms().findOne(roomId, new BasicDBObject("admin", 1))?.get("admin") as List<Integer>
        if (admin_ids == null || admin_ids.isEmpty()) {
            return [count: 0, list: []]
        }
        def admins = users().find($$(_id, $$($in, admin_ids)),UserController.user_info_field).sort($$(level: -1)).toArray()
        [count: admins.size(), list: admins]
    }


    /**
     * @apiVersion 0.0.1
     * @apiGroup Public
     * @apiName room
     * @api {get} public/room  家族房详细信息
     * @apiDescription
     * 获取家族房间相关信息
     *
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/public/room/1203122

     * @apiSuccessExample {json} Success-Response:
     *
     *     {
     *       code:1,
     *       data:{
     *          name: 房间名称,
     *          badge: 家族徽章图片地址,
     *          pic: 房间头像,
     *          app_pic_url:房间封面,
     *          family_notice:房间简介
     *          xy_star_id : 族长id,
     *          family_id : 家族id,
     *          visiter_count : 在线人数,
     *          member_count : 家族成员数
     *          found_time : 创建时间,
     *          follwers : 12 关注人数
     *          mic_first_user:麦1上用户信息
     *          mic_sec_user: 麦2上用户信息
     *          reward_diamond:10 大于0则显示红包按钮
     *          rank: 1 家族争霸的当前排名
     *       }
     *     }
     */
    def room(HttpServletRequest req) {
        Integer room_id = Web.roomId(req)
        def room = rooms().findOne(room_id, ROOM_FIELD)

        if (room == null) {
            return [code: 0, msg: '找不到房间']
        }
        //当前主麦和副麦用户信息
        Integer first_userId = (room.get("mic_first") ?: 0) as Integer
        Integer sec_userId = (room.get("mic_sec") ?: 0) as Integer
        if(first_userId > 0){
            room.put('mic_first_user', Web.getUserInfo(first_userId))
        }
        if(sec_userId > 0){
            room.put('mic_sec_user', Web.getUserInfo(sec_userId))
        }

        Integer visiter_count = (userRedis.opsForSet().size(KeyUtils.ROOM.users(room_id)) ?: 0) as Integer
        room.put('visiter_count', visiter_count )

        //是否显示红包按钮
        def familyDiamondKey = KeyUtils.FAMILIES.family_reward_redpack(room_id)
        def diamond = mainRedis.opsForValue().get(familyDiamondKey) as Long
        if (diamond != null && diamond > 0) {
            room.put('reward_diamond', diamond)
        }
        //家族贡献排名
        String redisKey = KeyUtils.FAMILIES.ranks()
        Long rank = Web.mainRedis.opsForZSet().reverseRank(redisKey, room_id.toString())
        room.put('rank', rank != null ? rank + 1 : 0)
        [code: 1, data: room]
    }

    def id_name(HttpServletRequest req) {
        Integer uid = Web.firstNumber(req)
        def user = users().findOne(uid, $$('nick_name': 1, mm_no: 1))
        if (user == null)
            user = users().findOne($$(mm_no: uid), $$('nick_name': 1, mm_no: 1))
        [code: 1, data: user]
    }


    def blackword_list(HttpServletRequest req) {
        Integer type = Web.firstNumber(req)
        String blackKey = KeyUtils.BLACKBLIST.blacklists(type)
        String json = mainRedis.opsForValue().get(blackKey)

        def query = $$('type', $$($in: [type, 2]))   //公共关键字 type:2
        if (StringUtils.isBlank(json)) {
            def db_obj = adminMongo.getCollection('blackwords').find(query, $$(_id: 1)).batchSize(5000).toArray()
                    .collect { it['_id'] }
            def result = [code: 1, data: db_obj]
            json = JSONUtil.beanToJson(result)
            mainRedis.opsForValue().set(blackKey, json)
            return result
        }
        return JSONUtil.jsonToMap(json)
    }

    private QueryBuilder BUILD_ROOM_LIST_QUERY(HttpServletRequest req) {
        QueryBuilder query = QueryBuilder.start()
        query.and("test").notEquals(true)

        def filter = req.getParameter("filter")//1:最新入驻 按15日内签约主播  2:最新开播 按1小时内开播主播
        if (StringUtils.isNotBlank(filter)) {
            String filed = ROOM_FILTER_FILED[Integer.valueOf(filter)]
            Long beginTime = System.currentTimeMillis() - ROOM_FILTER_TIME[filed]
            query.and(filed).greaterThan(beginTime)
        }

        //房间号
        def room_id = req.getParameter("room_id")
        if (StringUtils.isNotBlank(room_id)) {
            def room = rooms().findOne($$(_id: room_id as Integer), $$(_id: 1))
            if (room == null) {
                room = rooms().findOne($$(room_ids: room_id), $$(_id: 1))
            }
            Integer roomId = room?.get(_id) as Integer
            if (roomId) {
                query.and(_id).is(roomId)
            } else {
                Pattern pattern = Pattern.compile("^" + room_id + ".*\$", Pattern.CASE_INSENSITIVE);
                query.and("room_ids").regex(pattern)
            }
        }
        //昵称
        String nick_name = req.getParameter("nick_name")
        if (StringUtils.isNotBlank(nick_name)) {
            nick_name = HtmlUtils.htmlEscape(nick_name)
            nick_name = specialCharFilter(nick_name)
            Pattern pattern = Pattern.compile("^.*" + nick_name + ".*\$", Pattern.CASE_INSENSITIVE)
            query.and("nick_name").regex(pattern)
        }
        //标签
        String tag = req.getParameter("tag")
        if (StringUtils.isNotBlank(tag)) {
            query.and("tags").is(tag)
        }
        //城市
        String province = req.getParameter("province")
        if (StringUtils.isNotBlank(province)) {
            query.and("address.province").is(province)
        }
        //直播状态
        String live = req.getParameter("live")
        if (StringUtils.isNotBlank(live)) {
            query.and("live").is(Boolean.valueOf(live))
        }
        //性别
        String sex = req.getParameter("sex")
        if (StringUtils.isNotBlank(sex)) {
            query.and("real_sex").is(Integer.valueOf(sex))
        }
        return query;
    }

    //综合(全部)--0  | 人气--1 | 开播时间--2 | 主播等级--3 | 主播签约时间 --4 | 关注 --5
    private static
    final Map<Integer, String> ROOM_SORT_FILED = [0: 'rank_value', 1: 'visiter_count', 2: 'timestamp', 3: 'bean', 4: 'found_time', 5: 'followers', 6: 'hour_cost']

    //房间过滤条件
    private final static Long HOUR_MILLS = 3600 * 1000L
    private final static Long DAY_MILLS = 24 * 3600 * 1000L
    private static final Map<Integer, String> ROOM_FILTER_FILED = [1: 'found_time', 2: 'timestamp']
    private static final Map<String, Long> ROOM_FILTER_TIME = ['found_time': 15 * DAY_MILLS, 'timestamp': HOUR_MILLS]

    /**
     * @apiVersion 0.0.1
     * @apiGroup Public
     * @apiName room_list
     * @api { get } public/room_list?page=1&size=20  家族房列表
     * @apiDescription
     * 家族房间列表
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/public/room_list?page=1&size=20&type=
     *
     * @apiParam {int} type    不传或0-按照人气排名； 1-按照世界排名
     *
     ** @apiSuccessExample {json} Success-Response:
     *
     *     {

             "data": [
                 {
                   name: 房间名称,
                   pic_url: 家族头像,
                   app_pic_url: 家族封面,
                   family_notice: 家族简介,
                   xy_star_id : 族长id,
                   family_id : 家族id,
                   visiter_count : 在线人数,
                   member_count : 家族成员数
                   found_time : 创建时间,
                   followers : 12 关注人数
                 }
             ],
             "count": 1,
             "code": 1,
             "all_page": 1
         }
     */
    def room_list(HttpServletRequest req) {
        if ("1" == req.getParameter('type')) {
            return getRoomRankList(req)
        }

        //排序
        BasicDBObject desc = ROOM_DESC
        String sort = req.getParameter("sort")
        Integer order = ServletRequestUtils.getIntParameter(req, 'order', -1)
        String field = 'visiter_count'
        //查询
        QueryBuilder query = BUILD_ROOM_LIST_QUERY(req)
        if (StringUtils.isNotBlank(sort)) {
            desc = $$('live', -1)
            //field = ROOM_SORT_FILED.get(Integer.parseInt(sort))  //已优化20140530
            field = ROOM_SORT_FILED.get(0)  //已优化20140530
            desc = desc.append(field, order);
        }
        return getRoomList(req, desc, FAMILY_ROOM_QUERY)
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Public
     * @apiName search
     * @api { get } public/search?keyword=520  搜索家族房
     * @apiDescription
     * 搜索 房间号/靓号 昵称 标题
     * 最大搜索记录50条
     *
     * @apiParam { String } keyword  搜索关键字
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/public/search?keyword=520

     ** @apiSuccessExample {json} Success-Response:
     *
     *     {
             "count": 1,
             "data": [
                     {
                         name: 家族房间名称,
                         pic_url: 家族房间封面,
                         xy_star_id : 族长id,
                         family_id : 家族id,
                         visiter_count : 在线人数,
                         member_count : 家族成员数
                         found_time : 创建时间,
                         follwers : 12 关注人数
                    }
             ],
             "exec": 6,
             "code": 1,
             "all_page": 1
         }
     */
    def search(HttpServletRequest req) {
        String keyword = req.getParameter('keyword')
        if (StringUtils.isEmpty(keyword))
            return Result.丢失必需参数;

        BasicDBObject desc = ROOM_DESC
        QueryBuilder query = QueryBuilder.start()
        query.and("test").notEquals(true);
        Set<Integer> idSets = new HashSet()

        //房间号/靓号
        String room_id = null;
        if (NumberUtils.isNumber(keyword)) {
            room_id = keyword
        }
        if (StringUtils.isNotBlank(room_id)) {
            def room = rooms().findOne($$(_id: room_id as Integer), $$(_id: 1))
            if (room == null) {
                room = rooms().findOne($$(room_ids: room_id), $$(_id: 1))
            }
            Integer roomId = room?.get(_id) as Integer
            QueryBuilder idQuery = query;
            if (roomId) {
                idQuery.and(_id).is(roomId)
            } else {
                idQuery = buildRegexQuery('room_ids', room_id.toString())
            }
            idSets.addAll(getRoomIdsByQuery(idQuery.get()))
        }

        //昵称
        String nick_name = keyword
        if (StringUtils.isNotBlank(nick_name)) {
            def nameQuery = buildRegexQuery('nick_name', nick_name)
            idSets.addAll(getRoomIdsByQuery(nameQuery.get()))

        }
        //标题搜索
        String title = keyword
        if (StringUtils.isNotBlank(title)) {
            def titleQuery = buildRegexQuery('title', title)
            idSets.addAll(getRoomIdsByQuery(titleQuery.get()))

        }

        if (idSets == null || idSets.size() == 0) {
            return [code: 1, data: []]
        }
        //合并排序搜索
        def roomList = rooms().find($$($or, idSets.collect {$$(_id, it)}), ROOM_LIST_FIELD).sort(desc).limit(50).toArray()
        return [code: 1, data: roomList]
    }

    private QueryBuilder buildRegexQuery(String field, String value) {
        QueryBuilder query = QueryBuilder.start()
        query.and("test").notEquals(true).and("temp").notEquals(true).and("family_id").exists(false)
        value = HtmlUtils.htmlEscape(value)
        value = specialCharFilter(value)
        Pattern pattern = Pattern.compile("^.*" + value + ".*\$", Pattern.CASE_INSENSITIVE)
        query.and(field).regex(pattern)
        return query
    }

    //获得房间id
    private List<Integer> getRoomIdsByQuery(DBObject room_query) {
        def ids = []
        rooms().find(room_query, $$(_id: 1)).limit(20).toArray().each {DBObject obj ->
            ids.add(obj.get('_id') as Integer)
        }
        return ids
    }

    private getRoomList(HttpServletRequest req, BasicDBObject desc, DBObject room_query) {
        if (null == desc)
            desc = ROOM_DESC
        if (null == room_query)
            room_query = FAMILY_ROOM_QUERY

        int p = Web.getPage(req)
        Integer size = ServletRequestUtils.getIntParameter(req, ParamKey.In.size, 48)
        if (size < 0 || size > 5000) {
            size = 5000;
        }
        def pager = WebUtils.mongoPager(rooms(), room_query, ROOM_LIST_FIELD, desc, p, size)
        def roomList = pager.getData() as List<DBObject>
        String redisKey = KeyUtils.FAMILIES.ranks();
        def rankSet = mainRedis.opsForZSet()
        roomList.each { DBObject room ->
            String fid = room["family_id"] as String
            Integer rank = rankSet.reverseRank(redisKey, fid)
            room.put("rank", rank == null ? 0 : (rank+1));
        }
        [code : 1, data: roomList, all_page: pager.getAllPage(), count: pager.getCount()]
    }

    private getRoomRankList(HttpServletRequest req) {
        int page = ServletRequestUtils.getIntParameter(req, 'page', 1)
        int size = ServletRequestUtils.getIntParameter(req, 'size', 15)
        if(page < 0) page = 1
        long count = rankController.getRankCount() ?: 0
        long all_page = (long)((count + size - 1) / size)
        long end = (long)(page * size) > count ? count : (long)(page * size)
        [code: 1, data: rankController.getRankList((long)((page - 1) * size), end), all_page: all_page, count: count]
    }

    Map _lastVisiterCount


    def visiter_count() {
        Long count = 0L
        Long now = System.currentTimeMillis()
        String sCount = (String) mainRedis.opsForValue().get(KeyUtils.vistor_counts())
        if (StringUtils.isNotEmpty(sCount)) {
            try {
                count = Long.parseLong(sCount)
            }
            catch (Exception ex) {
                ex.printStackTrace()
            }
            return [code: 1, data: [count: count], ctime: now]
        }
        if (count == 0L) {
            Long lastCreateTime = _lastVisiterCount ? (Long) _lastVisiterCount.ctime : now
            if (_lastVisiterCount == null || lastCreateTime + 10 * 60L * 1000 < now)
                _lastVisiterCount = [code: 1, data: [count: this.roomUserCount()], ctime: now]
        }
        return _lastVisiterCount
    }

    private Long roomUserCount() {
        Long count = 0L
        rooms().find(new BasicDBObject('visiter_count', $$($gt, 0)), $$(visiter_count: 1)).toArray().each
                { DBObject dbo ->
                    count = count + (dbo.get("visiter_count") as Long)
                }
        return count
    }

    String[] feed_field = ['contact', 'content', 'uid', 'hid', 's', 'tid', 'app', 'f', 'net', 'v', 'rom']

    def feedback(HttpServletRequest req) {
        Long time = System.currentTimeMillis()
        def obj = new BasicDBObject(_id, time.toString())
        obj.put(timestamp, time)
        for (String k : feed_field) {
            String val = req.getParameter(k)
            if (null != val && val.length() < 400)
                obj.put(k, val)
        }
        logMongo.getCollection('feedbacks').save(obj)
        IMessageCode.OK
    }


    def show_download_info() {
        def query = $$(_id, [$in: ['download', 'download1', 'download2', 'download3']])
        //def query = $$(_id, [$in: ['download']])
        def result = adminMongo.getCollection('config').find(query, ALL_FIELD).toArray()
        [code: 1, data: result]
    }

    /**
     * 找回密码
     * @param req
     * @return
     */
    def find_pwd(HttpServletRequest req) {
        logger.debug('Received find_pwd params is {}', req.getParameterMap())
        String mobile = req.getParameter('mobile')
        String sms_code = req.getParameter('sms_code')
        String pwd = req.getParameter('pwd')
        Map result = UserWebApi.findPwd(mobile, sms_code, pwd)
        if (result == null)
            return [code: 0]
        if (((Number) result.get("code")).intValue() != 1) {
            return [code: result.get("code")]
        }
        final Map data = (Map) result.get("data");

        String newToken = (String) data.get("token");
        String new_token_key = KeyUtils.accessToken(newToken)

        String oldToken = (String) data.get("old_token");
        String old_token_key = KeyUtils.accessToken(oldToken)
        if (userRedis.hasKey(old_token_key))
            userRedis.rename(old_token_key, new_token_key)
        [code: 1, data: [token: newToken]]
    }


    /**
     * @apiVersion 0.0.1
     * @apiGroup Public
     * @apiName tag_list
     * @api { get } public/tag_list  标签列表
     * @apiDescription
     * 用户标签列表
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/public/tag_list
     *
     */
    def tag_list(HttpServletRequest req) {
        Crud.list(req, adminMongo.getCollection('tags'), EMPTY, ALL_FIELD, SJ_DESC)
    }

    private static List<String> robot_name = new ArrayList<>()

    private Map createRecord() {
        if (CollectionUtils.isEmpty(robot_name)) {
            users().find($$(via: 'robot', 'nick_name': [$exists: true]), $$(nick_name: 1)).limit(500).toArray().each { row ->
                robot_name.add(row['nick_name'] as String)
            }
        }
        return [name: robot_name.get(RandomExtUtils.randomBetweenMinAndMax(0, robot_name.size() - 1)),
                cash: RandomExtUtils.randomBetweenMinAndMax(5, 10)]
    }

}