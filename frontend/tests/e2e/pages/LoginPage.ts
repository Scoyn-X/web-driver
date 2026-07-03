import { type Page, type Locator, expect } from "@playwright/test";
import type { TestAccount } from "../helpers/users";

/**
 * 登录 / 注册 / 登出 页面对象。
 * 对应 src/views/login/* 与 src/layouts/components/Header/UserDropdown.vue。
 */
export class LoginPage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async goto() {
    await this.page.goto("/#/login");
    await expect(this.page.getByText("用户登录")).toBeVisible();
  }

  /** 通过登录页的「立即注册」切换到注册表单并提交注册 */
  async register(account: TestAccount) {
    await this.goto();
    // el-link 渲染为无 href 的 <a>，无 link 角色，用文本定位
    await this.page.getByText("立即注册").click();
    await expect(this.page.getByText("用户注册")).toBeVisible();

    await this.page.getByPlaceholder("请输入昵称").fill(account.nickname);
    await this.page.getByPlaceholder("2-20位，中英文/数字/下划线").fill(account.accountName);
    await this.page.getByPlaceholder("至少8位，须包含英文和数字").fill(account.password);
    await this.page.getByPlaceholder("请再次输入密码").fill(account.password);
    await this.page.getByPlaceholder("请输入邮箱").fill(account.email);
    // 账户类型默认为「个人」，无需更改

    await this.page.getByRole("button", { name: "注册" }).click();
    await expect(this.page.getByText("注册成功，请登录")).toBeVisible();
  }

  /** 在登录表单输入并提交，成功后跳转 /files */
  async login(account: Pick<TestAccount, "accountName" | "password">) {
    await this.goto();
    await this.page.getByPlaceholder("请输入账户名").fill(account.accountName);
    await this.page.getByPlaceholder("请输入密码").fill(account.password);
    await this.page.getByRole("button", { name: "登录" }).click();
    await expect(this.page.getByText("登录成功")).toBeVisible();
    await this.page.waitForURL("**/#/files");
  }

  /** 注册并登录（大多数用例的前置） */
  async registerAndLogin(account: TestAccount) {
    await this.register(account);
    await this.login(account);
  }

  /** 通过右上角头像下拉菜单退出登录 */
  async logout() {
    await this.page.locator(".user-trigger").first().click();
    await this.page.getByRole("button", { name: "退出登录" }).click();
    await this.page.waitForURL("**/#/login");
  }

  /** 断言当前处于已登录态（地址在受保护页面且有 token） */
  async expectLoggedIn() {
    const token = await this.page.evaluate(() => localStorage.getItem("token"));
    expect(token).toBeTruthy();
  }

  /** 断言当前处于未登录态 */
  async expectLoggedOut() {
    await expect(this.page).toHaveURL(/#\/login/);
    const token = await this.page.evaluate(() => localStorage.getItem("token"));
    expect(token).toBeFalsy();
  }
}
