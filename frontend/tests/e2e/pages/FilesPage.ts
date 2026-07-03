import { type Page, type Locator, expect } from "@playwright/test";

/**
 * 个人文件浏览器页面对象。
 * 对应 /files（FileExplorerPanel → FileExplorer / FileTable）及各操作弹窗。
 *
 * 非侵入定位说明（不修改被测源码）：
 *   文件行操作为纯图标按钮（el-tooltip 包裹的 el-button，无文字/无 testid）。
 *   经探针确认，个人空间「文件」行的操作按钮顺序固定为：
 *     [0]下载 [1]分享 [2]上传到团队 [3]移动 [4]复制 [5]删除
 *   其中“删除”是该行唯一的 danger 按钮，用 .el-button--danger 稳定定位。
 */
export class FilesPage {
  readonly page: Page;
  readonly fileTable: Locator;

  constructor(page: Page) {
    this.page = page;
    this.fileTable = page.locator(".file-table");
  }

  async goto() {
    await this.page.goto("/#/files");
    await this.openMyFilesTab();
  }

  /** 切到「我的文件」标签页 */
  async openMyFilesTab() {
    await this.page.getByText("我的文件", { exact: true }).click();
    await expect(this.page.getByRole("button", { name: "上传文件" })).toBeVisible();
  }

  /** 切到「回收站」标签页 */
  async openRecycleTab() {
    await this.page.getByText("回收站", { exact: true }).click();
  }

  /** 当前目录下，按文件/目录名定位表格行 */
  fileRow(name: string): Locator {
    return this.fileTable.getByRole("row").filter({ hasText: name });
  }

  /** 文件行的操作按钮集合（仅操作单元格内的按钮） */
  private actions(name: string): Locator {
    return this.fileRow(name).getByRole("button");
  }

  async expectFileVisible(name: string) {
    await expect(this.fileRow(name)).toBeVisible();
  }

  async expectFileHidden(name: string) {
    await expect(this.fileRow(name)).toHaveCount(0);
  }

  /** 上传一个内存文件到当前目录 */
  async uploadFile(name: string, content = "playwright e2e content") {
    await this.page.getByRole("button", { name: "上传文件" }).click();
    const dialog = this.page.locator(".el-dialog").filter({ hasText: "上传文件" });
    await expect(dialog).toBeVisible();
    await dialog.locator('input[type="file"]').setInputFiles({
      name,
      mimeType: "text/plain",
      buffer: Buffer.from(content, "utf-8"),
    });
    await dialog.getByRole("button", { name: "确定" }).click();
    await expect(this.page.getByText("上传成功")).toBeVisible();
    await this.expectFileVisible(name);
  }

  /** 新建目录 */
  async createDirectory(name: string) {
    await this.page.getByRole("button", { name: "新建文件夹" }).click();
    const dialog = this.page.locator(".el-dialog").filter({ hasText: "新建目录" });
    await expect(dialog).toBeVisible();
    await dialog.getByPlaceholder("请输入目录名称").fill(name);
    await dialog.getByRole("button", { name: "创建" }).click();
    await expect(this.page.getByText("目录创建成功")).toBeVisible();
    await this.expectFileVisible(name);
  }

  /** 进入目录（点击目录名） */
  async openDirectory(name: string) {
    await this.fileRow(name).locator(".file-link--dir").click();
    await expect(this.page.locator(".explorer-breadcrumb")).toContainText(name);
  }

  /** 返回根目录（点击面包屑根节点） */
  async goToRoot(rootLabel = "我的网盘") {
    await this.page.locator(".explorer-breadcrumb").getByText(rootLabel, { exact: true }).click();
  }

  /** 搜索文件（顶部搜索框，回车触发） */
  async search(keyword: string) {
    const box = this.page.getByPlaceholder("搜索文件...");
    await box.click();
    await box.fill(keyword);
    await box.press("Enter");
  }

  /** 下载文件（操作按钮[0]），断言出现「下载完成」通知 */
  async downloadFile(name: string) {
    await this.actions(name).nth(0).click();
    await expect(this.page.getByText("下载完成")).toBeVisible();
  }

  /** 删除文件（唯一的 danger 按钮），弹出确认框后确认 */
  async deleteFile(name: string) {
    await this.fileRow(name).locator("button.el-button--danger").click();
    const box = this.page.locator(".el-message-box");
    await expect(box).toBeVisible();
    await box.getByRole("button", { name: "删除" }).click();
    await expect(this.page.getByText("删除成功")).toBeVisible();
  }

  /** 移动文件到目标目录（操作按钮[3]，在弹窗目录树中选择目标目录后确定） */
  async moveFileToDirectory(name: string, targetDirName: string) {
    await this.actions(name).nth(3).click();
    const dialog = this.page.locator(".el-dialog").filter({ hasText: "移动文件" });
    await expect(dialog).toBeVisible();
    await dialog.getByText(targetDirName, { exact: true }).click();
    await dialog.getByRole("button", { name: "确定" }).click();
    await expect(this.page.getByText("移动成功")).toBeVisible();
  }

  /** 复制文件到目标目录（操作按钮[4]） */
  async copyFileToDirectory(name: string, targetDirName: string) {
    await this.actions(name).nth(4).click();
    const dialog = this.page.locator(".el-dialog").filter({ hasText: "复制文件" });
    await expect(dialog).toBeVisible();
    await dialog.getByText(targetDirName, { exact: true }).click();
    await dialog.getByRole("button", { name: "确定" }).click();
    await expect(this.page.getByText("复制成功")).toBeVisible();
  }

  /**
   * 创建分享（操作按钮[1]）并返回分享链接与（可选）提取码。
   * @param withCode true=提取码分享，false=公开分享
   */
  async createShare(
    name: string,
    withCode = false
  ): Promise<{ link: string; token: string; code: string | null }> {
    await this.actions(name).nth(1).click();
    // 弹窗标题在创建前后会从「分享文件/目录」变为「分享详情」，故以稳定的内部元素定位
    const dialog = this.page.locator(".el-dialog", {
      has: this.page.locator(".quick-share__file"),
    });
    await expect(dialog).toBeVisible();

    if (withCode) {
      await dialog.locator(".el-select").first().click();
      await this.page.getByRole("option", { name: "提取码" }).click();
    }
    await dialog.getByRole("button", { name: "分享" }).click();
    await expect(this.page.getByText("创建分享成功")).toBeVisible();

    const link = (await this.page.locator(".quick-share__link").innerText()).trim();
    const token = link
      .split("/s/")
      .pop()!
      .replace(/[/?#].*$/, "");
    let code: string | null = null;
    if (withCode) {
      const codeText = (await this.page.locator(".quick-share__code").innerText()).trim();
      code = codeText && codeText !== "-" ? codeText : null;
    }
    await dialog.getByRole("button", { name: "关闭" }).click();
    return { link, token, code };
  }

  /** 取消已存在的分享（再次点击分享按钮[1]，弹窗内点「取消分享」确认） */
  async cancelShare(name: string) {
    await this.actions(name).nth(1).click();
    const dialog = this.page.locator(".el-dialog", {
      has: this.page.locator(".quick-share__file"),
    });
    await expect(dialog).toBeVisible();
    await dialog.getByRole("button", { name: "取消分享" }).click();
    const box = this.page.locator(".el-message-box");
    await expect(box).toBeVisible();
    await box.getByRole("button", { name: "确定" }).click();
    await expect(this.page.getByText("取消分享成功")).toBeVisible();
  }

  // ---- 个人回收站（/files「回收站」标签，与团队复用同一 RecycleBinPanel）----

  private recycleRow(name: string): Locator {
    return this.page
      .locator(".panel-container .el-table")
      .getByRole("row")
      .filter({ hasText: name });
  }

  async restoreFromTrash(name: string) {
    await this.recycleRow(name).getByRole("button", { name: "还原" }).click();
    await expect(this.page.getByText("还原成功")).toBeVisible();
  }

  async purgeFromTrash(name: string) {
    await this.recycleRow(name).getByRole("button", { name: "彻底删除" }).click();
    const box = this.page.locator(".el-message-box");
    await expect(box).toBeVisible();
    await box.getByRole("button", { name: "删除" }).click();
    await expect(this.page.getByText("删除成功")).toBeVisible();
  }
}
