<template>
  <div
    class="resize-handle"
    :class="[`resize-handle--${direction}`, { 'is-active': dragging }]"
    @mousedown.prevent="onMouseDown"
  />
</template>

<script setup lang="ts">
  const props = withDefaults(
    defineProps<{
      width: number;
      min?: number;
      max?: number;
      direction?: "right" | "left";
    }>(),
    {
      min: 160,
      max: 400,
      direction: "right",
    }
  );

  const emit = defineEmits<{
    "update:width": [value: number];
    "update:dragging": [value: boolean];
  }>();

  let startX = 0;
  let startWidth = 0;
  let dragging = false;

  function setDragging(value: boolean) {
    dragging = value;
    emit("update:dragging", value);
    if (value) {
      document.body.classList.add("is-resizing");
    } else {
      document.body.classList.remove("is-resizing");
    }
  }

  function onMouseDown(e: MouseEvent) {
    startX = e.clientX;
    startWidth = props.width;
    setDragging(true);
    window.addEventListener("mousemove", onMouseMove);
    window.addEventListener("mouseup", onMouseUp);
  }

  function onMouseMove(e: MouseEvent) {
    const delta = e.clientX - startX;
    const sign = props.direction === "right" ? 1 : -1;
    let next = startWidth + sign * delta;
    if (next < props.min) next = props.min;
    if (next > props.max) next = props.max;
    emit("update:width", next);
  }

  function onMouseUp() {
    if (!dragging) return;
    setDragging(false);
    window.removeEventListener("mousemove", onMouseMove);
    window.removeEventListener("mouseup", onMouseUp);
  }

  onBeforeUnmount(onMouseUp);
</script>

<style scoped>
  .resize-handle {
    position: absolute;
    top: 0;
    bottom: 0;
    z-index: 10;
    width: 6px;
    cursor: col-resize;
    background: transparent;
    transition: background-color 0.15s;
  }

  .resize-handle--right {
    right: -3px;
  }

  .resize-handle--left {
    left: -3px;
  }

  .resize-handle:hover,
  .resize-handle.is-active {
    background: var(--el-color-primary);
    opacity: 0.5;
  }
</style>

<style>
  body.is-resizing,
  body.is-resizing * {
    cursor: col-resize !important;
    user-select: none !important;
  }
</style>
