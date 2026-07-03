import { test } from "@playwright/test";
import { LoginPage } from "../pages/LoginPage";
import { FilesPage } from "../pages/FilesPage";
import { makeAccount, uniqueName } from "../helpers/users";

/**
 * 流程二：个人文件 上传 / 浏览 / 下载 / 删除 / 搜索
 * 覆盖 Lab4 5.2.3 (2.a.ⅱ)
 */
test.describe("个人文件管理", () => {
  let files: FilesPage;

  test.beforeEach(async ({ page }) => {
    const login = new LoginPage(page);
    await login.registerAndLogin(makeAccount());
    files = new FilesPage(page);
    await files.goto();
  });

  test("上传 → 浏览 → 下载 → 删除", async () => {
    const name = uniqueName("e2e-file", ".txt");

    await files.uploadFile(name, "hello playwright e2e");
    await files.expectFileVisible(name);

    await files.downloadFile(name);

    await files.deleteFile(name);
    await files.expectFileHidden(name);
  });

  test("上传两个文件并按关键词搜索", async () => {
    const alpha = uniqueName("alpha", ".txt");
    const beta = uniqueName("beta", ".txt");
    await files.uploadFile(alpha);
    await files.uploadFile(beta);

    await files.search("alpha");
    await files.expectFileVisible(alpha);
    await files.expectFileHidden(beta);
  });
});
