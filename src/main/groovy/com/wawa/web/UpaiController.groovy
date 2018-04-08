package com.wawa.web

import com.mongodb.BasicDBObject
import com.wawa.base.BaseController
import com.wawa.base.anno.Rest
import com.wawa.common.util.JSONUtil
import com.wawa.common.util.MsgDigestUtil
import com.wawa.common.util.http.HttpClientUtil
import com.wawa.common.util.BusiExecutor
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest

import static com.wawa.common.doc.MongoKey.*

/**
 * 又拍云相关
 */
@Rest
class UpaiController extends BaseController{

    static final Logger logger = LoggerFactory.getLogger(UpaiController.class)

    static final String HTTP_FORM_KEY = "4BWq23g/pHl1Y7ZU39FJ55KjkFU=" //照片墙
    static final String ACCUSE_HTTP_FORM_KEY = "WhIHL/olRoJTu8QB9p5L7hitdv0=" //用户举报
    static final String NEST_HTTP_FORM_KEY = "uhhVeZ2fyZ/HpxdcktGaWnrSDag=" //小窝照片
    static final String AUDIO_NEST_FORM_KEY = "JHWLgFzhxRopA/D6WwLpZ4JBBog=" //小窝音频
    static final String STAR_VIDEO_FORM_KEY = "RXe4lE3hCkjwsh1ycaXoFZADbGk=" //主播面试视频


    static final String UPAI_NOTIFY_COLLECTION = "upai_notify"

    static final String PHOTO_DOMAIN = "http://img-album.b0.upaiyun.com";
    static final String NEST_AUDIO_DOMAIN = "http://audio-nest.b0.upaiyun.com";
    static final String NEST_PHOTO_DOMAIN = "http://img-nest.b0.upaiyun.com";
    static final String STAR_VIDEO_DOMAIN = "http://star-video.b0.upaiyun.com";
    static final String DOMAIN = "http://img-album.b0.upaiyun.com";


    static final Integer USER_DELETE = Integer.valueOf(0)
    static final String NEST_PHOTO_BUCKET = "img-nest"
    // use xylog
    //db.createCollection("upai_notify", {capped:true, size:1073741824})

//    static {
//
//        (DB) StaticSpring.get("logMongo");
//        if( ! adminDb.collectionExists(LOG_COLL_NAME) ){
//            // 1 GB
//            adminDb.createCollection(LOG_COLL_NAME,new BasicDBObject("capped",true).append("size",1<<30));
//        }
//    }

    def notify(HttpServletRequest req){
//        def code = req.getParameter('code') // 200
        def message = req.getParameter('message') as String
        def url = req.getParameter('url') as String
        def time = req.getParameter('time') as String
        if(MsgDigestUtil.MD5.digest2HEX("200&${message}&${url}&${time}&${HTTP_FORM_KEY}").equals(req.getParameter('sign'))){
            logger.debug("sign PASS")
            def notifys = logMongo.getCollection(UPAI_NOTIFY_COLLECTION)
            def obj = new BasicDBObject(_id,url)
            if(notifys.count(obj) == 0){
                obj.put(timestamp,Long.valueOf(time)*1000)
                obj.put("image_width",Integer.valueOf(req.getParameter("image-width") ?: "0"))
                obj.put("image_height",Integer.valueOf(req.getParameter("image-height") ?: "0"))
                obj.put("image_type",req.getParameter("image-type"))
                obj.put("image_frames",req.getParameter("image-frames"))
//                logger.debug("save obj: {}",obj)
                notifys.save(obj)
                return [code:1]
            }
        }
        [code:0]
    }



    @Deprecated
    static void send_audit(String path){
        def url = PHOTO_DOMAIN + path
        def sign = MsgDigestUtil.MD5.digest2HEX(url+audit_key)
        final String shenHeURL = "http://review.ttpod.com/webmsg/q/10900/s?sign=${sign}&url=${url}"
        BusiExecutor.execute(new Runnable() {
            void run() {
                HttpClientUtil.get(shenHeURL,null,HttpClientUtil.UTF8)
            }
        })
    }

    private static final String audit_key = "0ZMcF/cZM&*^tTp"

    private static final Integer NOT_ALLOWD = Integer.valueOf(1)
    @Deprecated
    def audit(HttpServletRequest req){
        def json = req.getParameter('json') as String
        def i = 0
        if(MsgDigestUtil.MD5.digest2HEX("${json}${audit_key}").equals(req.getParameter('sign'))){
            def list = JSONUtil.jsonToBean(json,ArrayList.class)
            def photos = photos()
            def ban_photos = mainMongo.getCollection("ban_photos")
            for(Object obj : list){
                def map = (Map)obj
                if(NOT_ALLOWD.equals( ((Number)map.get("code")).intValue() )){
                    String url = (String)map.get("id")
                    String path = url.replace(PHOTO_DOMAIN,"")
                    def photo =  photos.findAndRemove(new BasicDBObject(_id,path))
                    if( photo != null ){
                        i++
                        photo.put("s",NOT_ALLOWD)
                        photo.put("ban_time",System.currentTimeMillis())
                        photo.put("audit_message",map.get("message"))
                        ban_photos.save(photo)
                    }
                }
            }
        }

        [code:i]
    }

}
