package com.jiayuan.boot.system.quota.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.admin.config.SystemConfigProperties;
import com.jiayuan.boot.system.quota.converter.UserQuotaConverter;
import com.jiayuan.boot.system.quota.mapper.UserQuotaMapper;
import com.jiayuan.boot.system.quota.model.entity.UserQuota;
import com.jiayuan.boot.system.quota.model.vo.QuotaResponseVO;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QuotaServiceImpl 单元测试")
class QuotaServiceImplTest {

    private static final Long CURRENT_USER_ID = 1L;
    private static final long ONE_GB = 1073741824L;
    private static final long NORMAL_TOTAL_QUOTA = 104857600L;

    @Mock private UserQuotaMapper userQuotaMapper;
    @Mock private UserQuotaConverter userQuotaConverter;
    @Mock private SystemConfigProperties configProperties;

    @InjectMocks
    private QuotaServiceImpl quotaService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    @BeforeAll
    static void initMybatisPlusLambda() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, UserQuota.class);
    }

    @BeforeEach
    void setUp() {
        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(CURRENT_USER_ID);
        when(configProperties.getNormalTotalQuota()).thenReturn(NORMAL_TOTAL_QUOTA);
        when(configProperties.getNormalSingleFileLimit()).thenReturn(NORMAL_TOTAL_QUOTA);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    // ==================== initQuota ====================

    @Nested
    @DisplayName("initQuota")
    class InitQuotaTests {

        @Test
        @DisplayName("初始化配额：普通用户默认 100MB 总配额、0 已用")
        void init_success() {
            quotaService.initQuota(CURRENT_USER_ID);

            ArgumentCaptor<UserQuota> quotaCaptor = ArgumentCaptor.forClass(UserQuota.class);
            verify(userQuotaMapper).insert(quotaCaptor.capture());
            assertThat(quotaCaptor.getValue().getUserId()).isEqualTo(CURRENT_USER_ID);
            assertThat(quotaCaptor.getValue().getTotalQuota()).isEqualTo(NORMAL_TOTAL_QUOTA);
            assertThat(quotaCaptor.getValue().getUsedSpace()).isZero();
        }
    }

    // ==================== getQuotaInfo ====================

    @Nested
    @DisplayName("getQuotaInfo")
    class GetQuotaInfoTests {

        @Test
        @DisplayName("正常获取配额信息")
        void get_success() {
            UserQuota quota = buildQuota(ONE_GB, 500L);
            when(userQuotaMapper.selectOne(any())).thenReturn(quota);
            QuotaResponseVO vo = new QuotaResponseVO();
            when(userQuotaConverter.toQuotaVO(quota)).thenReturn(vo);

            QuotaResponseVO result = quotaService.getQuotaInfo();

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("配额记录不存在：抛 USER_RESOURCE_NOT_FOUND")
        void get_notFound_throws() {
            when(userQuotaMapper.selectOne(any())).thenReturn(null);

            assertThatThrownBy(() -> quotaService.getQuotaInfo())
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
        }
    }

    // ==================== checkQuota ====================

    @Nested
    @DisplayName("checkQuota")
    class CheckQuotaTests {

        @Test
        @DisplayName("配额充足：不抛异常")
        void check_sufficient() {
            UserQuota quota = buildQuota(ONE_GB, 0L);
            when(userQuotaMapper.selectOne(any())).thenReturn(quota);

            quotaService.checkQuota(CURRENT_USER_ID, 1024L);
            // no exception
        }

        @Test
        @DisplayName("配额不足：抛 USER_QUOTA_EXHAUSTED")
        void check_exhausted_throws() {
            UserQuota quota = buildQuota(NORMAL_TOTAL_QUOTA, NORMAL_TOTAL_QUOTA - 100);
            when(userQuotaMapper.selectOne(any())).thenReturn(quota);

            assertThatThrownBy(() -> quotaService.checkQuota(CURRENT_USER_ID, 200L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_QUOTA_EXHAUSTED);
        }

        @Test
        @DisplayName("刚好等于剩余空间：不抛异常")
        void check_exactlyEqual() {
            UserQuota quota = buildQuota(NORMAL_TOTAL_QUOTA, NORMAL_TOTAL_QUOTA - 500);
            when(userQuotaMapper.selectOne(any())).thenReturn(quota);

            quotaService.checkQuota(CURRENT_USER_ID, 500L);
            // no exception
        }

        @Test
        @DisplayName("VIP 无限容量：新增容量操作不因个人配额被拦截")
        void check_vipUnlimited() {
            UserQuota quota = buildQuota(QuotaServiceImpl.UNLIMITED_TOTAL_QUOTA, ONE_GB);
            when(userQuotaMapper.selectOne(any())).thenReturn(quota);

            quotaService.checkQuota(CURRENT_USER_ID, Long.MAX_VALUE / 2);
            // no exception
        }

        @Test
        @DisplayName("普通用户单文件超过 100MB：抛 UPLOAD_FILE_TOO_LARGE")
        void checkSingleFile_normalTooLarge_throws() {
            UserQuota quota = buildQuota(NORMAL_TOTAL_QUOTA, 0L);
            when(userQuotaMapper.selectOne(any())).thenReturn(quota);

            assertThatThrownBy(() -> quotaService.checkSingleFileLimit(CURRENT_USER_ID, NORMAL_TOTAL_QUOTA + 1))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.UPLOAD_FILE_TOO_LARGE);
        }

        @Test
        @DisplayName("VIP 单文件无限制：超过 100MB 不抛异常")
        void checkSingleFile_vipUnlimited() {
            UserQuota quota = buildQuota(QuotaServiceImpl.UNLIMITED_TOTAL_QUOTA, 0L);
            when(userQuotaMapper.selectOne(any())).thenReturn(quota);

            quotaService.checkSingleFileLimit(CURRENT_USER_ID, NORMAL_TOTAL_QUOTA + 1);
            // no exception
        }
    }

    // ==================== VIP quota profile ====================

    @Nested
    @DisplayName("setVipState")
    class SetVipStateTests {

        @Test
        @DisplayName("升级 VIP：个人配额切换为无限")
        void setVipState_upgrade() {
            UserQuota quota = buildQuota(NORMAL_TOTAL_QUOTA, 0L);
            when(userQuotaMapper.selectOne(any())).thenReturn(quota);

            quotaService.setVipState(CURRENT_USER_ID, true);

            assertThat(quota.getTotalQuota()).isEqualTo(QuotaServiceImpl.UNLIMITED_TOTAL_QUOTA);
            verify(userQuotaMapper).updateById(quota);
        }

        @Test
        @DisplayName("降级普通用户：个人配额恢复为 100MB")
        void setVipState_downgrade() {
            UserQuota quota = buildQuota(QuotaServiceImpl.UNLIMITED_TOTAL_QUOTA, ONE_GB);
            when(userQuotaMapper.selectOne(any())).thenReturn(quota);

            quotaService.setVipState(CURRENT_USER_ID, false);

            assertThat(quota.getTotalQuota()).isEqualTo(NORMAL_TOTAL_QUOTA);
            verify(userQuotaMapper).updateById(quota);
        }
    }

    // ==================== increaseUsedSpace / decreaseUsedSpace ====================

    @Nested
    @DisplayName("increaseUsedSpace / decreaseUsedSpace")
    class SpaceChangeTests {

        @Test
        @DisplayName("增加已使用空间")
        void increase_success() {
            when(userQuotaMapper.increaseUsedSpace(CURRENT_USER_ID, 1024L)).thenReturn(1);

            quotaService.increaseUsedSpace(CURRENT_USER_ID, 1024L);

            verify(userQuotaMapper).increaseUsedSpace(CURRENT_USER_ID, 1024L);
        }

        @Test
        @DisplayName("并发原子增加失败：抛 USER_QUOTA_EXHAUSTED")
        void increase_quotaExceeded_throws() {
            when(userQuotaMapper.increaseUsedSpace(CURRENT_USER_ID, 1024L)).thenReturn(0);
            when(userQuotaMapper.selectOne(any())).thenReturn(buildQuota(NORMAL_TOTAL_QUOTA, NORMAL_TOTAL_QUOTA));

            assertThatThrownBy(() -> quotaService.increaseUsedSpace(CURRENT_USER_ID, 1024L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_QUOTA_EXHAUSTED);
        }

        @Test
        @DisplayName("减少已使用空间")
        void decrease_success() {
            quotaService.decreaseUsedSpace(CURRENT_USER_ID, 512L);

            verify(userQuotaMapper).decreaseUsedSpace(CURRENT_USER_ID, 512L);
        }
    }

    // ==================== helpers ====================

    private UserQuota buildQuota(long total, long used) {
        UserQuota q = new UserQuota();
        q.setId(1L);
        q.setUserId(CURRENT_USER_ID);
        q.setTotalQuota(total);
        q.setUsedSpace(used);
        return q;
    }
}
