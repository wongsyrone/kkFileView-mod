package cn.keking.utils;

import org.junit.jupiter.api.Test;

public class KkFileUtilsTests {

    @Test
    void truncateFileNameKeepExtension_shortNameUnchanged() {
        String in = "report.pdf";
        assert KkFileUtils.truncateFileNameKeepExtension(in).equals(in);
    }

    @Test
    void truncateFileNameKeepExtension_longNamePassesLengthValidation() {
        String longName = "a".repeat(400) + ".pdf";
        String out = KkFileUtils.truncateFileNameKeepExtension(longName);
        assert KkFileUtils.validateFileNameLength(out);
        assert out.endsWith(".pdf");
        assert !out.equals(longName);
    }

    @Test
    void truncateFileNameKeepExtension_preservesDirectoryPrefix() {
        String longName = "folder/sub/" + "b".repeat(400) + ".docx";
        String out = KkFileUtils.truncateFileNameKeepExtension(longName);
        assert out.startsWith("folder/sub/");
        assert out.endsWith(".docx");
        assert KkFileUtils.validateFileNameLength(out);
    }

    @Test
    void truncateFileNameKeepExtension_differentMiddlesDoNotCollide() {
        String prefix = "x".repeat(150);
        String suffix = "y".repeat(150);
        String nameA = prefix + "AAAA" + suffix + ".txt";
        String nameB = prefix + "BBBB" + suffix + ".txt";
        String outA = KkFileUtils.truncateFileNameKeepExtension(nameA);
        String outB = KkFileUtils.truncateFileNameKeepExtension(nameB);
        assert !outA.equals(outB);
        assert KkFileUtils.validateFileNameLength(outA);
        assert KkFileUtils.validateFileNameLength(outB);
    }

    @Test
    void truncateFileNameKeepExtension_sameInputIsDeterministic() {
        String longName = "c".repeat(400) + ".xlsx";
        String out1 = KkFileUtils.truncateFileNameKeepExtension(longName);
        String out2 = KkFileUtils.truncateFileNameKeepExtension(longName);
        assert out1.equals(out2);
    }

    @Test
    void truncateFileNameKeepExtension_preservesSurrogatePairsRoundTrip() {
        // repeated emoji (surrogate pair) 混合超长文件名，确保按码点边界截断，不产生半个代理对
        String emoji = "😀";
        String longName = emoji.repeat(200) + ".png";
        String out = KkFileUtils.truncateFileNameKeepExtension(longName);
        assert KkFileUtils.validateFileNameLength(out);
        assert out.endsWith(".png");
        byte[] utf8 = out.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String roundTrip = new String(utf8, java.nio.charset.StandardCharsets.UTF_8);
        assert roundTrip.equals(out);
    }
}
