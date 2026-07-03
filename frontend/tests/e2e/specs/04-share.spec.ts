import { test, expect } from "@playwright/test";
import { LoginPage } from "../pages/LoginPage";
import { FilesPage } from "../pages/FilesPage";
import { SharePage } from "../pages/SharePage";
import { makeAccount, uniqueName } from "../helpers/users";

/**
 * 流程四：创建分享 / 访问分享 / 输入提取码 / 下载文件（含取消分享后失效）
 * 覆盖 Lab4 5.2.3 (2.a.ⅳ)
 *
 * 分享访问以「未登录的全新浏览器上下文」打开，验证公开访问链路。
 */
test.describe("文件分享与提取码访问", () => {
  test("公开分享：未登录访问并下载", async ({ page, browser }) => {
    const login = new LoginPage(page);
    await login.registerAndLogin(makeAccount());
    const files = new FilesPage(page);
    await files.goto();

    const name = uniqueName("share-pub", ".txt");
    await files.uploadFile(name);
    const { token } = await files.createShare(name, false);

    const guest = await browser.newContext();
    const guestPage = await guest.newPage();
    const share = new SharePage(guestPage);
    await share.goto(token);
    await share.expectFileName(name);
    await share.download();
    await guest.close();
  });

  test("提取码分享：输入提取码后下载", async ({ page, browser }) => {
    const login = new LoginPage(page);
    await login.registerAndLogin(makeAccount());
    const files = new FilesPage(page);
    await files.goto();

    const name = uniqueName("share-code", ".txt");
    await files.uploadFile(name);
    const { token, code } = await files.createShare(name, true);
    expect(code).not.toBeNull();

    const guest = await browser.newContext();
    const guestPage = await guest.newPage();
    const share = new SharePage(guestPage);
    await share.goto(token);
    await share.verifyExtractCode(code!);
    await share.download();
    await guest.close();
  });

  test("取消分享后链接失效（状态流转）", async ({ page, browser }) => {
    const login = new LoginPage(page);
    await login.registerAndLogin(makeAccount());
    const files = new FilesPage(page);
    await files.goto();

    const name = uniqueName("share-cancel", ".txt");
    await files.uploadFile(name);
    const { token } = await files.createShare(name, false);
    await files.cancelShare(name);

    const guest = await browser.newContext();
    const guestPage = await guest.newPage();
    const share = new SharePage(guestPage);
    await share.goto(token);
    await share.expectInvalid();
    await guest.close();
  });

  test("失效分享：访问不存在的分享 token（异常路径）", async ({ browser }) => {
    const guest = await browser.newContext();
    const guestPage = await guest.newPage();
    const share = new SharePage(guestPage);
    await share.goto("nonexistenttoken000");
    await share.expectInvalid();
    await guest.close();
  });
});
