package com.stellar.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stellar.common.BusinessException;
import com.stellar.common.SecurityConstants;
import com.stellar.interceptor.WebUtils;
import com.stellar.system.dto.LoginRequest;
import com.stellar.system.dto.LoginResult;
import com.stellar.system.entity.SysUser;
import com.stellar.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String LOGIN_FAIL_PREFIX = "stellar:login-fail:";
    public static final String LOGIN_ATTEMPT_PREFIX = "stellar:login-attempt:";

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${stellar.security.login-max-fail:5}")
    private int loginMaxFail = 5;
    @Value("${stellar.security.login-lock-minutes:15}")
    private long loginLockMinutes = 15;
    @Value("${stellar.security.login-ip-max-attempts:10}")
    private int loginIpMaxAttempts = 10;
    @Value("${stellar.security.login-ip-window-minutes:10}")
    private long loginIpWindowMinutes = 10;

    /**
     * 登录（带爆破防护）：
     * 1. IP 维度短窗口尝试次数限制（10 次 / 10 分钟，配置化）；
     * 2. 账号维度失败锁定（连续失败 {loginMaxFail} 次锁 {loginLockMinutes} 分钟）；
     * 3. 成功登录清空失败计数与 IP 尝试计数。
     */
    public LoginResult login(LoginRequest request, HttpServletRequest httpRequest) {
        String username = request.getUsername();
        String ip = WebUtils.getClientIp(httpRequest);
        checkIpAttempt(ip);
        checkLocked(username);

        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username));
        if (user == null) {
            recordFail(username);
            throw new BusinessException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordFail(username);
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
        clearFail(username);
        clearIpAttempt(ip);
        StpUtil.login(user.getId());
        // S3 真·强制：默认口令首登标记未清前，会话打标由 AuthInterceptor 拦截非改密/登出/取信息外的所有受保护接口
        if (user.getMustChangePassword() != null && user.getMustChangePassword() == 1) {
            StpUtil.getSession().set(SecurityConstants.SESSION_KEY_MUST_CHANGE_PASSWORD, Boolean.TRUE);
        }
        LoginResult result = new LoginResult();
        result.setToken(StpUtil.getTokenValue());
        result.setUserInfo(user);
        return result;
    }

    public void logout() {
        StpUtil.logout();
    }

    /** IP 短窗口登录尝试计数，超阈值直接拒绝（先计数后判断，窗口随首次尝试开始） */
    private void checkIpAttempt(String ip) {
        String key = LOGIN_ATTEMPT_PREFIX + ip;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofMinutes(loginIpWindowMinutes));
        }
        if (count != null && count > loginIpMaxAttempts) {
            throw new BusinessException("登录尝试过于频繁，请稍后再试");
        }
    }

    /** 账号失败计数达到阈值则拒绝登录 */
    private void checkLocked(String username) {
        String value = stringRedisTemplate.opsForValue().get(LOGIN_FAIL_PREFIX + username);
        if (value != null && Long.parseLong(value) >= loginMaxFail) {
            throw new BusinessException("登录失败次数过多，账号已锁定，请 " + loginLockMinutes + " 分钟后再试");
        }
    }

    /** 记录一次登录失败，首次失败起算锁定窗口 */
    private void recordFail(String username) {
        String key = LOGIN_FAIL_PREFIX + username;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofMinutes(loginLockMinutes));
        }
    }

    /** 登录成功清空失败计数 */
    private void clearFail(String username) {
        stringRedisTemplate.delete(LOGIN_FAIL_PREFIX + username);
    }

    /** 登录成功清空 IP 尝试计数（合法用户周期性重回，不被自身正常登录耗尽配额） */
    private void clearIpAttempt(String ip) {
        stringRedisTemplate.delete(LOGIN_ATTEMPT_PREFIX + ip);
    }
}