import { test, expect } from "@playwright/test";
import { LoginPage } from "../pages/LoginPage";
import { FilesPage } from "../pages/FilesPage";
import { TeamsPage } from "../pages/TeamsPage";
import { makeAccount, uniqueName, type TestAccount } from "../helpers/users";

/**
 * 流程五：创建团队 / 邀请成员 / 接受邀请 / 按角色访问团队文件
 * 覆盖 Lab4 5.2.3 (2.a.ⅴ)
 *
 * 单页顺序切换两个账户；通过 Viewer（受限）与 Editor（可写）对比验证角色权限差异。
 */
test.describe("团队协作与角色访问", () => {
  /** 注册被邀请人 → Owner 建团队上传文件并邀请 → 被邀请人接受 → 进入团队，返回团队名 */
  async function setupTeamWithInvitee(
    page: import("@playwright/test").Page,
    role: "Editor" | "Viewer"
  ): Promise<{ teamName: string; teamFile: string }> {
    const login = new LoginPage(page);
    const teams = new TeamsPage(page);
    const files = new FilesPage(page);

    const invitee = makeAccount("inv");
    await login.register(invitee);

    const owner = makeAccount("own");
    await login.registerAndLogin(owner);
    const teamName = uniqueName("team");
    await teams.gotoList();
    await teams.createTeam(teamName);
    await teams.enterTeam(teamName);

    await teams.openTab("文件");
    const teamFile = uniqueName("team-file", ".txt");
    await files.uploadFile(teamFile);

    await teams.invite(invitee.accountName, role);

    await login.logout();
    await login.login(invitee);
    await teams.acceptInvitationFor(teamName);
    await teams.gotoList();
    await teams.enterTeam(teamName);
    await teams.openTab("文件");
    return { teamName, teamFile };
  }

  test("Viewer 接受邀请后只读访问（无上传权限）", async ({ page }) => {
    const files = new FilesPage(page);
    const { teamFile } = await setupTeamWithInvitee(page, "Viewer");

    // 可见团队文件（file:list），但 Viewer 无 file:upload，工具栏无「上传文件」
    await files.expectFileVisible(teamFile);
    await expect(page.getByRole("button", { name: "上传文件" })).toHaveCount(0);
  });

  test("Editor 接受邀请后可写访问（具备上传权限，正向权限）", async ({ page }) => {
    const files = new FilesPage(page);
    const { teamFile } = await setupTeamWithInvitee(page, "Editor");

    // Editor 具备 file:upload，工具栏出现「上传文件」，且可见既有团队文件
    await files.expectFileVisible(teamFile);
    await expect(page.getByRole("button", { name: "上传文件" })).toBeVisible();
  });
});
