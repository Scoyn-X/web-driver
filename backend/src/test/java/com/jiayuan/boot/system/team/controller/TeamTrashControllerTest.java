package com.jiayuan.boot.system.team.controller;

import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.team.model.vo.TeamFileResponseVO;
import com.jiayuan.boot.system.team.service.TeamFileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 团队回收站控制器单元测试。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@DisplayName("TeamTrashController 单元测试")
class TeamTrashControllerTest {

    private static final Long TEAM_ID = 9L;

    @Test
    @DisplayName("恢复团队回收站文件：返回冲突文件数据")
    void restoreTrash_returnsConflictFileData() {
        TeamFileService teamFileService = mock(TeamFileService.class);
        TeamTrashController controller = new TeamTrashController(teamFileService);
        TeamFileResponseVO conflict = new TeamFileResponseVO();
        conflict.setId(20L);
        conflict.setOriginalName("report.pdf");
        when(teamFileService.restoreTrash(TEAM_ID, 10L, null))
                .thenThrow(new TeamFileService.RestoreConflictException(conflict));

        Result<TeamFileResponseVO> result = controller.restoreTrash(TEAM_ID, 10L, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR.getCode());
        assertThat(result.getMsg()).isEqualTo("目标目录下已存在同名文件或目录");
        assertThat(result.getData()).isSameAs(conflict);
    }
}
