package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //2. 格式不正确
            return Result.fail("手机号格式不正确");
        }
        //3. 格式符合生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4.保存验证码至session
        session.setAttribute("code",code);
        //5. 发送验证码
        log.debug("发送验证码："+code);
        //返回
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.验证手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号格式错误");
        }
        // 2.验证code
        String code = loginForm.getCode();
        Object cacheCode = session.getAttribute("code");
        if(cacheCode==null||!cacheCode.toString().equals(code)){
            return Result.fail("验证码错误");
        }
        // 3.根据手机号查找用户
        User user = query().eq("phone", loginForm.getPhone()).one();
        // 4.不存在，创建新用户
        if(user==null){
            user = createUserWithPhone(loginForm.getPhone());
        }
        // 5.保存用户到session
        session.setAttribute("user",user);

        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("test_"+RandomUtil.randomString(6));
        save(user);
        return user;
    }
}
