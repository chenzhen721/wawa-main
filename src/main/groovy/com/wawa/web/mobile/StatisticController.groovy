package com.wawa.web.mobile

import com.mongodb.BasicDBObject
import com.wawa.api.Web
import com.wawa.base.BaseController
import com.wawa.base.anno.Rest
import com.wawa.common.doc.Result
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static com.wawa.common.util.WebUtils.$$
import static com.wawa.common.doc.MongoKey.*

/**
 *手机客户端相关统计
 */
@Rest
class StatisticController extends BaseController{

    static final  Logger logger = LoggerFactory.getLogger(StatisticController.class)

    private static final String KEY = "OSERNIUGLKGFMV/ITFOBXCXA1CXA6UQ6";

    def weixin_template(HttpServletRequest req,HttpServletResponse response){
        def redirect_url = req["redirect_url"] as String
        def event = req['event'] as String
        def trace_id = req['trace_id'] as String
        def uid = req['uid'] as Integer
        if(StringUtils.isEmpty(redirect_url)){
            return  Result.丢失必需参数
        }
        if(StringUtils.isEmpty(trace_id)){
            return  Result.丢失必需参数
        }
        def log_id ="weixin_template_${trace_id}".toString()
        def info = $$(_id: log_id, type: 'weixin_template_event', trace_id:trace_id,event: event,  user_id: uid, ip: Web.getClientIp(req), timestamp: System.currentTimeMillis())
        log(info)
        String url = URLDecoder.decode(redirect_url, "UTF-8");
        logger.debug("redirect_url : {}",url)
        response.sendRedirect(url)
    }


    private void log(BasicDBObject log){
        def install_logs = logMongo.getCollection('event_logs')
        if(install_logs.count($$(_id, log.get(_id) as String)) == 0)
            install_logs.save(log)
    }
}
