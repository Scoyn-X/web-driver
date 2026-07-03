import { defineStore } from "pinia";

interface TeamState {
  teams: any[];
  teamsLoaded: boolean;
  detailsByTeamId: Record<number, any>;
  permsByTeamId: Record<number, string[]>;
}

function toFiniteNumber(value: unknown) {
  if (value === null || value === undefined || value === "") return value;
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? numericValue : value;
}

function normalizeQuota(quota: any) {
  if (!quota) return quota;
  return {
    ...quota,
    totalQuota: toFiniteNumber(quota.totalQuota),
    usedSpace: toFiniteNumber(quota.usedSpace),
    remainingSpace: toFiniteNumber(quota.remainingSpace),
  };
}

function normalizeTeam(team: any) {
  if (!team) return team;
  return {
    ...team,
    id: toFiniteNumber(team.id),
    ownerId: toFiniteNumber(team.ownerId),
    ownerAccountId: toFiniteNumber(team.ownerAccountId),
    totalQuota: toFiniteNumber(team.totalQuota),
    usedSpace: toFiniteNumber(team.usedSpace),
    remainingSpace: toFiniteNumber(team.remainingSpace),
    quota: normalizeQuota(team.quota),
  };
}

export const useTeamStore = defineStore("team", {
  state: (): TeamState => ({
    teams: [],
    teamsLoaded: false,
    detailsByTeamId: {},
    permsByTeamId: {},
  }),

  getters: {
    rolesOf:
      (state) =>
      (teamId: number): string[] => {
        const detail = state.detailsByTeamId[teamId];
        const team = state.teams.find((t) => t.id === teamId);
        const myRole = detail?.myRole ?? detail?.role ?? team?.myRole ?? team?.role;
        return myRole ? [myRole] : [];
      },

    permsOf:
      (state) =>
      (teamId: number): string[] =>
        state.permsByTeamId[teamId] ?? [],
  },

  actions: {
    setTeams(teams: any[]) {
      this.teams = teams.map(normalizeTeam);
      this.teamsLoaded = true;
      this.teams.forEach((t) => {
        if (t.id && !this.detailsByTeamId[t.id]) {
          this.detailsByTeamId[t.id] = t;
        }
      });
    },

    setDetail(teamId: number, detail: any) {
      if (!teamId) return;
      this.detailsByTeamId[teamId] = normalizeTeam(detail);
    },

    setPerms(teamId: number, perms: string[]) {
      if (!teamId) return;
      this.permsByTeamId[teamId] = [...perms];
    },

    clearTeam(teamId: number) {
      if (!teamId) return;
      delete this.detailsByTeamId[teamId];
      delete this.permsByTeamId[teamId];
    },

    reset() {
      this.teams = [];
      this.teamsLoaded = false;
      this.detailsByTeamId = {};
      this.permsByTeamId = {};
    },
  },
});
