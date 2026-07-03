<template>
  <div class="panel-container">
    <el-table
      v-loading="loading"
      :data="items"
      height="100%"
    >
      <template #empty>
        <el-empty description="暂无文件" />
      </template>
      <el-table-column
        width="48"
        align="center"
      >
        <template #header>
          <el-checkbox
            :model-value="allSelected"
            :indeterminate="selectionIndeterminate"
            @change="toggleAll"
          />
        </template>
        <template #default="{ row }">
          <el-checkbox
            :model-value="isSelected(row.id)"
            @click.stop
            @change="(checked) => toggleRow(row.id, checked)"
          />
        </template>
      </el-table-column>
      <el-table-column
        prop="originalName"
        label="文件名"
        min-width="220"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <div class="flex gap-2 items-center">
            <FileIcon
              :type="resolveFileType(row)"
              :size="20"
            />
            <span class="file-link">{{ row.originalName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="类型"
        width="100"
        align="center"
        :formatter="fmtType"
      />
      <el-table-column
        prop="fileSize"
        label="大小"
        width="120"
        align="center"
        :formatter="fmtSize"
      />
      <el-table-column
        v-if="scope === 'team'"
        label="删除者"
        width="120"
        align="center"
      >
        <template #default="{ row }">
          {{ row.deletedByName || "-" }}
        </template>
      </el-table-column>
      <el-table-column
        prop="path"
        label="原路径"
        min-width="220"
        show-overflow-tooltip
      />
      <el-table-column
        prop="deletedAt"
        label="删除时间"
        min-width="160"
        align="center"
        :formatter="fmtTime"
      />
      <el-table-column
        prop="expireAt"
        label="到期时间"
        min-width="160"
        align="center"
        :formatter="fmtTime"
      />
      <el-table-column
        label="操作"
        fixed="right"
        width="200"
        align="center"
      >
        <template #default="{ row }">
          <div class="operation-cell">
            <el-button
              link
              type="primary"
              icon="Refresh"
              @click="restore(row.id)"
            >
              还原
            </el-button>
            <el-button
              link
              type="danger"
              icon="Delete"
              @click="removeForever(row.id)"
            >
              彻底删除
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
  import RecycleBinAPI from "@/api/recycle-bin.api";
  import TeamAPI from "@/api/team.api";
  import { PersonalAPI } from "@/api/personal.api";
  import { formatTimestamp } from "@/utils/format-time";
  import { formatFileSize, formatFileType, resolveFileType } from "../../utils/file-display";

  defineOptions({
    name: "RecycleBin",
    inheritAttrs: false,
  });

  const props = withDefaults(
    defineProps<{
      scope?: "personal" | "team" | "private";
      teamId?: number;
    }>(),
    { scope: "personal" }
  );

  const emit = defineEmits<{
    (e: "changed"): void;
  }>();

  const loading = ref(false);
  const items = ref<any[]>();
  const selectedIds = ref<number[]>([]);

  const allSelected = computed(
    () => items.value?.length > 0 && selectedIds.value.length === items.value.length
  );
  const selectionIndeterminate = computed(
    () => selectedIds.value.length > 0 && selectedIds.value.length < (items.value?.length ?? 0)
  );

  async function fetchRecycle() {
    if (props.scope === "team" && !props.teamId) return;
    loading.value = true;
    try {
      if (props.scope === "private") {
        items.value = await PersonalAPI.listPrivateTrash();
      } else if (props.scope === "team") {
        items.value = await TeamAPI.listTrash(props.teamId!);
      } else {
        items.value = await RecycleBinAPI.list();
      }
      selectedIds.value = [];
    } finally {
      loading.value = false;
    }
  }

  function isSelected(id: number) {
    return selectedIds.value.includes(id);
  }

  function toggleRow(id: number, checked: boolean) {
    selectedIds.value = checked
      ? Array.from(new Set([...selectedIds.value, id]))
      : selectedIds.value.filter((item) => item !== id);
  }

  function toggleAll(checked: boolean) {
    selectedIds.value = checked ? (items.value?.map((item) => item.id) ?? []) : [];
  }

  function fmtTime(_row: any, _col: unknown, value: string) {
    return value ? formatTimestamp(value) : "-";
  }

  function fmtType(row: any) {
    return formatFileType(row);
  }

  function fmtSize(row: any, _col: unknown, cellValue: number) {
    return formatFileSize(row, cellValue, "-");
  }

  async function restore(id: number) {
    try {
      if (props.scope === "private") {
        await PersonalAPI.restorePrivateTrash(id);
      } else if (props.scope === "team") {
        await TeamAPI.restoreTrash(props.teamId!, id);
      } else {
        await RecycleBinAPI.restore(id);
      }
      ElMessage.success("还原成功");
      await fetchRecycle();
      emit("changed");
    } catch {
      ElMessage.error("还原失败");
    }
  }

  async function removeForever(id: number) {
    try {
      await ElMessageBox.confirm("该文件将被永久删除，是否继续？", "提示", {
        type: "warning",
        confirmButtonText: "删除",
        cancelButtonText: "取消",
      });
      if (props.scope === "private") {
        await PersonalAPI.permanentlyDeletePrivateTrash(id);
      } else if (props.scope === "team") {
        await TeamAPI.permanentlyDeleteTrash(props.teamId!, id);
      } else {
        await RecycleBinAPI.permanentDelete(id);
      }
      ElMessage.success("删除成功");
      await fetchRecycle();
      emit("changed");
    } catch (error) {
      if (error === "cancel" || error === "close") return;
      ElMessage.error("删除失败");
    }
  }

  defineExpose({ fetchRecycle });
</script>

<style scoped>
  .panel-container {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-height: 0;
    padding: 4px 12px;
  }

  .operation-cell {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    align-items: center;
    justify-content: center;
  }
</style>
