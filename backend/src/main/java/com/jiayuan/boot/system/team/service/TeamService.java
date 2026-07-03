package com.jiayuan.boot.system.team.service;

import com.jiayuan.boot.system.team.model.vo.TeamCreateRequestVO;
import com.jiayuan.boot.system.team.model.vo.TeamQuotaResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamUpdateRequestVO;

import java.util.List;

/**
 * 团队空间服务接口
 *
 * @author didongchen
 * @since 2026/05/17
 */
public interface TeamService {

    /**
     * 创建团队空间，创建者自动成为 Owner。
     *
     * @param request 创建团队请求
     * @return 团队信息
     */
    TeamResponseVO createTeam(TeamCreateRequestVO request);

    /**
     * 查询团队详情（含成员数、Owner 名称）。
     *
     * @param teamId 团队ID
     * @return 团队信息
     */
    TeamResponseVO getTeamById(Long teamId);

    /**
     * 查询当前用户所属的团队列表。
     *
     * @return 团队信息列表
     */
    List<TeamResponseVO> listUserTeams();

    /**
     * 修改团队名称和描述（需 Owner 或 Admin）。
     *
     * @param teamId  团队ID
     * @param request 修改请求
     * @return 更新后的团队信息
     */
    TeamResponseVO updateTeam(Long teamId, TeamUpdateRequestVO request);

    /**
     * 解散团队空间（仅 Owner 可操作）。
     *
     * @param teamId 团队ID
     */
    void dissolveTeam(Long teamId);

    /**
     * 查询团队空间配额。
     *
     * @param teamId 团队ID
     * @return 配额信息
     */
    TeamQuotaResponseVO getTeamQuota(Long teamId);
}
