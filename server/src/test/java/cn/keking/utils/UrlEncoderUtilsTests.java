package cn.keking.utils;

import org.junit.jupiter.api.Test;

public class UrlEncoderUtilsTests {

    @Test
    void hasUrlEncoded_mixedValidEscapesAndUnescapedParens_isTrue() {
        // 复现场景（数据已脱敏）：JS encodeURIComponent 不转义括号，导致字符串里合法 %XX 转义和裸括号混在一起
        String s = "sample-report%20%EF%BC%88draft%EF%BC%89(1).docx";
        assert UrlEncoderUtils.hasUrlEncoded(s);
    }

    @Test
    void hasUrlEncoded_fullyValidPercentEncoded_isTrue() {
        String s = "%E6%96%87%E6%A1%A3.pdf";
        assert UrlEncoderUtils.hasUrlEncoded(s);
    }

    @Test
    void hasUrlEncoded_plainNonAsciiNoPercent_isFalse() {
        String s = "普通文件 report.pdf";
        assert !UrlEncoderUtils.hasUrlEncoded(s);
    }

    @Test
    void hasUrlEncoded_barePercentNotHex_isFalse() {
        String s = "50% off.pdf";
        assert !UrlEncoderUtils.hasUrlEncoded(s);
    }

    @Test
    void hasUrlEncoded_percentAtEndWithoutEnoughChars_isFalse() {
        String s = "report.pdf%2";
        assert !UrlEncoderUtils.hasUrlEncoded(s);
    }

    @Test
    void hasUrlEncoded_plainSafeCharsOnlyNoPercent_isFalse() {
        String s = "report.pdf";
        assert !UrlEncoderUtils.hasUrlEncoded(s);
    }

    @Test
    void hasUrlEncoded_lowercaseHexEscape_isTrue() {
        // RFC 3986 十六进制大小写不敏感：curl/部分标准库会输出小写 %xx
        String s = "%e6%96%87%e6%a1%a3.pdf";
        assert UrlEncoderUtils.hasUrlEncoded(s);
    }

    @Test
    void percentDecode_leavesLiteralPlusUntouched() throws Exception {
        // decodeURIComponent 语义：裸的 '+' 不是空格，必须原样保留，不能像 URLDecoder 那样解成空格
        String s = "ABC-100+D%E7%A4%BA%E4%BE%8B.pdf";
        String decoded = UrlEncoderUtils.percentDecode(s, "UTF-8");
        assert decoded.equals("ABC-100+D示例.pdf");
    }

    @Test
    void percentDecode_decodesEscapedPlus() throws Exception {
        String s = "a%2Bb.txt";
        assert UrlEncoderUtils.percentDecode(s, "UTF-8").equals("a+b.txt");
    }

    @Test
    void percentDecode_leavesUnescapedParensUntouched() throws Exception {
        String s = "%E7%A4%BA%E4%BE%8B%E6%8A%A5%E5%91%8A(1).xlsx";
        assert UrlEncoderUtils.percentDecode(s, "UTF-8").equals("示例报告(1).xlsx");
    }

    @Test
    void percentDecode_decodesMultiByteUtf8CodePoint() throws Exception {
        String s = "%E6%96%87%E6%A1%A3.pdf";
        assert UrlEncoderUtils.percentDecode(s, "UTF-8").equals("文档.pdf");
    }

    @Test
    void percentDecode_reproducesMixedEscapeShape() throws Exception {
        // 复现真实场景的字符组合（数据已脱敏）：转义部分 + 未转义括号 + 已转义 '+'（%2B）混在一起
        String encoded = "ABC%20%EF%BC%88draft%EF%BC%89XYZ-100%2BD%E7%A4%BA%E4%BE%8B(1).xlsx";
        String decoded = UrlEncoderUtils.percentDecode(encoded, "UTF-8");
        assert decoded.equals("ABC （draft）XYZ-100+D示例(1).xlsx");
        assert decoded.length() < encoded.length();
    }

    @Test
    void percentDecode_noEscapesIsUnchanged() throws Exception {
        String s = "report(1).pdf";
        assert UrlEncoderUtils.percentDecode(s, "UTF-8").equals(s);
    }

    @Test
    void percentDecode_lowercaseHexEscape_decodesCorrectly() throws Exception {
        String s = "%e6%96%87%e6%a1%a3.pdf";
        assert UrlEncoderUtils.percentDecode(s, "UTF-8").equals("文档.pdf");
    }
}
