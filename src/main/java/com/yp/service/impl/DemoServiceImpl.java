package com.yp.service.impl;

import mvcframework.annotation.GPService;
import com.yp.service.IDemoService;

/**
 * Created by pp on 2020/3/12.
 */
@GPService
public class DemoServiceImpl implements IDemoService {

    @Override
    public String getName(String name) {
        return "My name is " + name;
    }
}
