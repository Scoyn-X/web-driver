import type { App } from "vue";
import { hasPermDirective, hasRoleDirective } from "./permission";

export function setupDirectives(app: App<Element>) {
  app.directive("hasPerm", hasPermDirective);
  app.directive("hasRole", hasRoleDirective);
}
