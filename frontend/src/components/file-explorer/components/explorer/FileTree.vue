<template>
  <el-tree
    ref="treeRef"
    :data="rootNodes"
    node-key="key"
    :props="treeProps"
    lazy
    :load="loadNode"
    highlight-current
    stored-expand
    :current-node-key="currentNodeKey"
    @node-click="handleNodeClick"
  >
    <template #default="{ data }">
      <div class="tree-node">
        <FileIcon
          type="folder"
          :size="16"
        />
        <span>{{ data.label }}</span>
      </div>
    </template>
  </el-tree>
</template>

<script setup lang="ts">
  import DirectoryAPI from "@/api/directory.api";
  import TeamAPI from "@/api/team.api";
  import { PersonalAPI } from "@/api/personal.api";
  import { normalizeDirectoryId } from "../../utils/file-display";

  interface TreeNode {
    key: number;
    label: string;
    parentId: number;
    isLeaf: boolean;
  }

  const props = withDefaults(
    defineProps<{
      activeDirectoryId?: number;
      activeTrailIds?: number[];
      scope?: "personal" | "team" | "private";
      teamId?: number;
    }>(),
    { scope: "personal" }
  );

  const emit = defineEmits<{
    (e: "change-directory", directoryId: number): void;
  }>();

  const treeProps = {
    label: "label",
    children: "children",
    isLeaf: "isLeaf",
  } as const;

  const treeRef = ref<any>(null);

  const rootNodes = computed<TreeNode[]>(() => [
    {
      key: 0,
      label:
        props.scope === "team" ? "团队文件" : props.scope === "private" ? "私密空间" : "我的网盘",
      parentId: 0,
      isLeaf: false,
    },
  ]);

  const currentNodeKey = computed(() => props.activeDirectoryId ?? 0);

  watch(
    () => [props.activeDirectoryId, props.activeTrailIds],
    async () => {
      await syncTreeExpandedState();
    },
    { immediate: true, deep: true }
  );

  function handleNodeClick(node: TreeNode) {
    emit("change-directory", normalizeDirectoryId(node.key));
  }

  function loadNode(node: any, resolve: (data: TreeNode[]) => void) {
    if (node.level === 0) {
      resolve(rootNodes.value);
      return;
    }

    const parentId = normalizeDirectoryId(node.data?.key);
    const fetcher =
      props.scope === "private"
        ? PersonalAPI.listPrivateDirectories(parentId)
        : props.scope === "team" && props.teamId
          ? TeamAPI.listDirectories(props.teamId, parentId)
          : DirectoryAPI.listChildDirectories(parentId);

    fetcher
      .then((children: any) => {
        const nodes = (Array.isArray(children) ? children : [])
          .map((item: any) => toTreeNode(item))
          .sort((a: any, b: any) => a.label.localeCompare(b.label, "zh-CN"));
        resolve(nodes);
      })
      .catch(() => {
        resolve([]);
      });
  }

  async function syncTreeExpandedState() {
    await nextTick();

    const tree = treeRef.value;
    if (!tree) return;

    const currentId = normalizeDirectoryId(props.activeDirectoryId);
    const path = [0, ...(props.activeTrailIds || []).map((id) => normalizeDirectoryId(id))].filter(
      (id, index, arr) => arr.indexOf(id) === index
    );

    for (const id of path) {
      await expandNodeById(id);
    }

    tree.setCurrentKey(currentId);
  }

  function expandNodeById(id: number) {
    return new Promise<void>((resolve) => {
      const tree = treeRef.value;
      if (!tree) {
        resolve();
        return;
      }

      const node = tree.getNode(id);
      if (!node || node.expanded || node.isLeaf) {
        resolve();
        return;
      }

      let done = false;
      const finish = () => {
        if (done) return;
        done = true;
        resolve();
      };

      try {
        node.expand(finish);
        setTimeout(finish, 300);
      } catch {
        finish();
      }
    });
  }

  function toTreeNode(node: any): TreeNode {
    return {
      key: normalizeDirectoryId(node.id),
      label: node.name,
      parentId: normalizeDirectoryId(node.parentId ?? node.directoryId ?? 0),
      isLeaf: !node.hasChildren,
    };
  }
</script>

<style scoped>
  .tree-node {
    display: inline-flex;
    gap: 6px;
    align-items: center;
    min-width: max-content;
  }

  :deep(.el-tree) {
    width: max-content;
    min-width: max-content;
  }

  :deep(.el-tree-node__content) {
    height: 32px;
  }
</style>
