package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    //注入redis
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //2. 格式不正确
            return Result.fail("手机号格式不正确");
        }
        //3. 格式符合生成验证码
        String code = RandomUtil.randomNumbers(6);

        /*4.保存验证码至session
        session.setAttribute("code",code);*/

        //4. 改为保存验证码至redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5. 发送验证码
        log.debug("发送验证码至"+phone+"："+code);
        //返回
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.验证手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 2.验证code
        String code = loginForm.getCode();
        //   从redis获取验证码
        //Object cacheCode = session.getAttribute("code");
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if(cacheCode==null||!cacheCode.equals(code)){
            return Result.fail("验证码错误");
        }
        // 3.根据手机号查找用户
        User user = query().eq("phone", phone).one();
        // 4.不存在，创建新用户
        if(user==null){
            user = createUserWithPhone(phone);
        }
        // 5.保存用户到session
        // TODO 保存用户到redis

        //  生成随机的token
        String token = UUID.randomUUID().toString(true);//不带中划线的UUID
        //  将USERDTO转为HASH类型
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);//HUtool的转换属性方法
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions
                        .create()
                        .setFieldValueEditor((name,value) -> value.toString()));//解决转为String报错
        //TODO 存储
        //session.setAttribute("user",user);
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,stringObjectMap);//putAll方法直接传Map
        //设置redis有效期
        stringRedisTemplate.expire(tokenKey,30,TimeUnit.MINUTES);//设置有效期为30分钟

        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("test_"+RandomUtil.randomString(6));
        save(user);
        return user;
    }
}
