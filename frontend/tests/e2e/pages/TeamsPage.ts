import { type Page, type Locator, expect } from "@playwright/test";

/**
 * 团队相关页面对象：团队列表、创建团队、进入团队详情、邀请成员、
 * 团队详情各标签页（文件 / 成员 / 邀请 / 回收站）、处理收到的邀请。
 */
export class TeamsPage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async gotoList() {
    await this.page.goto("/#/teams");
    await expect(this.page.getByRole("heading", { name: "我的团队" })).toBeVisible();
  }

  /** 创建团队 */
  async createTeam(name: string) {
    await this.page.getByRole("button", { name: "创建团队" }).click();
    const dialog = this.page.locator(".el-dialog").filter({ hasText: "创建团队" });
    await expect(dialog).toBeVisible();
    await dialog.getByPlaceholder("例：项目协作组").fill(name);
    await dialog.getByRole("button", { name: "创建" }).click();
    await expect(this.page.getByText("团队创建成功")).toBeVisible();
    await expect(this.page.getByText(name).first()).toBeVisible();
  }

  /** 进入团队详情（网格视图点击团队项） */
  async enterTeam(name: string) {
    await this.page.locator(".team-item").filter({ hasText: name }).first().click();
    await this.page.waitForURL("**/#/teams/*");
    await expect(this.page.locator(".team-detail__name")).toContainText(name);
  }

  /** 切换团队详情标签页（文件 / 成员 / 邀请 / 分享 / 回收站 / 设置） */
  async openTab(label: string) {
    await this.page.locator(".team-detail__tabbar").getByText(label, { exact: true }).click();
  }

  /** 在「邀请」标签页邀请一名已存在用户 */
  async invite(accountName: string, role: "Admin" | "Editor" | "Viewer" = "Editor") {
    await this.openTab("邀请");
    await this.page.getByRole("button", { name: "邀请新成员" }).click();
    const dialog = this.page.locator(".el-dialog").filter({ hasText: "邀请成员" });
    await expect(dialog).toBeVisible();

    // 远程搜索选择被邀请用户：点击 el-select 聚焦其 filterable 输入框后键入
    const userSelect = dialog.locator(".el-select").first();
    await userSelect.click();
    await this.page.keyboard.type(accountName);
    await this.page.getByRole("option", { name: new RegExp(`@${accountName}`) }).click();

    // 选择角色（第二个 el-select）
    const roleLabelMap = { Admin: "管理员", Editor: "编辑者", Viewer: "只读者" };
    if (role !== "Editor") {
      await dialog.locator(".el-select").nth(1).click();
      await this.page.getByRole("option", { name: roleLabelMap[role] }).click();
    }

    await dialog.getByRole("button", { name: "发送邀请" }).click();
    await expect(this.page.getByText("邀请已发送")).toBeVisible();
  }

  /** 进入「我的邀请」页并接受指定团队的邀请 */
  async acceptInvitationFor(teamName: string) {
    await this.page.goto("/#/invitations");
    await expect(this.page.getByRole("heading", { name: "我的邀请" })).toBeVisible();
    const row = this.page.getByRole("row").filter({ hasText: teamName });
    await expect(row).toBeVisible();
    await row.getByRole("button", { name: "接受" }).click();
    const box = this.page.locator(".el-message-box");
    await expect(box).toBeVisible();
    await box.getByRole("button", { name: "接受" }).click();
    await expect(this.page.getByText("已加入团队")).toBeVisible();
  }

  // ---- 团队回收站（详情页「回收站」标签）----

  recycleRow(name: string): Locator {
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
