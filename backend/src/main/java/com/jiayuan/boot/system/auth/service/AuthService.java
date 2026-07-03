package com.jiayuan.boot.system.auth.service;

import com.jiayuan.boot.system.auth.model.vo.LoginRequestVO;
import com.jiayuan.boot.system.auth.model.vo.LoginResponseVO;
import com.jiayuan.boot.system.auth.model.vo.RegisterRequestVO;

/**
 * 认证服务接口
 *
 * @author didongchen
 * @since 2026/04/10
 */
public interface AuthService {

    /**
     * 用户注册
     *
     * @param request 注册请求
     */
    void register(RegisterRequestVO request);

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 登录响应（含 JWT Token）
     */
    LoginResponseVO login(LoginRequestVO request);

}
