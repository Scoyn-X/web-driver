<template>
  <div class="panel-container">
    <el-table
      v-loading="loading"
      :data="shares"
    >
      <template #empty>
        <el-empty description="暂无分享" />
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
        prop="fileName"
        label="文件名"
        min-width="180"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <div class="flex gap-2 items-center">
            <FileIcon
              :type="resolveShareFileType(row)"
              :size="20"
            />
            <span class="file-link">{{ row.fileName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="文件类型"
        width="100"
        align="center"
        :formatter="(row: any) => formatShareFileType(row)"
      />
      <el-table-column
        label="分享方式"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          {{ row.accessType === 1 ? "提取码" : "公开" }}
        </template>
      </el-table-column>
      <el-table-column
        label="提取码"
        width="110"
        align="center"
        :formatter="(row: any) => shareExtractCode(row)"
      />
      <el-table-column
        label="状态"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            :type="shareStatusTagType(row)"
            size="small"
          >
            {{ shareStatusLabel(row) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="创建时间"
        width="160"
        align="center"
        :formatter="(row: any) => fmtTime(row.createTime)"
      />
      <el-table-column
        label="操作"
        width="200"
        align="center"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            icon="Link"
            @click="copyLink(row)"
          >
            复制链接
          </el-button>
          <el-button
            v-hasPerm="['share:manage']"
            link
            type="danger"
            icon="Delete"
            @click="cancel(row.id)"
          >
            取消分享
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
  import TeamAPI from "@/api/team.api";
  import { formatTimestamp } from "@/utils/format-time";
  import {
    buildShareLink,
    formatShareFileType,
    resolveShareFileType,
    shareExtractCode,
    shareStatusLabel,
    shareStatusTagType,
  } from "@/components/file-explorer/utils/share-display";

  defineOptions({
    name: "TeamShares",
    inheritAttrs: false,
  });

  const props = defineProps<{
    teamId: number;
  }>();

  const emit = defineEmits<{
    (e: "changed"): void;
  }>();

  const shares = ref<any[]>();
  const loading = ref(false);
  const selectedIds = ref<number[]>([]);

  const allSelected = computed(
    () => shares.value?.length > 0 && selectedIds.value.length === shares.value.length
  );
  const selectionIndeterminate = computed(
    () => selectedIds.value.length > 0 && selectedIds.value.length < (shares.value?.length ?? 0)
  );

  function fmtTime(value: string) {
    return value ? formatTimestamp(value) : "-";
  }

  async function fetchShares() {
    if (!props.teamId) return;
    loading.value = true;
    try {
      shares.value = await TeamAPI.listTeamShares(props.teamId);
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
    selectedIds.value = checked ? (shares.value?.map((item) => item.id) ?? []) : [];
  }

  async function copyLink(share: any) {
    const link = buildShareLink(share.shareToken);
    try {
      await navigator.clipboard.writeText(link);
      ElMessage.success("链接已复制");
    } catch {
      ElMessage.warning("复制失败，请手动复制");
    }
  }

  // async function copyCode(share: any) {
  //   const code = shareExtractCode(share);
  //   if (code === "-") {
  //     ElMessage.info("该分享未设置提取码");
  //     return;
  //   }
  //   try {
  //     await navigator.clipboard.writeText(code);
  //     ElMessage.success("提取码已复制");
  //   } catch {
  //     ElMessage.warning("复制失败，请手动复制");
  //   }
  // }

  async function cancel(shareId: number) {
    try {
      await ElMessageBox.confirm("确认取消该分享？", "提示", {
        type: "warning",
        confirmButtonText: "取消分享",
        cancelButtonText: "返回",
      });
      await TeamAPI.cancelTeamShare(props.teamId, shareId);
      ElMessage.success("分享已取消");
      await fetchShares();
      emit("changed");
    } catch (error: any) {
      if (error === "cancel" || error === "close") return;
      ElMessage.error("取消失败");
    }
  }

  watch(() => props.teamId, fetchShares);

  defineExpose({ refresh: fetchShares });
</script>

<style scoped>
  .panel-container {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-height: 0;
    padding: 4px 12px;
  }
</style>
