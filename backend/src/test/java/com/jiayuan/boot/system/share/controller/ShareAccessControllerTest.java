package com.jiayuan.boot.system.share.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiayuan.boot.system.oss.model.vo.FileListResponseVO;
import com.jiayuan.boot.system.share.model.vo.ShareAccessResponseVO;
import com.jiayuan.boot.system.share.model.vo.ShareDownloadResponseVO;
import com.jiayuan.boot.system.share.model.vo.ShareVerifyRequestVO;
import com.jiayuan.boot.system.share.service.ShareService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ShareAccessController} 匿名访问控制层测试。
 *
 * @author charleslam
 * @since 2026/04/14
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShareAccessController 匿名访问测试")
class ShareAccessControllerTest {

    @Mock
    private ShareService shareService;

    @InjectMocks
    private ShareAccessController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/s/{token} 返 ShareAccessResponseVO")
    void getShare_returnsAccessInfo() throws Exception {
        ShareAccessResponseVO vo = testModel(new ShareAccessResponseVO(), Map.of(
                "fileName", "report.pdf",
                "fileSize", 1024L,
                "fileSizeFormatted", "1.00 KB",
                "requireExtractCode", true,
                "isDirectory", false));
        when(shareService.getShareByToken("tok123")).thenReturn(vo);

        mockMvc.perform(get("/api/v1/s/{token}", "tok123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("report.pdf"))
                .andExpect(jsonPath("$.data.fileSizeFormatted").value("1.00 KB"))
                .andExpect(jsonPath("$.data.requireExtractCode").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/s/{token}/verify 转发 extractCode 给 service")
    void verify_passesExtractCode() throws Exception {
        ShareVerifyRequestVO req = testModel(new ShareVerifyRequestVO(), Map.of("extractCode", "ABCD"));

        mockMvc.perform(post("/api/v1/s/{token}/verify", "tok123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"));

        verify(shareService).verifyExtractCode(eq("tok123"), eq("ABCD"));
    }

    @Test
    @DisplayName("GET /api/v1/s/{token}/download?code=... 返后端受控下载 URL")
    void download_withCode_returnsControlledUrl() throws Exception {
        ShareDownloadResponseVO vo = new ShareDownloadResponseVO(
                "http://localhost/api/v1/s/tok123/download/file?code=ABCD", "report.pdf");
        when(shareService.getDownloadUrl(eq("tok123"), eq("ABCD"), eq(null))).thenReturn(vo);

        mockMvc.perform(get("/api/v1/s/{token}/download", "tok123").param("code", "ABCD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl")
                        .value("http://localhost/api/v1/s/tok123/download/file?code=ABCD"))
                .andExpect(jsonPath("$.data.fileName").value("report.pdf"));

        verify(shareService).getDownloadUrl(eq("tok123"), eq("ABCD"), eq(null));
    }

    @Test
    @DisplayName("GET /api/v1/s/{token}/download 不带 code（全公开）也正常")
    void download_withoutCode_forPublicShare() throws Exception {
        ShareDownloadResponseVO vo = new ShareDownloadResponseVO(
                "http://localhost/api/v1/s/tok123/download/file", "public.pdf");
        when(shareService.getDownloadUrl(eq("tok123"), eq(null), eq(null))).thenReturn(vo);

        mockMvc.perform(get("/api/v1/s/{token}/download", "tok123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl")
                        .value("http://localhost/api/v1/s/tok123/download/file"));

        verify(shareService).getDownloadUrl(eq("tok123"), eq(null), eq(null));
    }

    @Test
    @DisplayName("GET /api/v1/s/{token}/download/file 转发受控流式下载")
    void downloadFile_delegatesToService() throws Exception {
        mockMvc.perform(get("/api/v1/s/{token}/download/file", "tok123")
                        .param("code", "ABCD")
                        .param("fileId", "42"))
                .andExpect(status().isOk());

        verify(shareService).downloadFile(eq("tok123"), eq("ABCD"), eq(42L), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("GET /api/v1/s/{token}/children 默认 parentId=0 并转发 code")
    void children_defaultsParentIdAndPassesCode() throws Exception {
        FileListResponseVO vo = emptyFileList();
        when(shareService.listSharedChildren("tok123", 0L, "ABCD")).thenReturn(vo);

        mockMvc.perform(get("/api/v1/s/{token}/children", "tok123").param("code", "ABCD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());

        verify(shareService).listSharedChildren(eq("tok123"), eq(0L), eq("ABCD"));
    }

    @Test
    @DisplayName("GET /api/v1/s/{token}/children?parentId=... 转发指定目录")
    void children_passesParentId() throws Exception {
        FileListResponseVO vo = emptyFileList();
        when(shareService.listSharedChildren("tok123", 20L, null)).thenReturn(vo);

        mockMvc.perform(get("/api/v1/s/{token}/children", "tok123").param("parentId", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.breadcrumb").isArray());

        verify(shareService).listSharedChildren(eq("tok123"), eq(20L), eq(null));
    }

    private static FileListResponseVO emptyFileList() {
        return testModel(new FileListResponseVO(), Map.of(
                "items", Collections.emptyList(),
                "breadcrumb", Collections.emptyList()));
    }

    private static <T> T testModel(T target, Map<String, Object> values) {
        values.forEach((name, value) -> ReflectionTestUtils.setField(target, name, value));
        return target;
    }
}
