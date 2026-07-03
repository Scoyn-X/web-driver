/**
 * 访问计数器 mock，对应 `src/api/counter.api.ts`（接口路径为 /visits）。
 */
import { defineMock } from "vite-plugin-mock-dev-server";
import { ok } from "./shared";

/** 进程内访问计数，dev server 重启即重置 */
let visits = 42;

export default defineMock([
  /** 获取访问计数 */
  {
    url: "/dev-api/api/v1/visits",
    method: "GET",
    body: () => ok(visits),
  },

  /** 访问计数 +1 */
  {
    url: "/dev-api/api/v1/visits",
    method: "POST",
    body: () => ok((visits += 1)),
  },

  /** 重置访问计数 */
  {
    url: "/dev-api/api/v1/visits",
    method: "DELETE",
    body: () => {
      visits = 0;
      return ok(null);
    },
  },
]);
