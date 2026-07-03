import { test, expect } from "@playwright/test";
import { LoginPage } from "../pages/LoginPage";
import { makeAccount } from "../helpers/users";

/**
 * 流程一：用户注册 / 登录 / 登出（含路由守卫与参数校验异常路径）
 * 覆盖 Lab4 5.2.3 (2.a.ⅰ)
 */
test.describe("认证流程", () => {
  test("注册 → 登录 → 登出（正常主流程）", async ({ page }) => {
    const login = new LoginPage(page);
    const account = makeAccount();

    await login.register(account);
    await login.login(account);

    await login.expectLoggedIn();
    await expect(page).toHaveURL(/#\/files/);

    await login.logout();
    await login.expectLoggedOut();
  });

  test("错误密码登录失败（异常路径）", async ({ page }) => {
    const login = new LoginPage(page);
    const account = makeAccount();
    await login.register(account);

    await login.goto();
    await page.getByPlaceholder("请输入账户名").fill(account.accountName);
    await page.getByPlaceholder("请输入密码").fill("WrongPass999");
    await page.getByRole("button", { name: "登录" }).click();

    await expect(page.getByText("账户名或密码错误")).toBeVisible();
    await login.expectLoggedOut();
  });

  test("未登录访问受保护页被路由守卫拦截到登录页（鉴权）", async ({ page }) => {
    await page.goto("/#/files");
    await expect(page).toHaveURL(/#\/login/);
    await expect(page.getByText("用户登录")).toBeVisible();
    const token = await page.evaluate(() => localStorage.getItem("token"));
    expect(token).toBeFalsy();
  });

  test("注册弱密码被前端校验拦截（参数校验异常）", async ({ page }) => {
    const login = new LoginPage(page);
    await login.goto();
    await page.getByText("立即注册").click();
    await page.getByPlaceholder("请输入昵称").fill("weak");
    await page.getByPlaceholder("2-20位，中英文/数字/下划线").fill("weakpwduser");
    await page.getByPlaceholder("至少8位，须包含英文和数字").fill("short");
    await page.getByPlaceholder("请再次输入密码").fill("short");
    await page.getByPlaceholder("请输入邮箱").fill("weak@e2e.test");
    await page.getByRole("button", { name: "注册" }).click();

    // 命中前端密码规则校验，停留在注册表单，未提交成功
    await expect(page.getByText("至少8位", { exact: false })).toBeVisible();
    await expect(page.getByText("注册成功，请登录")).toHaveCount(0);
  });
});
