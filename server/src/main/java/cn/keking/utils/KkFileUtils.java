package cn.keking.utils;

import cn.keking.config.ConfigConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KkFileUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(KkFileUtils.class);

    public static final String DEFAULT_FILE_ENCODING = "UTF-8";

    // 路径遍历关键字列表
    private static final Set<String> illegalFileStrList;

    static {
        Set<String> set = new HashSet<>();

        // 基本路径遍历
        Collections.addAll(set, "../", "./", "..\\", ".\\", "\\..", "\\.", "..", "...", "....", ".....");

        // URL编码的路径遍历
        Collections.addAll(set, "%2e%2e%2f", "%2e%2e/", "..%2f", "%2e%2e%5c", "%2e%2e\\", "..%5c",
                "%252e%252e%252f", "%252e%252e/", "..%252f");

        // Unicode编码绕过
        Collections.addAll(set, "\\u002e\\u002e\\u002f", "\\U002e\\U002e\\U002f",
                "\u00c0\u00ae\u00c0\u00ae", "\u00c1\u009c\u00c1\u009c");

        // 特殊分隔符
        Collections.addAll(set, "|..|", "|../|", "|..\\|");

        // Windows特殊路径
        Collections.addAll(set, "\\\\?\\", "\\\\.\\");

        // 转换为不可变集合
        illegalFileStrList = Collections.unmodifiableSet(set);
    }

    /**
     * 检查文件名是否合规
     *
     * @param fileName 文件名
     * @return 合规结果, true:不合规，false:合规
     */
    public static boolean isIllegalFileName(String fileName) {
        for (String str : illegalFileStrList) {
            if (fileName.contains(str)) {
                return true;
            }
        }
        return false;
    }
    public static boolean validateFileNameLength(String fileName) {
        if (fileName == null) {
            return false;
        }
        // 单个路径组件长度限制：Windows 按字符计，Linux 按 UTF-8 字节计
        int maxLength = 255;
        String[] pathComponents = fileName.replace('\\', '/').split("/");
        for (String component : pathComponents) {
            int componentLength = isWindows()
                    ? component.length()
                    : component.getBytes(StandardCharsets.UTF_8).length;
            if (componentLength > maxLength) {
                System.err.println("文件名长度超过限制（255）：" + component);
                return false;
            }
        }
        return true;
    }

    private static final int FILE_NAME_MAX_COMPONENT_LENGTH = 255;
    private static final String TRUNCATE_MARKER_PREFIX = "~";
    private static final String TRUNCATE_MARKER_SUFFIX = "~";
    private static final int TRUNCATE_HASH_HEX_LENGTH = 8;

    /**
     * 对超过 {@link #FILE_NAME_MAX_COMPONENT_LENGTH} 的文件名做保留扩展名的中间截断，未超限的原样返回。
     * 仅处理最后一段路径（basename），目录前缀不做改动；扩展名按最后一个"."之后的部分计算（与
     * {@link #suffixFromFileName} 同一约定）。截断时插入基于原始文件名的短哈希标记，避免不同原始文件名
     * 因头尾相同而截断后落到同一缓存路径/文件名上；始终保留头部与尾部内容，不会退化为纯哈希文件名。
     * <p>
     * 这里只是极少数场景（原始文件名解码后依然超长）的兜底：绝大多数"看起来超长"其实是 originFileName
     * 还没走 {@link UrlEncoderUtils#percentDecode} 正确解码，那类问题在解码阶段就已经解决了。既然走到这里
     * 的都是真实超长文件名这种罕见兜底场景，不需要按平台字节/Unicode 码点精确计算预算，用简单的字符截取
     * + 收缩循环（按 {@link #validateFileNameLength} 同款的平台度量方式收紧）即可保证不超限。
     *
     * @param fileName 原始文件名（可能带路径）
     * @return 未超限则原样返回；超限则返回 头部+哈希标记+尾部+扩展名 的截断结果
     */
    public static String truncateFileNameKeepExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return fileName;
        }
        int sepIdx = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String dir = sepIdx >= 0 ? fileName.substring(0, sepIdx + 1) : "";
        String base = sepIdx >= 0 ? fileName.substring(sepIdx + 1) : fileName;

        if (componentLength(base) <= FILE_NAME_MAX_COMPONENT_LENGTH) {
            return fileName;
        }

        int dotIdx = base.lastIndexOf('.');
        String namePart = dotIdx > 0 ? base.substring(0, dotIdx) : base;
        String ext = dotIdx > 0 ? base.substring(dotIdx) : "";

        String marker = TRUNCATE_MARKER_PREFIX + shortHash(base) + TRUNCATE_MARKER_SUFFIX;
        int remaining = Math.max(FILE_NAME_MAX_COMPONENT_LENGTH - componentLength(ext) - componentLength(marker), 0);
        int headLen = Math.min(namePart.length(), (remaining + 1) / 2);
        int tailLen = Math.min(Math.max(namePart.length() - headLen, 0), remaining - headLen);

        String head = namePart.substring(0, headLen);
        String tail = tailLen > 0 ? namePart.substring(namePart.length() - tailLen) : "";

        String result = head + marker + tail + ext;
        // 按平台度量方式收缩到不超限；字符预算按字符数分配，非 Windows 下多字节字符可能一次分配偏多，
        // 靠这个循环兜底收紧即可，不需要提前按字节精算
        while (componentLength(result) > FILE_NAME_MAX_COMPONENT_LENGTH && (!head.isEmpty() || !tail.isEmpty())) {
            if (!tail.isEmpty()) {
                tail = tail.substring(1);
            } else {
                head = head.substring(0, head.length() - 1);
            }
            result = head + marker + tail + ext;
        }
        // 收缩可能让边界正好停在代理对中间，补一刀清理，避免留下半个 emoji
        if (!head.isEmpty() && Character.isHighSurrogate(head.charAt(head.length() - 1))) {
            head = head.substring(0, head.length() - 1);
        }
        if (!tail.isEmpty() && Character.isLowSurrogate(tail.charAt(0))) {
            tail = tail.substring(1);
        }

        return dir + head + marker + tail + ext;
    }

    private static int componentLength(String s) {
        return isWindows() ? s.length() : s.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String shortHash(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(TRUNCATE_HASH_HEX_LENGTH);
            for (int i = 0; i < TRUNCATE_HASH_HEX_LENGTH / 2; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    /**
     * 检查是否是数字
     *
     * @param str 文件名
     * @return 合规结果, true:不合规，false:合规
     */
    public static boolean isInteger(String str) {
        if (StringUtils.hasText(str)) {
            boolean strResult = str.matches("-?[0-9]+.?[0-9]*");
            return strResult;
        }
        return false;
    }

    /**
     * 判断url是否是http资源
     *
     * @param url url
     * @return 是否http
     */
    public static boolean isHttpUrl(URL url) {
        return url.getProtocol().toLowerCase().startsWith("http") || url.getProtocol().toLowerCase().startsWith("https");
    }

    /**
     * 判断url是否是file资源
     *
     */
    public static boolean isFileUrl(URL url) {
        return url.getProtocol().toLowerCase().startsWith("file");
    }

    /**
     * 判断当前操作系统是否为Windows
     */
    static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    /**
     * 判断url是否是ftp资源
     *
     * @param url url
     * @return 是否ftp
     */
    public static boolean isFtpUrl(URL url) {
        return "ftp".equalsIgnoreCase(url.getProtocol());
    }

    /**
     * 删除单个文件
     *
     * @param fileName 要删除的文件的文件名
     * @return 单个文件删除成功返回true，否则返回false
     */
    public static boolean deleteFileByName(String fileName) {
        File file = new File(fileName);
        // 如果文件路径所对应的文件存在，并且是一个文件，则直接删除
        if (file.exists() && file.isFile()) {
            if (file.delete()) {
                LOGGER.info("删除单个文件" + fileName + "成功！");
                return true;
            } else {
                LOGGER.info("删除单个文件" + fileName + "失败！");
                return false;
            }
        } else {
            LOGGER.info("删除单个文件失败：" + fileName + "不存在！");
            return false;
        }
    }


    public static String htmlEscape(String input) {
        if (StringUtils.hasText(input)) {
            //input = input.replaceAll("\\{", "%7B").replaceAll("}", "%7D").replaceAll("\\\\", "%5C");
            String htmlStr = HtmlUtils.htmlEscape(input, "UTF-8");
            //& -> &amp;
            return htmlStr.replace("&amp;", "&");
        }
        return input;
    }


    /**
     * 通过文件名获取文件后缀
     *
     * @param fileName 文件名称
     * @return 文件后缀
     */
    public static String suffixFromFileName(String fileName) {
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }


    /**
     * 根据文件路径删除文件
     *
     * @param filePath 绝对路径
     */
    public static void deleteFileByPath(String filePath) {
        File file = new File(filePath);
        if (file.exists() && !file.delete()) {
            LOGGER.warn("压缩包源文件删除失败:{}！", filePath);
        }
    }

    /**
     * 删除目录及目录下的文件
     *
     * @param dir 要删除的目录的文件路径
     * @return 目录删除成功返回true，否则返回false
     */
    public static boolean deleteDirectory(String dir) {
        // 如果dir不以文件分隔符结尾，自动添加文件分隔符
        if (!dir.endsWith(File.separator)) {
            dir = dir + File.separator;
        }
        File dirFile = new File(dir);
        // 如果dir对应的文件不存在，或者不是一个目录，则退出
        if ((!dirFile.exists()) || (!dirFile.isDirectory())) {
            LOGGER.info("删除目录失败：" + dir + "不存在！");
            return false;
        }
        boolean flag = true;
        // 删除文件夹中的所有文件包括子目录
        File[] files = dirFile.listFiles();
        for (int i = 0; i < Objects.requireNonNull(files).length; i++) {
            // 删除子文件
            if (files[i].isFile()) {
                flag = KkFileUtils.deleteFileByName(files[i].getAbsolutePath());
                if (!flag) {
                    break;
                }
            } else if (files[i].isDirectory()) {
                // 删除子目录
                flag = KkFileUtils.deleteDirectory(files[i].getAbsolutePath());
                if (!flag) {
                    break;
                }
            }
        }

        if (!dirFile.delete() || !flag) {
            LOGGER.info("删除目录失败！");
            return false;
        }
        return true;
    }

    /**
     * 判断文件是否允许上传
     *
     * @param file 文件扩展名
     * @return 是否允许上传
     */
    public static boolean isAllowedUpload(String file) {
        String fileType = suffixFromFileName(file);
        for (String type : ConfigConstants.getProhibit()) {
            if (type.equals(fileType)){
                return false;
            }
        }
        return !ObjectUtils.isEmpty(fileType);
    }

    /**
     * 判断文件是否存在
     *
     * @param filePath 文件路径
     * @return 是否存在 true:存在，false:不存在
     */
    public static boolean isExist(String filePath) {
        File file = new File(filePath);
        return file.exists();
    }
    /**
     * 判断是否是数字
     */
    public static boolean isNumeric(String str){
        Pattern pattern = Pattern.compile("[0-9]*");
        if (ObjectUtils.isEmpty(str)){
            return false;
        }
        Matcher isNum = pattern.matcher(str);
        return isNum.matches();
    }
}
