package com.jiayuan.boot.system.auth.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.auth.converter.AuthConverter;
import com.jiayuan.boot.system.auth.mapper.SysUserMapper;
import com.jiayuan.boot.system.auth.model.entity.SysUser;
import com.jiayuan.boot.system.auth.model.vo.LoginRequestVO;
import com.jiayuan.boot.system.auth.model.vo.LoginResponseVO;
import com.jiayuan.boot.system.auth.model.vo.RegisterRequestVO;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.security.mapper.SysAccountMapper;
import com.jiayuan.boot.system.security.model.entity.SysAccount;
import com.jiayuan.boot.system.security.util.JwtUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthServiceImpl 单元测试")
class AuthServiceImplTest {

    @Mock private SysUserMapper sysUserMapper;
    @Mock private SysAccountMapper sysAccountMapper;
    @Mock private AuthConverter authConverter;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtils jwtUtils;
    @Mock private QuotaService quotaService;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeAll
    static void initMybatisPlusLambda() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, SysAccount.class);
    }

    @BeforeEach
    void setUp() {
        // MapStruct mock 默认行为：从 request 构造实体
        when(authConverter.toSysUser(any())).thenAnswer(inv -> {
            RegisterRequestVO req = inv.getArgument(0);
            SysUser user = new SysUser();
            user.setNickname(req.getNickname());
            user.setEmail(req.getEmail());
            user.setStatus(1);
            return user;
        });
        when(authConverter.toSysAccount(any(), any(), anyString())).thenAnswer(inv -> {
            RegisterRequestVO req = inv.getArgument(0);
            Long userId = inv.getArgument(1);
            String pwd = inv.getArgument(2);
            SysAccount account = new SysAccount();
            account.setUserId(userId);
            account.setAccountName(req.getAccountName());
            account.setPassword(pwd);
            account.setAccountType(req.getAccountType());
            account.setStatus(1);
            account.setDescription("初始账户");
            return account;
        });
    }

    // ==================== 注册 ====================

    @Nested
    @DisplayName("register")
    class RegisterTests {

        @Test
        @DisplayName("正常注册：创建用户 + 创建账户 + 初始化配额")
        void register_success() {
            when(sysAccountMapper.selectCount(any())).thenReturn(0L);
            when(sysUserMapper.selectCount(any())).thenReturn(0L);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded_pwd");

            authService.register(buildRegisterRequest());

            verify(sysUserMapper).insert(any(SysUser.class));
            verify(sysAccountMapper).insert(any(SysAccount.class));
            verify(quotaService).initQuota(any());
        }

        @Test
        @DisplayName("重复账户名：抛 USERNAME_ALREADY_EXISTS")
        void register_duplicateAccountName_throws() {
            when(sysAccountMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> authService.register(buildRegisterRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USERNAME_ALREADY_EXISTS);

            verify(sysUserMapper, never()).insert(any());
        }

        @Test
        @DisplayName("重复邮箱：抛 EMAIL_ALREADY_EXISTS")
        void register_duplicateEmail_throws() {
            when(sysAccountMapper.selectCount(any())).thenReturn(0L);
            when(sysUserMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> authService.register(buildRegisterRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.EMAIL_ALREADY_EXISTS);

            verify(sysUserMapper, never()).insert(any());
        }
    }

    // ==================== 登录 ====================

    @Nested
    @DisplayName("login")
    class LoginTests {

        @Test
        @DisplayName("正常登录：返回 token 和用户信息")
        void login_success() {
            SysAccount account = buildAccount();
            SysUser user = buildUser();

            when(sysAccountMapper.selectOne(any())).thenReturn(account);
            when(passwordEncoder.matches("pass1234", "encoded_pwd")).thenReturn(true);
            when(sysUserMapper.selectById(1L)).thenReturn(user);
            when(jwtUtils.generateToken(anyLong(), anyLong(), anyString())).thenReturn("jwt_token");

            LoginResponseVO resp = authService.login(buildLoginRequest());

            assertThat(resp.getToken()).isEqualTo("jwt_token");
            assertThat(resp.getUserId()).isEqualTo(1L);
            assertThat(resp.getNickname()).isEqualTo("测试用户");
        }

        @Test
        @DisplayName("账户不存在：抛 USER_PASSWORD_ERROR")
        void login_accountNotFound_throws() {
            when(sysAccountMapper.selectOne(any())).thenReturn(null);

            assertThatThrownBy(() -> authService.login(buildLoginRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_PASSWORD_ERROR);
        }

        @Test
        @DisplayName("错误密码：抛 USER_PASSWORD_ERROR")
        void login_wrongPassword_throws() {
            SysAccount account = buildAccount();
            when(sysAccountMapper.selectOne(any())).thenReturn(account);
            when(passwordEncoder.matches("pass1234", "encoded_pwd")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(buildLoginRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_PASSWORD_ERROR);
        }

        @Test
        @DisplayName("账户被禁用：抛 USER_PASSWORD_ERROR")
        void login_accountDisabled_throws() {
            SysAccount account = buildAccount();
            account.setStatus(0);
            when(sysAccountMapper.selectOne(any())).thenReturn(account);

            assertThatThrownBy(() -> authService.login(buildLoginRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_PASSWORD_ERROR);
        }
    }

    // ==================== helpers ====================

    private RegisterRequestVO buildRegisterRequest() {
        RegisterRequestVO req = new RegisterRequestVO();
        req.setNickname("测试用户");
        req.setAccountName("test_user");
        req.setPassword("pass1234");
        req.setEmail("test@example.com");
        req.setAccountType("personal");
        return req;
    }

    private LoginRequestVO buildLoginRequest() {
        LoginRequestVO req = new LoginRequestVO();
        req.setAccountName("test_user");
        req.setPassword("pass1234");
        return req;
    }

    private SysAccount buildAccount() {
        SysAccount account = new SysAccount();
        account.setId(10L);
        account.setUserId(1L);
        account.setAccountName("test_user");
        account.setPassword("encoded_pwd");
        account.setStatus(1);
        return account;
    }

    private SysUser buildUser() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setNickname("测试用户");
        user.setEmail("test@example.com");
        user.setStatus(1);
        return user;
    }
}
