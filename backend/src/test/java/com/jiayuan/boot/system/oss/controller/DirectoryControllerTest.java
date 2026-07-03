package com.jiayuan.boot.system.oss.controller;

import com.jiayuan.boot.system.oss.model.vo.DirectoryNodeResponseVO;
import com.jiayuan.boot.system.oss.service.DirectoryService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link DirectoryController} 控制层测试（仅覆盖目录树懒加载 GET 路由）。
 *
 * @author charleslam
 * @since 2026/04/16
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DirectoryController 控制层测试")
class DirectoryControllerTest {

    @Mock
    private DirectoryService directoryService;

    @InjectMocks
    private DirectoryController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/directories?parentId=X 返回子目录列表")
    void listChildren_returnsList() throws Exception {
        DirectoryNodeResponseVO node = new DirectoryNodeResponseVO();
        node.setId(10L);
        node.setName("A");
        node.setParentId(0L);
        node.setHasChildren(true);
        when(directoryService.listChildDirectories(eq(0L))).thenReturn(List.of(node));

        mockMvc.perform(get("/api/v1/directories").param("parentId", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].name").value("A"))
                .andExpect(jsonPath("$.data[0].hasChildren").value(true));

        verify(directoryService).listChildDirectories(eq(0L));
    }

    @Test
    @DisplayName("GET /api/v1/directories 默认 parentId=0")
    void listChildren_defaultRoot() throws Exception {
        when(directoryService.listChildDirectories(eq(0L))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/directories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(directoryService).listChildDirectories(eq(0L));
    }
}
