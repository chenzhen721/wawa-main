package com.wawa.web.union

import com.mongodb.DBCollection
import com.wawa.base.BaseController
import com.wawa.base.anno.Rest
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest

import static com.wawa.common.util.WebUtils.$$
import static com.wawa.common.doc.MongoKey.*

/**
 * 联运渠道相关数据
 */

@Rest
class ChannelController extends BaseController {

    static final Logger logger = LoggerFactory.getLogger(ChannelController.class)

    DBCollection channels() { adminMongo.getCollection('channels') }

    /**
     * 获得渠道属性
     * @param req
     * @return
     */
    def prop(HttpServletRequest req) {
        logger.debug('Recv prop params {}', req.getParameterMap())
        def prop = channels().findOne($$(_id, req[_id]), $$(properties: 1, qrcode_img: 1, isAutoDownload : 1))
        return [code: 1, data: prop]
    }
}
