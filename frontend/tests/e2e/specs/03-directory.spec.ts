import { test } from "@playwright/test";
import { LoginPage } from "../pages/LoginPage";
import { FilesPage } from "../pages/FilesPage";
import { makeAccount, uniqueName } from "../helpers/users";

/**
 * 流程三：目录 创建 / 进入 / 复制 / 移动
 * 覆盖 Lab4 5.2.3 (2.a.ⅲ)
 *
 * 全程在根目录发起复制/移动（复制→dirA、移动→dirB），最后进入 dirB 验证，
 * 避免依赖面包屑往返导航，保证用例稳定。
 */
test.describe("目录与文件复制移动", () => {
  let files: FilesPage;

  test.beforeEach(async ({ page }) => {
    const login = new LoginPage(page);
    await login.registerAndLogin(makeAccount());
    files = new FilesPage(page);
    await files.goto();
  });

  test("创建目录 → 复制文件入目录 → 移动文件入另一目录 → 进入目录验证", async () => {
    const dirA = uniqueName("dirA");
    const dirB = uniqueName("dirB");
    const file = uniqueName("e2e-cm", ".txt");

    await files.createDirectory(dirA);
    await files.createDirectory(dirB);
    await files.uploadFile(file);

    // 复制到 dirA：复制语义下，原文件仍保留在根目录
    await files.copyFileToDirectory(file, dirA);
    await files.expectFileVisible(file);

    // 移动到 dirB：移动语义下，原文件从根目录消失
    await files.moveFileToDirectory(file, dirB);
    await files.expectFileHidden(file);

    // 进入 dirB 校验文件已迁入（同时覆盖“进入目录”）
    await files.openDirectory(dirB);
    await files.expectFileVisible(file);
  });
});
