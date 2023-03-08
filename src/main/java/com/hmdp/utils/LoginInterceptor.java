package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

//登录拦截器
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    //前置拦截
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 1.获取session
        HttpSession session = request.getSession();
        // 2.获取session中的用户
        Object user = session.getAttribute("user");
        // 3.判断用户是否存在
        if(user==null) {
            // 4.不存在，拦截
            response.setStatus(401);//可以直接报错，也可以返回状态码
            //返回false就是拦截
            return false;
        }
        // 5.存在，存放至TreadLocal
        UserHolder.saveUser(BeanUtil.copyProperties(user, UserDTO.class));
        //放行
        return true;
    }

    @Override
    //渲染之后的拦截
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
