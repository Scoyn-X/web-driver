<template>
  <div class="bpmn-viewer-shell w-full">
    <div
      v-if="renderError"
      class="bpmn-viewer-empty"
    >
      <el-empty :description="renderError" />
    </div>
    <div
      v-else
      class="bpmn-viewer-layout"
    >
      <div
        ref="containerRef"
        class="bpmn-viewer"
      />

      <aside
        v-if="hasTimeline"
        class="bpmn-viewer-timeline"
      >
        <div class="bpmn-viewer-timeline__header">
          <h3 class="bpmn-viewer-timeline__title">
            {{ timelineTitle }}
          </h3>
        </div>

        <el-empty
          v-if="!normalizedNodes.length"
          description="暂无节点详情"
          :image-size="88"
        />

        <el-scrollbar
          v-else
          class="bpmn-viewer-timeline__scroll"
        >
          <el-timeline>
            <el-timeline-item
              v-for="(node, index) in normalizedNodes"
              :key="node.nodeId || `${node.nodeName || 'node'}-${index}`"
              :type="
                node.completed ? 'success' : node.startTime || node.endTime ? 'primary' : 'info'
              "
              :hollow="!node.completed"
              :timestamp="node.timestampText"
              placement="top"
            >
              <div class="bpmn-viewer-timeline__card">
                <div class="bpmn-viewer-timeline__card-head">
                  <div class="bpmn-viewer-timeline__card-title">
                    {{ node.nodeName || node.nodeId || `节点 ${index + 1}` }}
                  </div>
                  <div class="bpmn-viewer-timeline__card-tags">
                    <el-tag
                      size="small"
                      :type="
                        node.completed
                          ? 'success'
                          : node.startTime || node.endTime
                            ? 'primary'
                            : 'info'
                      "
                    >
                      {{ node.statusText }}
                    </el-tag>
                  </div>
                </div>

                <dl class="bpmn-viewer-timeline__meta">
                  <div>
                    <dt>执行人</dt>
                    <dd>{{ node.assigneeText }}</dd>
                  </div>
                  <div>
                    <dt>开始时间</dt>
                    <dd>{{ node.startTimeText }}</dd>
                  </div>
                  <div>
                    <dt>结束时间</dt>
                    <dd>{{ node.endTimeText }}</dd>
                  </div>
                  <div>
                    <dt>耗时</dt>
                    <dd>{{ node.durationText }}</dd>
                  </div>
                </dl>
              </div>
            </el-timeline-item>
          </el-timeline>
        </el-scrollbar>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
  import BpmnViewer from "bpmn-js";
  import "bpmn-js/dist/assets/diagram-js.css";
  import "bpmn-js/dist/assets/bpmn-js.css";
  import { formatTimestamp } from "@/utils/format-time";

  type TimelineNode = {
    nodeId?: string;
    nodeName?: string;
    nodeType?: string;
    assignee?: string;
    username?: string;
    accountName?: string;
    startTime?: string;
    endTime?: string;
    durationMillis?: number;
    completed?: boolean;
  };

  type NormalizedTimelineNode = TimelineNode & {
    assigneeText: string;
    startTimeText: string;
    endTimeText: string;
    durationText: string;
    statusText: string;
    timestampText: string;
  };

  defineOptions({
    name: "BpmnViewer",
    inheritAttrs: false,
  });

  const props = withDefaults(
    defineProps<{
      xml: string;
      activeNodeIds?: string[];
      completedNodeIds?: string[];
      nodes?: TimelineNode[];
      timelineTitle?: string;
      timelineWidth?: number;
      height?: number;
    }>(),
    {
      height: 200,
      timelineTitle: "节点详情",
      timelineWidth: 360,
    }
  );

  const containerRef = ref();
  let viewer: BpmnViewer | null = null;
  const renderError = ref("");

  const hasTimeline = computed(() => (props.nodes || []).length > 0);
  const normalizedNodes = computed<NormalizedTimelineNode[]>(() =>
    (props.nodes || []).map((node) => {
      const fmtTime = (value?: string) => (value ? formatTimestamp(String(value)) : "-");
      const fmtDuration = (value?: number) => {
        if (value == null) return "-";
        if (value < 1000) return `${value} ms`;

        const seconds = value / 1000;
        if (seconds < 60) return `${seconds.toFixed(seconds < 10 ? 1 : 0)} s`;

        const minutes = Math.floor(seconds / 60);
        const remainSeconds = Math.round(seconds % 60);
        return `${minutes} 分 ${remainSeconds} 秒`;
      };

      return {
        ...node,
        assigneeText: node.accountName || node.username || node.assignee || "-",
        startTimeText: fmtTime(node.startTime),
        endTimeText: fmtTime(node.endTime),
        durationText: fmtDuration(node.durationMillis),
        statusText: node.completed
          ? "已完成"
          : node.startTime || node.endTime
            ? "进行中"
            : "待处理",
        timestampText:
          node.startTime && node.endTime
            ? `${fmtTime(node.startTime)} - ${fmtTime(node.endTime)}`
            : fmtTime(node.startTime || node.endTime),
      };
    })
  );

  async function render() {
    renderError.value = "";

    if (!containerRef.value || !props.xml) {
      renderError.value = "无法解析";
      return;
    }

    if (viewer) {
      viewer.destroy();
      viewer = null;
    }

    await nextTick();
    viewer = new BpmnViewer({ container: containerRef.value });
    try {
      await viewer.importXML(props.xml);
      const canvas = viewer.get("canvas") as any;
      const elementRegistry = viewer.get("elementRegistry") as any;
      const rootElement = canvas.getRootElement();

      if (!rootElement?.children?.length) {
        throw new Error("diagram has no renderable elements");
      }

      props.completedNodeIds?.forEach((elementId) => {
        if (elementRegistry.get(elementId)) {
          canvas.addMarker(elementId, "bpmn-node-completed");
        }
      });

      props.activeNodeIds?.forEach((elementId) => {
        if (elementRegistry.get(elementId)) {
          canvas.addMarker(elementId, "bpmn-node-active");
        }
      });

      canvas.zoom("fit-viewport");
    } catch (err) {
      console.error("BPMN render failed:", err);
      renderError.value = "无法解析";
      viewer?.destroy();
      viewer = null;
    }
  }

  watch(() => [props.xml, props.activeNodeIds, props.completedNodeIds], render, { deep: true });

  onMounted(() => {
    nextTick(render);
  });

  onBeforeUnmount(() => {
    viewer?.destroy();
    viewer = null;
  });
</script>

<style scoped>
  .bpmn-viewer-shell {
    width: 100%;
  }

  .bpmn-viewer-layout {
    display: flex;
    align-items: stretch;
    width: 100%;
    height: v-bind("props.height + 'px'");
    max-height: v-bind("props.height + 'px'");
    overflow: hidden;
  }

  .bpmn-viewer {
    flex: 1 1 auto;
    min-width: 0;
    height: 100%;
    background: var(--el-fill-color-blank);
  }

  .bpmn-viewer-timeline {
    display: flex;
    flex: 0 0 v-bind("props.timelineWidth + 'px'");
    flex-direction: column;
    min-width: 0;
    height: 100%;
    overflow: hidden;
  }

  .bpmn-viewer-timeline__header {
    flex-shrink: 0;
    margin-bottom: 12px;
  }

  .bpmn-viewer-timeline__title {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  .bpmn-viewer-timeline__scroll {
    flex: 1;
    min-height: 0;
  }

  .bpmn-viewer-timeline__card {
    padding: 12px 12px 10px;
    background: var(--el-fill-color-blank);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 10px;
  }

  .bpmn-viewer-timeline__card-head {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: flex-start;
    justify-content: space-between;
    margin-bottom: 10px;
  }

  .bpmn-viewer-timeline__card-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  .bpmn-viewer-timeline__card-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    justify-content: flex-end;
  }

  .bpmn-viewer-timeline__meta {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px 12px;
    margin: 0;
  }

  .bpmn-viewer-timeline__meta div {
    min-width: 0;
  }

  .bpmn-viewer-timeline__meta dt {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .bpmn-viewer-timeline__meta dd {
    margin: 4px 0 0;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 13px;
    color: var(--el-text-color-primary);
    white-space: nowrap;
  }

  .bpmn-viewer-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    min-height: v-bind("props.height + 'px'");
    background: var(--el-fill-color-blank);
  }

  :deep(.bjs-powered-by) {
    display: none !important;
  }

  :deep(.bpmn-node-completed .djs-visual > :first-child) {
    fill: var(--el-color-success-light-9) !important;
    stroke: var(--el-color-success) !important;
  }

  :deep(.bpmn-node-completed .djs-visual > text) {
    fill: var(--el-color-success-dark-2) !important;
  }

  :deep(.bpmn-node-active .djs-visual > :first-child) {
    fill: var(--el-color-primary-light-9) !important;
    stroke: var(--el-color-primary) !important;
  }

  :deep(.bpmn-node-active .djs-visual > text) {
    fill: var(--el-color-primary-dark-2) !important;
  }

  /* Remove default left indent of Element Plus timeline used in the viewer */
  .bpmn-viewer-timeline :deep(.el-timeline) {
    padding-left: 0;
  }

  .bpmn-viewer-timeline :deep(.el-timeline__item),
  .bpmn-viewer-timeline :deep(.el-timeline-item) {
    margin-left: 4px;
  }
</style>
