package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

//登录拦截器
public class LoginInterceptor implements HandlerInterceptor {

    //不被管理，通过构造函数注入
    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    //前置拦截
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 1. 获取请求头中的token，前端通过传入authorization来传token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            // 4.不存在，拦截
            response.setStatus(401);//可以直接报错，也可以返回状态码
            //返回false就是拦截
            return false;
        }
        //HttpSession session = request.getSession();

        // 2. 获取redis中的用户
        String tokenKey = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);//这个方法如果返回的map如果是null，会自动处理成empty
        //Object user = session.getAttribute("user");
        // 3.判断用户是否存在
        if(userMap.isEmpty()) {
            // 4.不存在，拦截
            response.setStatus(401);//可以直接报错，也可以返回状态码
            //返回false就是拦截
            return false;
        }
        //   将map转为UserDTO
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        // 5.存在，存放至TreadLocal
        UserHolder.saveUser(BeanUtil.copyProperties(userDTO, UserDTO.class));

        // 6. 刷新token有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES);
        //放行
        return true;
    }

    @Override
    //渲染之后的拦截
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
