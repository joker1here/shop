package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class LoginIntercepter implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //从ThreadLoacl中获取用户信息
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            //为空，设置状态码，拦截
            response.setStatus(401);
            return false;
        }
        //放行
        return true;
    }
}
