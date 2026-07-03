package com.jiayuan.boot.system.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.auth.converter.AuthConverter;
import com.jiayuan.boot.system.auth.mapper.SysUserMapper;
import com.jiayuan.boot.system.auth.model.entity.SysUser;
import com.jiayuan.boot.system.auth.model.vo.LoginRequestVO;
import com.jiayuan.boot.system.auth.model.vo.LoginResponseVO;
import com.jiayuan.boot.system.auth.model.vo.RegisterRequestVO;
import com.jiayuan.boot.system.auth.service.AuthService;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.security.mapper.SysAccountMapper;
import com.jiayuan.boot.system.security.model.entity.SysAccount;
import com.jiayuan.boot.system.security.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务实现
 *
 * @author didongchen
 * @since 2026/04/10
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysAccountMapper sysAccountMapper;
    private final AuthConverter authConverter;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final QuotaService quotaService;

    /**
     * 用户注册
     */
    @Override
    @Transactional
    public void register(RegisterRequestVO request) {
        // 校验账户名唯一
        Long accountCount = sysAccountMapper.selectCount(
                new LambdaQueryWrapper<SysAccount>().eq(SysAccount::getAccountName, request.getAccountName())
        );
        if (accountCount > 0) {
            throw new BusinessException(ResultCode.USERNAME_ALREADY_EXISTS);
        }

        // 校验邮箱唯一
        Long emailCount = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, request.getEmail())
        );
        if (emailCount > 0) {
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }

        // 1. 创建用户
        SysUser user = authConverter.toSysUser(request);
        sysUserMapper.insert(user);

        // 2. 为用户创建账户
        SysAccount account = authConverter.toSysAccount(
                request, user.getId(), passwordEncoder.encode(request.getPassword()));
        sysAccountMapper.insert(account);

        // 3. 初始化用户配额
        quotaService.initQuota(user.getId());
    }

    /**
     * 用户登录
     */
    @Override
    public LoginResponseVO login(LoginRequestVO request) {
        // 根据账户名查询账户
        SysAccount account = sysAccountMapper.selectOne(
                new LambdaQueryWrapper<SysAccount>().eq(SysAccount::getAccountName, request.getAccountName())
        );
        if (account == null) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }

        // 校验账户是否启用
        if (account.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }

        // 校验密码
        if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }

        // 查询用户信息
        SysUser user = sysUserMapper.selectById(account.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }

        // 生成 JWT Token
        String token = jwtUtils.generateToken(user.getId(), account.getId(), user.getNickname());
        return new LoginResponseVO(token, user.getId(), account.getId(), user.getNickname(), account.getAccountName());
    }

}

