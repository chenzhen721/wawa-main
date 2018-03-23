package com.wawa.web.api

import com.wawa.base.BaseController
import com.wawa.base.anno.Rest
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest

/**
 * 定时任务调用
 */
@Rest
class JobController extends BaseController {
    static final Logger logger = LoggerFactory.getLogger(JobController.class)

    @Resource
    CatchuController catchuController

    def catch_success_expire(HttpServletRequest req) {
        catchuController.catch_success_expire(req)
    }
}
