import { defineMock } from "vite-plugin-mock-dev-server";
import { ok } from "./shared";

const ROLES = [
  { value: "Owner", label: "拥有者", authority: 3, assignable: false },
  { value: "Admin", label: "管理员", authority: 2, assignable: true },
  { value: "Editor", label: "编辑者", authority: 1, assignable: true },
  { value: "Viewer", label: "只读者", authority: 0, assignable: true },
];

export default defineMock([
  {
    url: "/dev-api/api/v1/roles",
    method: "GET",
    body: () => ok(ROLES),
  },
]);
