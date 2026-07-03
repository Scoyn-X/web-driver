import { defineMock } from "vite-plugin-mock-dev-server";
import { ok } from "./shared";

const PERMISSIONS = [
  "team:manage",
  "team:dissolve",
  "owner:transfer",
  "member:invite",
  "member:remove",
  "role:update",
  "file:list",
  "file:detail",
  "file:download",
  "file:upload",
  "file:move",
  "file:copy",
  "file:delete",
  "share:create",
  "share:manage",
  "trash:list",
  "trash:restore",
  "trash:delete",
  "file:transfer:to-personal",
  "file:transfer:to-team",
];

export default defineMock([
  {
    url: "/dev-api/api/v1/permissions",
    method: "GET",
    body: () => ok(PERMISSIONS),
  },
]);
