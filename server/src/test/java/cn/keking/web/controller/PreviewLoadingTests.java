package cn.keking.web.controller;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.model.FileType;
import cn.keking.service.FileHandlerService;
import cn.keking.service.FilePreview;
import cn.keking.service.FilePreviewFactory;
import freemarker.template.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PreviewLoadingTests {
    private final String previousKey = ConfigConstants.getKey();
    private final String previousWeb = ConfigConstants.getOfficeTypeWeb();
    private final String previousTiff = ConfigConstants.getTifPreviewType();
    private final String previousMedia = ConfigConstants.getMediaConvertDisable();
    private final FileHandlerService handler = mock(FileHandlerService.class);
    private final FilePreviewFactory factory = mock(FilePreviewFactory.class);
    private final FilePreview preview = mock(FilePreview.class);
    private final OnlinePreviewController controller = new OnlinePreviewController(factory, handler, null, null);
    private final FileAttribute file = new FileAttribute();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final ExtendedModelMap model = new ExtendedModelMap();
    private final String source = "https://files.example/document";

    @BeforeEach void setup() {
        ConfigConstants.setKeyValue("false");
        ConfigConstants.setOfficeTypeWebValue("web");
        ConfigConstants.setTifPreviewTypeValue("jpg");
        ConfigConstants.setMediaConvertDisableValue("true");
        file.setName("document");
        when(handler.getFileAttribute(anyString(), any())).thenReturn(file);
        when(factory.get(file)).thenReturn(preview);
        when(preview.filePreviewHandle(anyString(), any(), same(file))).thenReturn("converted");
    }

    @AfterEach void restore() {
        ConfigConstants.setKeyValue(previousKey);
        ConfigConstants.setOfficeTypeWebValue(previousWeb);
        ConfigConstants.setTifPreviewTypeValue(previousTiff);
        ConfigConstants.setMediaConvertDisableValue(previousMedia);
    }

    private String open(String url) {
        String encoded = Base64.getEncoder().encodeToString(url.getBytes(StandardCharsets.UTF_8));
        return controller.onlinePreview(encoded, null, null, "false", "3", "false", model, request);
    }

    @ParameterizedTest
    @CsvSource({"OFFICE,docx,pdf,false", "OFFICE,xlsx,html,true", "OFFICE,pptx,image,false",
            "OFFICE,docx,allImages,false", "PDF,pdf,image,false", "PDF,pdf,allImages,false",
            "CAD,dwg,pdf,false", "TIFF,tiff,pdf,false", "MEDIACONVERT,avi,pdf,false",
            "COMPRESS,zip,pdf,false"})
    void displaysWaitingBeforeStartingConversion(FileType type, String suffix, String mode, boolean html) {
        file.setType(type);
        file.setSuffix(suffix);
        file.setOfficePreviewType(mode);
        file.setHtmlView(html);
        file.setForceUpdatedCache(true);
        request.setParameter("forceUpdatedCache", "true");

        assertEquals("waiting", open(source));
        assertEquals(true, model.get("loadingPreview"));
        verifyNoInteractions(preview);

        request.addHeader("X-KK-Preview-Loading", "true");
        assertEquals("converted", open(source));
        verify(preview).filePreviewHandle(eq(source), same(model), same(file));
        assertTrue(file.forceUpdatedCache());
        assertEquals("true", request.getParameter("forceUpdatedCache"));
        assertEquals("3", model.get("page"));
    }

    @ParameterizedTest
    @CsvSource({"PDF,pdf,pdf", "PICTURE,png,image", "MEDIA,mp4,pdf",
            "OFFICE,xlsx,pdf", "OFFICE,csv,image", "EML,eml,pdf"})
    void keepsDirectPreviewPaths(FileType type, String suffix, String mode) {
        file.setType(type);
        file.setSuffix(suffix);
        file.setOfficePreviewType(mode);
        assertEquals("converted", open(source));
    }

    @Test void followsTiffAndMediaConversionSettings() {
        file.setType(FileType.TIFF);
        ConfigConstants.setTifPreviewTypeValue("tif");
        assertEquals("converted", open(source));
        ConfigConstants.setTifPreviewTypeValue("PDF");
        assertEquals("waiting", open(source));
        file.setType(FileType.MEDIACONVERT);
        ConfigConstants.setMediaConvertDisableValue("false");
        assertEquals("converted", open(source));
        assertEquals("waiting", open("ftp://files.example/document.avi"));
    }

    @Test void rendersBothInitialLoadingAndExistingPollingWithoutMissingVariables() throws Exception {
        Configuration config = new Configuration(Configuration.VERSION_2_3_32);
        config.setClassForTemplateLoading(getClass(), "/web");
        model.put("fileName", "document.docx");
        model.put("message", "文件正在转换中，请稍候");
        model.put("loadingPreview", true);
        StringWriter output = new StringWriter();
        config.getTemplate("waiting.ftl").process(model, output);
        assertTrue(output.toString().contains("X-KK-Preview-Loading"));
        assertFalse(output.toString().contains("location.reload()"));
        assertFalse(output.toString().contains("cleanForceUpdateParam"));

        model.remove("loadingPreview");
        model.put("time", 3);
        output = new StringWriter();
        config.getTemplate("waiting.ftl").process(model, output);
        assertTrue(output.toString().contains("let countdown = 3;"));
        assertTrue(output.toString().contains("cleanForceUpdateParam"));
        assertFalse(output.toString().contains("fetch("));
    }
}
