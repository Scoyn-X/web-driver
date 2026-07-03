import { test } from "@playwright/test";
import { LoginPage } from "../pages/LoginPage";
import { FilesPage } from "../pages/FilesPage";
import { makeAccount, uniqueName } from "../helpers/users";

/**
 * 流程七：个人文件删除进入回收站 / 还原 / 彻底删除
 * 对应 Lab4 5.2.3 (2.a.ⅵ) 的个人空间版本（团队版本见 06-team-trash）。
 */
test.describe("个人回收站", () => {
  test("删除入回收站 → 还原 → 再删除 → 彻底删除", async ({ page }) => {
    const login = new LoginPage(page);
    await login.registerAndLogin(makeAccount("ptrash"));
    const files = new FilesPage(page);
    await files.goto();

    const f = uniqueName("ptrash-file", ".txt");
    await files.uploadFile(f);

    // 删除 → 进入回收站
    await files.deleteFile(f);
    await files.expectFileHidden(f);

    // 回收站还原
    await files.openRecycleTab();
    await files.restoreFromTrash(f);

    // 回到「我的文件」确认已还原
    await files.openMyFilesTab();
    await files.expectFileVisible(f);

    // 再次删除并彻底删除
    await files.deleteFile(f);
    await files.openRecycleTab();
    await files.purgeFromTrash(f);
  });
});
