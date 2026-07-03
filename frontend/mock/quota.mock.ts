/**
 * 个人配额 mock，对应 `src/api/quota.api.ts`。
 *
 * 已用空间由 `data/file.state.ts` 中的真实文件大小汇总得出，
 * 因此上传 / 删除文件后再查配额会同步变化。
 */
import { defineMock } from "vite-plugin-mock-dev-server";
import { ok } from "./shared";
import { quotaInfo } from "./data/file.state";

export default defineMock([
  /** 获取当前用户配额 */
  {
    url: "/dev-api/api/v1/quota",
    method: "GET",
    body: () => ok(quotaInfo()),
  },
]);
