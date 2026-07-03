package com.jiayuan.boot.system.team.controller;

import com.jiayuan.boot.system.share.controller.TeamShareController;
import com.jiayuan.boot.system.team.model.vo.CreateInvitationRequestVO;
import com.jiayuan.boot.system.team.model.vo.InvitationActionRequestVO;
import com.jiayuan.boot.system.team.model.vo.MemberRoleUpdateRequestVO;
import com.jiayuan.boot.system.team.model.vo.TeamDissolveRequestVO;
import com.jiayuan.boot.system.team.model.vo.TeamUpdateRequestVO;
import com.jiayuan.boot.system.team.model.vo.TransferOwnerRequestVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 团队权限保护路由映射测试。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@DisplayName("团队权限保护路由映射测试")
class TeamProtectedRouteMappingTest {

    @Test
    @DisplayName("受权限保护的团队控制器类路径使用 teamId")
    void protectedControllerClassMappingsUseTeamId() {
        assertRequestMapping(TeamFileController.class, "/api/v1/team/{teamId}");
        assertRequestMapping(TeamTrashController.class, "/api/v1/team/{teamId}/trash");
        assertRequestMapping(TeamShareController.class, "/api/v1/team/{teamId}/shares");
    }

    @Test
    @DisplayName("受权限保护的方法首个路径变量绑定 teamId")
    void protectedMethodsBindTeamIdPathVariable() {
        assertPreAuthorizedMethodsBindTeamId(TeamController.class);
        assertPreAuthorizedMethodsBindTeamId(TeamFileController.class);
        assertPreAuthorizedMethodsBindTeamId(TeamTrashController.class);
        assertPreAuthorizedMethodsBindTeamId(TeamShareController.class);
        assertPreAuthorizedMethodsBindTeamId(TeamPermissionController.class);
        assertPreAuthorizedMethodsBindTeamId(MemberController.class);
        assertPreAuthorizedMethodsBindTeamId(TeamInvitationController.class);
    }

    @Test
    @DisplayName("团队详情和配额路径暴露 teamId")
    void teamDetailAndQuotaMappingsUseTeamId() throws NoSuchMethodException {
        assertGetMapping(TeamController.class.getMethod("getTeamById", Long.class), "/{teamId}");
        assertGetMapping(TeamController.class.getMethod("getTeamQuota", Long.class), "/{teamId}/quota");
        assertGetMapping(TeamPermissionController.class.getMethod("getTeamPermissions", Long.class),
                "/team/{teamId}/permissions");
        assertGetMapping(TeamMenuController.class.getMethod("listTeamMenus", Long.class),
                "/api/v1/team/{teamId}/menus");
    }

    @Test
    @DisplayName("团队菜单路径在 Controller 入口做成员权限检查")
    void teamMenuMappingDeclaresPreAuthorize() throws NoSuchMethodException {
        Method method = TeamMenuController.class.getMethod("listTeamMenus", Long.class);

        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation).as("listTeamMenus PreAuthorize").isNotNull();
        assertThat(annotation.value()).isEqualTo("@requireTeamPerm.hasPerm('file:list')");
        assertFirstPathVariableName(method, "teamId");
    }

    @Test
    @DisplayName("团队管理路径暴露 teamId")
    void teamManagementMappingsUseTeamId() throws NoSuchMethodException {
        assertPutMapping(TeamController.class.getMethod("updateTeam", Long.class, TeamUpdateRequestVO.class),
                "/{teamId}");
        assertPostMapping(TeamController.class.getMethod("dissolveTeam", Long.class, TeamDissolveRequestVO.class),
                "/{teamId}/dissolve");
    }

    @Test
    @DisplayName("团队成员路径暴露 teamId")
    void memberMappingsUseTeamId() throws NoSuchMethodException {
        assertGetMapping(MemberController.class.getMethod("listMembers", Long.class), "/{teamId}/members");
        assertDeleteMapping(MemberController.class.getMethod("removeMember", Long.class, Long.class),
                "/{teamId}/members/{memberId}");
        assertDeleteMapping(MemberController.class.getMethod("exitTeam", Long.class), "/{teamId}/members/me");
        assertPutMapping(MemberController.class.getMethod(
                "updateMemberRole", Long.class, Long.class, MemberRoleUpdateRequestVO.class),
                "/{teamId}/members/{memberId}/role");
        assertPutMapping(MemberController.class.getMethod("transferOwner", Long.class, TransferOwnerRequestVO.class),
                "/{teamId}/owner/transfer");
        assertPostMapping(MemberController.class.getMethod("leaveTeam", Long.class), "/{teamId}/leave");
        assertPostMapping(MemberController.class.getMethod(
                "transferOwnerPost", Long.class, TransferOwnerRequestVO.class), "/{teamId}/transfer-owner");
    }

    @Test
    @DisplayName("团队邀请路径暴露 teamId")
    void invitationMappingsUseTeamId() throws NoSuchMethodException {
        assertGetMapping(TeamInvitationController.class.getMethod("listTeamInvitations", Long.class, String.class),
                "/api/v1/team/{teamId}/invitations");
        assertPostMapping(TeamInvitationController.class.getMethod(
                "createInvitation", Long.class, CreateInvitationRequestVO.class),
                "/api/v1/team/{teamId}/invitations");
        assertPostMapping(TeamInvitationController.class.getMethod(
                "handleInvitationAction", Long.class, InvitationActionRequestVO.class),
                "/api/v1/team/{teamId}/invitations/actions");
        assertPutMapping(TeamInvitationController.class.getMethod("acceptInvitation", Long.class, Long.class),
                "/api/v1/team/{teamId}/invitations/{invitationId}/accept");
        assertPutMapping(TeamInvitationController.class.getMethod("rejectInvitation", Long.class, Long.class),
                "/api/v1/team/{teamId}/invitations/{invitationId}/reject");
        assertGetMapping(TeamInvitationController.class.getMethod("getProcessDiagram", Long.class),
                "/api/v1/invitations/{id}/process-diagram");
    }

    @Test
    @DisplayName("团队上下文方法首个路径变量绑定 teamId")
    void teamContextMethodsBindTeamIdPathVariable() throws NoSuchMethodException {
        assertFirstPathVariableName(TeamController.class.getMethod(
                "updateTeam", Long.class, TeamUpdateRequestVO.class), "teamId");
        assertFirstPathVariableName(TeamController.class.getMethod(
                "dissolveTeam", Long.class, TeamDissolveRequestVO.class), "teamId");
        assertFirstPathVariableName(MemberController.class.getMethod("listMembers", Long.class), "teamId");
        assertFirstPathVariableName(MemberController.class.getMethod("removeMember", Long.class, Long.class),
                "teamId");
        assertFirstPathVariableName(MemberController.class.getMethod("exitTeam", Long.class), "teamId");
        assertFirstPathVariableName(MemberController.class.getMethod(
                "updateMemberRole", Long.class, Long.class, MemberRoleUpdateRequestVO.class), "teamId");
        assertFirstPathVariableName(MemberController.class.getMethod(
                "transferOwner", Long.class, TransferOwnerRequestVO.class), "teamId");
        assertFirstPathVariableName(MemberController.class.getMethod("leaveTeam", Long.class), "teamId");
        assertFirstPathVariableName(MemberController.class.getMethod(
                "transferOwnerPost", Long.class, TransferOwnerRequestVO.class), "teamId");
        assertFirstPathVariableName(TeamInvitationController.class.getMethod(
                "listTeamInvitations", Long.class, String.class), "teamId");
        assertFirstPathVariableName(TeamInvitationController.class.getMethod(
                "createInvitation", Long.class, CreateInvitationRequestVO.class), "teamId");
        assertFirstPathVariableName(TeamInvitationController.class.getMethod(
                "handleInvitationAction", Long.class, InvitationActionRequestVO.class), "teamId");
        assertFirstPathVariableName(TeamInvitationController.class.getMethod(
                "acceptInvitation", Long.class, Long.class), "teamId");
        assertFirstPathVariableName(TeamInvitationController.class.getMethod(
                "rejectInvitation", Long.class, Long.class), "teamId");
    }

    private void assertRequestMapping(Class<?> controllerClass, String expected) {
        RequestMapping mapping = controllerClass.getAnnotation(RequestMapping.class);
        assertThat(mapping).as(controllerClass.getSimpleName() + " RequestMapping").isNotNull();
        assertThat(mapping.value()).containsExactly(expected);
    }

    private void assertPreAuthorizedMethodsBindTeamId(Class<?> controllerClass) {
        Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
                .forEach(method -> assertFirstPathVariableName(method, "teamId"));
    }

    private void assertFirstPathVariableName(Method method, String expected) {
        Parameter parameter = Arrays.stream(method.getParameters())
                .filter(candidate -> candidate.isAnnotationPresent(PathVariable.class))
                .findFirst()
                .orElseThrow(() -> new AssertionError(method.getName() + " 缺少团队路径变量"));
        PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
        String actual = pathVariable.value().isBlank() ? pathVariable.name() : pathVariable.value();
        assertThat(actual).as(method.getName() + " 团队路径变量").isEqualTo(expected);
    }

    private void assertGetMapping(Method method, String expected) {
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertThat(mapping).as(method.getName() + " GetMapping").isNotNull();
        assertThat(mapping.value()).containsExactly(expected);
    }

    private void assertPostMapping(Method method, String expected) {
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        assertThat(mapping).as(method.getName() + " PostMapping").isNotNull();
        assertThat(mapping.value()).containsExactly(expected);
    }

    private void assertPutMapping(Method method, String expected) {
        PutMapping mapping = method.getAnnotation(PutMapping.class);
        assertThat(mapping).as(method.getName() + " PutMapping").isNotNull();
        assertThat(mapping.value()).containsExactly(expected);
    }

    private void assertDeleteMapping(Method method, String expected) {
        DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
        assertThat(mapping).as(method.getName() + " DeleteMapping").isNotNull();
        assertThat(mapping.value()).containsExactly(expected);
    }
}
