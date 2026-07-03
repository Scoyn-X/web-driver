import { type Page, expect } from "@playwright/test";

/**
 * 分享访问页面对象（公开链接，无需登录）。
 * 对应 /#/s/:shareToken（src/views/share/index.vue）。
 */
export class SharePage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async goto(token: string) {
    await this.page.goto(`/#/s/${token}`);
  }

  /** 断言分享信息加载成功，展示了文件名 */
  async expectFileName(name: string) {
    await expect(this.page.locator(".file-name")).toContainText(name);
  }

  /** 输入提取码并验证 */
  async verifyExtractCode(code: string) {
    await this.page.getByPlaceholder("请输入 4 位提取码").fill(code);
    await this.page.getByRole("button", { name: "验证" }).click();
    await expect(this.page.getByText("提取码验证成功")).toBeVisible();
  }

  /** 点击下载并断言下载完成 */
  async download() {
    await this.page.getByRole("button", { name: "下载文件" }).click();
    await expect(this.page.getByText("下载完成")).toBeVisible();
  }

  /** 断言分享失效（错误提示） */
  async expectInvalid() {
    await expect(this.page.getByText("分享不存在、已失效或已被取消")).toBeVisible();
  }
}
