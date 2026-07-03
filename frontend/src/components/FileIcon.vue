<template>
  <img
    :src="getIcon(type)"
    :alt="type"
    :class="'inline-block align-middle'"
    :style="{ width: size + 'px', height: size + 'px' }"
  />
</template>

<script setup lang="ts">
  interface FileIconProps {
    type?: string;
    size?: number;
  }
  const { type = "file", size = 24 } = defineProps<FileIconProps>();

  const extMap: Record<string, string> = {
    folder: "folder",
    pdf: "pdf",
    ppt: "ppt",
    pptx: "ppt",
    doc: "office-doc",
    docx: "office-doc",
    xls: "office-els",
    xlsx: "office-els",
    txt: "txt",
    zip: "zip",
    rar: "rar",
    mp3: "mp3",
    mp4: "mp4",
    avi: "video",
    mov: "video",
    image: "image",
    svg: "image-pic",
    png: "image-png",
    jpg: "image-jpeg",
    jpeg: "image-jpeg",
    gif: "image-gif",
    apk: "apk",
    exe: "exe",
    code: "code",
    css: "css",
    js: "js",
    untitled: "untitled",
  };

  const iconModules = import.meta.glob("/src/assets/icons/file/*.svg", {
    eager: true,
    import: "default",
  }) as Record<string, string>;

  const iconMap = Object.fromEntries(
    Object.entries(iconModules).map(([path, url]) => {
      const fileName = path.split("/").pop()?.replace(".svg", "") || "file";
      return [fileName, url];
    })
  );

  function getIcon(t: string = "file") {
    if (!t) return iconMap.file;
    if (t === "folder") return iconMap.folder;
    const ext = t.toLowerCase();
    const name = extMap[ext] ?? "file";
    return iconMap[name] ?? iconMap.file;
  }
</script>
