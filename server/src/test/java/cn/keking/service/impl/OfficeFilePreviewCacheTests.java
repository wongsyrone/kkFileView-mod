package cn.keking.service.impl;

import cn.keking.model.FileAttribute;
import cn.keking.service.FileHandlerService;
import cn.keking.service.PdfToJpgService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfficeFilePreviewCacheTests {

    private final FileHandlerService fileHandler = mock(FileHandlerService.class);
    private final PdfToJpgService pdfToJpg = mock(PdfToJpgService.class);
    private final OfficeFilePreviewImpl preview = new OfficeFilePreviewImpl(
            fileHandler, null, new OtherFilePreviewImpl(), pdfToJpg);
    private final String pdfPath = "cached-document.pdf";
    private final List<String> images = List.of("cached-document/0.jpg");

    @ParameterizedTest
    @CsvSource({
            "docx, image, officePicture",
            "docx, allImages, picture",
            "pptx, image, ppt",
            "dwg, image, officePicture"
    })
    void shouldGenerateImagesWhenOnlyConvertedPdfIsCached(String suffix, String mode, String view) throws Exception {
        FileAttribute file = new FileAttribute();
        file.setSuffix(suffix);
        when(fileHandler.loadPdf2jpgCache(pdfPath)).thenReturn(List.of());
        when(pdfToJpg.pdf2jpg(pdfPath, pdfPath, file)).thenReturn(images);
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals(view, preview.getPreviewType(model, file, mode, pdfPath, pdfPath));
        assertEquals(images, model.get("imgUrls"));
        assertEquals(images.getFirst(), model.get("currentUrl"));
        verify(pdfToJpg).pdf2jpg(pdfPath, pdfPath, file);
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void shouldReuseExistingImagesWithoutConversion(boolean encrypted) throws Exception {
        FileAttribute file = new FileAttribute();
        file.setSuffix("docx");
        when(pdfToJpg.hasEncryptedPdfCacheSimple(pdfPath)).thenReturn(encrypted);
        if (encrypted) {
            when(pdfToJpg.getEncryptedPdfCache(pdfPath)).thenReturn(images);
        } else {
            when(fileHandler.loadPdf2jpgCache(pdfPath)).thenReturn(images);
        }
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("officePicture", preview.getPreviewType(model, file, "image", pdfPath, pdfPath));
        assertEquals(images, model.get("imgUrls"));
        verify(pdfToJpg, never()).pdf2jpg(pdfPath, pdfPath, file);
    }

    @Test
    void shouldReportFailureWhenImageGenerationReturnsNoImages() {
        FileAttribute file = new FileAttribute();
        file.setSuffix("docx");
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("fileNotSupported", preview.getPreviewType(model, file, "image", pdfPath, pdfPath));
        assertEquals("Office转换缓存异常，请联系管理员", model.get("msg"));
    }
}
