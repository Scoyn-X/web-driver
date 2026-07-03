import type { Directive, DirectiveBinding, WatchStopHandle } from "vue";
import { watch } from "vue";
import router from "@/router";
import { useTeamStore } from "@/store/team";

type PermissionElement = HTMLElement & {
  __hasPermStop?: WatchStopHandle;
  __hasRoleStop?: WatchStopHandle;
};

function currentTeamId(): number | null {
  const raw = router.currentRoute.value.params.teamId;
  if (!raw) return null;
  const id = Number(raw);
  return Number.isFinite(id) && id > 0 ? id : null;
}

function normalizeRequired(
  binding: DirectiveBinding,
  directive: string,
  label: string,
  example: string
): string | string[] {
  const required = binding.value as string | string[];
  if (!required || (typeof required !== "string" && !Array.isArray(required))) {
    throw new Error(`${directive} 需要${label}：${example}`);
  }
  return required;
}

function setVisible(el: HTMLElement, visible: boolean) {
  if (!el.dataset.originalDisplay) {
    el.dataset.originalDisplay = el.style.display || "";
  }
  el.style.display = visible ? el.dataset.originalDisplay : "none";
}

function hasAny(required: string | string[], values: string[]) {
  return Array.isArray(required)
    ? required.some((value) => values.includes(value))
    : values.includes(required);
}

function applyPermission(el: HTMLElement, binding: DirectiveBinding) {
  const required = normalizeRequired(
    binding,
    "v-hasPerm",
    "权限标识",
    `v-hasPerm="'role:update'" 或 v-hasPerm="['role:update','member:remove']"`
  );
  const teamId = currentTeamId();
  if (teamId === null) {
    setVisible(el, true);
    return;
  }

  const teamStore = useTeamStore();
  if (!Object.prototype.hasOwnProperty.call(teamStore.permsByTeamId, teamId)) {
    setVisible(el, true);
    return;
  }

  const perms = teamStore.permsOf(teamId);
  setVisible(el, perms.includes("*:*:*") || hasAny(required, perms));
}

function applyRole(el: HTMLElement, binding: DirectiveBinding) {
  const required = normalizeRequired(
    binding,
    "v-hasRole",
    "角色标识",
    `v-hasRole="'Owner'" 或 v-hasRole="['Owner','Admin']"`
  );
  const teamId = currentTeamId();
  if (teamId === null) {
    setVisible(el, true);
    return;
  }

  const teamStore = useTeamStore();
  const roles = teamStore.rolesOf(teamId);
  if (!roles.length) {
    setVisible(el, true);
    return;
  }

  setVisible(el, hasAny(required, roles));
}

export const hasPermDirective: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const teamId = currentTeamId();
    if (teamId === null) {
      applyPermission(el, binding);
      return;
    }
    const teamStore = useTeamStore();
    const updateVisibility = () => applyPermission(el, binding);
    (el as PermissionElement).__hasPermStop = watch(
      () => teamStore.permsByTeamId[teamId],
      updateVisibility,
      { immediate: true }
    );
  },
  updated(el: HTMLElement, binding: DirectiveBinding) {
    applyPermission(el, binding);
  },
  unmounted(el: HTMLElement) {
    const stop = (el as PermissionElement).__hasPermStop;
    if (typeof stop === "function") stop();
    delete (el as PermissionElement).__hasPermStop;
  },
};

export const hasRoleDirective: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const teamId = currentTeamId();
    if (teamId === null) {
      applyRole(el, binding);
      return;
    }
    const teamStore = useTeamStore();
    const updateVisibility = () => applyRole(el, binding);
    (el as PermissionElement).__hasRoleStop = watch(
      () => teamStore.rolesOf(teamId),
      updateVisibility,
      { immediate: true }
    );
  },
  updated(el: HTMLElement, binding: DirectiveBinding) {
    applyRole(el, binding);
  },
  unmounted(el: HTMLElement) {
    const stop = (el as PermissionElement).__hasRoleStop;
    if (typeof stop === "function") stop();
    delete (el as PermissionElement).__hasRoleStop;
  },
};
