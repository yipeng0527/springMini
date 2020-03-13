package com.yp.web;

import mvcframework.annotation.GPAutowired;
import mvcframework.annotation.GPController;
import mvcframework.annotation.GPRequestMapping;
import mvcframework.annotation.GPRequestParam;
import com.yp.service.IDemoService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Created by pp on 2020/3/12.
 */
@GPController
@GPRequestMapping("/demo")
public class DemoAction {

    @GPAutowired
    private IDemoService demoService;

    @GPRequestMapping("/get")
    public void getName(HttpServletRequest request, HttpServletResponse response,
                        @GPRequestParam("name") String name) throws Exception {
        String resultMsg = demoService.getName(name);
        response.getWriter().write(resultMsg);
    }

    @GPRequestMapping("/add")
    public void add(HttpServletRequest request, HttpServletResponse response,
                    @GPRequestParam("a") Integer a, @GPRequestParam("b") Integer b) throws Exception {
        Integer result = a + b;
        response.getWriter().write(result.toString());
    }
}
