package cn.keking.utils;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

public class UrlEncoderUtils {

    /**
     * 判断str是否urlEncoder.encode过<br>
     * 经常遇到这样的情况，拿到一个URL,但是搞不清楚到底要不要encode.<Br>
     * 不做encode吧，担心出错，做encode吧，又怕重复了<Br>
     */
    public static boolean hasUrlEncoded(String str) {

        /*
         * 判断依据：字符串里只要存在至少一个合法的 %XX 转义序列（X 是十六进制大写字符，范围 [0-9A-F]），
         * 且不存在裸的、不合规范的 % ，就认为这个字符串整体是 urlEncode 过的，值得 decode。
         *
         * 之所以不要求“每个字符都必须是安全字符或 %XX”，是因为不少编码器（比如浏览器/JS 的
         * encodeURIComponent）不会转义 ( ) ! ~ ' * 等字符，真实转义过的字符串经常是
         * “正常转义部分 + 少量未转义的安全标点”混在一起。之前要求整串都合规的写法，会因为
         * 字符串里混了一个没转义的括号，就把整串误判成“未转义”，导致该 decode 的没有被 decode，
         * 未解码的 %XX 转义串（往往比真实内容长很多）被当成原始文件名继续往下传。
         *
         * 裸的 %（后面不是两位合法十六进制字符）依然是明确的“不是完整 urlEncode 过的字符串”信号，
         * 一旦出现直接判定为未转义，交给调用方决定是否重新编码。
         */
        boolean hasValidEscape = false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c != '%') {
                continue;
            }
            if (i + 2 < str.length() && isDigit16Char(str.charAt(i + 1)) && isDigit16Char(str.charAt(i + 2))) {
                hasValidEscape = true;
                i += 2;
            } else {
                return false;
            }
        }

        return hasValidEscape;
    }

    /**
     * 按 RFC 3986 / JS decodeURIComponent 的语义做 percent-decode：只解码 %XX，不把 '+' 当作空格处理。
     * <br>
     * kkFileView 里被 decode 的字符串（originFileName、compressFilePath 等）来自前端 encodeURIComponent
     * 编码后的结果，而 encodeURIComponent 会把字面量 '+' 转义成 %2B，从不会输出裸的 '+' 表示空格；
     * java.net.URLDecoder 实现的是 application/x-www-form-urlencoded 语义（'+' 会被解成空格），
     * 用它解 encodeURIComponent 的产物，遇到字面量确实是 '+' 的场景就会被错误地解码成空格。
     * 这里手写一个只认 %XX、其余字符原样保留的解码，和 decodeURIComponent 语义对齐。
     *
     * @param str      待解码字符串
     * @param encoding 转义字节对应的字符集名称
     */
    public static String percentDecode(String str, String encoding) throws UnsupportedEncodingException {
        if (str == null) {
            return null;
        }
        StringBuilder result = new StringBuilder(str.length());
        ByteArrayOutputStream escapedBytes = new ByteArrayOutputStream();
        int len = str.length();
        int i = 0;
        while (i < len) {
            char c = str.charAt(i);
            if (c == '%' && i + 2 < len && isDigit16Char(str.charAt(i + 1)) && isDigit16Char(str.charAt(i + 2))) {
                escapedBytes.write(Integer.parseInt(str.substring(i + 1, i + 3), 16));
                i += 3;
                continue;
            }
            if (escapedBytes.size() > 0) {
                result.append(escapedBytes.toString(encoding));
                escapedBytes.reset();
            }
            result.append(c);
            i++;
        }
        if (escapedBytes.size() > 0) {
            result.append(escapedBytes.toString(encoding));
        }
        return result.toString();
    }

    /**
     * 判断c是否是16进制的字符。RFC 3986 里 %XX 的十六进制大小写不敏感（浏览器/JS encodeURIComponent
     * 固定输出大写，但 curl、部分语言标准库、手工拼接的 URL 经常是小写），这里大小写都接受。
     */
    private static boolean isDigit16Char(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
    }
}