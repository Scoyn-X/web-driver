import { test } from "@playwright/test";
import { LoginPage } from "../pages/LoginPage";
import { FilesPage } from "../pages/FilesPage";
import { TeamsPage } from "../pages/TeamsPage";
import { makeAccount, uniqueName } from "../helpers/users";

/**
 * 流程六：团队文件删除进入回收站 / 恢复 / 彻底删除
 * 覆盖 Lab4 5.2.3 (2.a.ⅵ)
 */
test.describe("团队回收站", () => {
  test("删除入回收站 → 还原 → 再删除 → 彻底删除", async ({ page }) => {
    const login = new LoginPage(page);
    const teams = new TeamsPage(page);
    const files = new FilesPage(page);

    await login.registerAndLogin(makeAccount("trash"));
    const teamName = uniqueName("team");
    await teams.gotoList();
    await teams.createTeam(teamName);
    await teams.enterTeam(teamName);

    // 上传团队文件
    await teams.openTab("文件");
    const tf = uniqueName("trash-file", ".txt");
    await files.uploadFile(tf);

    // 删除 → 进入团队回收站
    await files.deleteFile(tf);
    await files.expectFileHidden(tf);

    // 回收站还原
    await teams.openTab("回收站");
    await teams.restoreFromTrash(tf);

    // 回到文件页确认已还原
    await teams.openTab("文件");
    await files.expectFileVisible(tf);

    // 再次删除并彻底删除
    await files.deleteFile(tf);
    await teams.openTab("回收站");
    await teams.purgeFromTrash(tf);
  });
});
