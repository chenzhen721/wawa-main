package com.wawa.groovy

import groovy.transform.CompileStatic

/**
 *
 * 方便的动态扩展特性
 * date: 13-4-19 下午12:53
 * @author: yangyang.cong@ttpod.com
 */
@CompileStatic
class WebSupport {


//    @TypeChecked(TypeCheckingMode.SKIP)
//    private static void initHttpServletRequest(){
//        if('true'.equals(AppProperties.get('app.request_getAt')))
//        HttpServletRequest.metaClass['getProperty'] = {String name->
//            ((HttpServletRequest)delegate).getParameter(name)
//        }
//    }

    static void init(){
        //initHttpServletRequest()
    }

}
