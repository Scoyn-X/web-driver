package com.jiayuan.boot.system.oss.controller;

import com.jiayuan.boot.system.oss.model.vo.RecycleBinItemResponseVO;
import com.jiayuan.boot.system.oss.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link RecycleBinController} 控制层测试（Bonus 4.3）。
 *
 * @author charleslam
 * @since 2026/04/14
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RecycleBinController 控制层测试")
class RecycleBinControllerTest {

    @Mock
    private FileService fileService;

    @InjectMocks
    private RecycleBinController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/recycle-bin 返回回收站列表（含人类可读 path）")
    void list_returnsFileList() throws Exception {
        RecycleBinItemResponseVO item = new RecycleBinItemResponseVO();
        item.setId(1L);
        item.setOriginalName("recycled.txt");
        item.setPath("/A/B/recycled.txt");
        item.setFileSize(123L);
        item.setIsDirectory(0);
        when(fileService.listRecycleBin()).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/recycle-bin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].originalName").value("recycled.txt"))
                .andExpect(jsonPath("$.data[0].path").value("/A/B/recycled.txt"))
                .andExpect(jsonPath("$.data[0].fileSize").value(123))
                // 瘦身字段不应出现
                .andExpect(jsonPath("$.data[0].fileUrl").doesNotExist())
                .andExpect(jsonPath("$.data[0].mimeType").doesNotExist())
                .andExpect(jsonPath("$.data[0].parentId").doesNotExist())
                .andExpect(jsonPath("$.data[0].fullPath").doesNotExist());
    }

    @Test
    @DisplayName("POST /{id}/restore 转发 id 给 service")
    void restore_passesId() throws Exception {
        mockMvc.perform(post("/api/v1/recycle-bin/{id}/restore", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"));

        verify(fileService).restoreFromRecycleBin(eq(42L));
    }

    @Test
    @DisplayName("DELETE /{id} 调用 permanentlyDelete")
    void permanentDelete_callsService() throws Exception {
        mockMvc.perform(delete("/api/v1/recycle-bin/{id}", 99L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"));

        verify(fileService).permanentlyDelete(eq(99L));
    }
}
