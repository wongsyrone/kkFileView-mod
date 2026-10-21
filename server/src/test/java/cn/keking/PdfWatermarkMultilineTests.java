package cn.keking;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDF 预览页的水印实现独立于 static/js/watermark.js（viewer.mjs 自带一份），
 * 多行水印必须两边都支持，否则同一份 watermark.txt 在 PDF 与其它预览里表现不一致。
 *
 * 关键约束（保持上游语义）：单行水印行为不变 —— 生效高度取 max(配置高度, 行高×行数)，
 * 单行时配置高度（默认 80/180）远大于行高，结果与配置值一致。
 */
class PdfWatermarkMultilineTests {

    @Test
    void shouldSplitWatermarkTextIntoLinesInPdfViewer() throws IOException {
        String viewer = readResource("/static/pdfjs/web/viewer.mjs");

        assertTrue(viewer.contains("function getWatermarkLines(text)"),
                "viewer.mjs must expose a line splitter for multi-line watermarks");
        // 配置里写成字面量 \n 与真实换行都要能拆行
        assertTrue(viewer.contains(".replace(/\\\\r\\\\n/g, \"\\n\")"),
                "literal \\r\\n must be normalised");
        assertTrue(viewer.contains(".replace(/\\r\\n/g, \"\\n\")"),
                "real CRLF must be normalised");
        assertTrue(viewer.contains(".split(\"\\n\")"));
    }

    @Test
    void shouldGrowWatermarkBoxToFitAllLines() throws IOException {
        String viewer = readResource("/static/pdfjs/web/viewer.mjs");

        assertTrue(viewer.contains("const lines = getWatermarkLines(watermarkTxt);"));
        assertTrue(viewer.contains("const lineHeight = Math.ceil(getWatermarkFontSize(settings.fontsize) * 1.35);"));
        // 单行不变的关键：取 max 而不是直接覆盖配置高度
        assertTrue(viewer.contains("settings.height = Math.max(settings.height, lineHeight * lines.length);"),
                "effective height must be max(configured height, lineHeight * lines)");
        assertTrue(viewer.contains("div.style.height = `${settings.height}px`;"));
    }

    @Test
    void shouldNotStackWatermarksOnRepeatedRenders() throws IOException {
        String viewer = readResource("/static/pdfjs/web/viewer.mjs");

        // PDFPageView.render() 会在缩放时对同一个页面 div 反复调用 addWatermark
        assertTrue(viewer.contains("div.className = 'mask_div kk-pdf-watermark';"));
        assertTrue(viewer.contains("function removeRenderedWatermarks(container)"));
        assertTrue(viewer.contains("child.classList.contains('kk-pdf-watermark')"));
    }

    private String readResource(String resourcePath) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            assertNotNull(in, () -> "resource not found: " + resourcePath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
