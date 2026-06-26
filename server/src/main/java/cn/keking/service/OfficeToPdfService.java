package cn.keking.service;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import com.sun.star.document.UpdateDocMode;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.local.LocalConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author yudian-it
 */
@Component
public class OfficeToPdfService {

    private final static Logger logger = LoggerFactory.getLogger(OfficeToPdfService.class);

    public void openOfficeToPDF(String inputFilePath, String outputFilePath, FileAttribute fileAttribute) throws OfficeException {
        office2pdf(inputFilePath, outputFilePath, fileAttribute);
    }


    public static void converterFile(File inputFile, String outputFilePath_end, FileAttribute fileAttribute) throws OfficeException {
        Instant startTime = Instant.now();
        File outputFile = new File(outputFilePath_end);
        File preparedInputFile = inputFile;
        File temporaryInputFile = null;
        // 假如目标路径不存在,则新建该路径
        if (!outputFile.getParentFile().exists() && !outputFile.getParentFile().mkdirs()) {
            logger.error("创建目录【{}】失败，请检查目录权限！",outputFilePath_end);
        }
        LocalConverter.Builder builder;
        Map<String, Object> filterData = new HashMap<>();
        filterData.put("EncryptFile", true);
        if(!ConfigConstants.getOfficePageRange().equals("false")){
            filterData.put("PageRange", ConfigConstants.getOfficePageRange()); //限制页面
        }
        if(!ConfigConstants.getOfficeWatermark().equals("false")){
            filterData.put("Watermark", ConfigConstants.getOfficeWatermark());  //水印
        }
        filterData.put("Quality", ConfigConstants.getOfficeQuality()); //图片压缩
        filterData.put("MaxImageResolution", ConfigConstants.getOfficeMaxImageResolution()); //DPI
        if(ConfigConstants.getOfficeExportBookmarks()){
            filterData.put("ExportBookmarks", true); //导出书签
        }
        if(ConfigConstants.getOfficeExportNotes()){
            filterData.put("ExportNotes", true); //批注作为PDF的注释
        }
        if(ConfigConstants.getOfficeDocumentOpenPasswords()){
            filterData.put("DocumentOpenPassword", fileAttribute.getFilePassword()); //给PDF添加密码
        }
        Map<String, Object> customProperties = new HashMap<>();
        customProperties.put("FilterData", filterData);
        if (StringUtils.isNotBlank(fileAttribute.getFilePassword())) {
            Map<String, Object> loadProperties = new HashMap<>();
            loadProperties.put("Hidden", true);
            loadProperties.put("ReadOnly", true);
            loadProperties.put("UpdateDocMode", UpdateDocMode.NO_UPDATE);
            loadProperties.put("Password", fileAttribute.getFilePassword());
            builder = LocalConverter.builder().loadProperties(loadProperties).storeProperties(customProperties);
        } else {
            builder = LocalConverter.builder().storeProperties(customProperties);
        }

        try {
            temporaryInputFile = prepareSpreadsheetForPdf(inputFile, fileAttribute);
            if (temporaryInputFile != null) {
                preparedInputFile = temporaryInputFile;
            }
            builder.build().convert(preparedInputFile).to(outputFile).execute();

            // 计算转换耗时
            Instant endTime = Instant.now();
            Duration duration = Duration.between(startTime, endTime);

            // 格式化显示耗时（支持不同时间单位）
            String durationFormatted;
            if (duration.toMinutes() > 0) {
                durationFormatted = String.format("%d分%d秒", duration.toMinutes(), duration.toSecondsPart());
            } else if (duration.toSeconds() > 0) {
                durationFormatted = String.format("%d.%03d秒",duration.toSeconds(), duration.toMillisPart());
            } else {
                durationFormatted = String.format("%d毫秒", duration.toMillis());
            }

            logger.info("文件转换成功：{} -> {}，耗时：{}",
                    inputFile.getName(),outputFile.getName(),  durationFormatted);

        } catch (OfficeException e) {
            Instant endTime = Instant.now();
            Duration duration = Duration.between(startTime, endTime);
            logger.error("文件转换失败：{}，已耗时：{}毫秒，错误信息：{}", inputFile.getName(), duration.toMillis(), e.getMessage());
            throw e;
        } finally {
            if (temporaryInputFile != null && temporaryInputFile.exists() && !temporaryInputFile.delete()) {
                logger.warn("删除临时转换文件失败：{}", temporaryInputFile.getAbsolutePath());
            }
        }
    }

    private static File prepareSpreadsheetForPdf(File inputFile, FileAttribute fileAttribute) {
        if (fileAttribute == null || fileAttribute.isHtmlView() || !isPoiSpreadsheet(fileAttribute.getSuffix())) {
            return null;
        }
        String suffix = "." + fileAttribute.getSuffix().toLowerCase(Locale.ROOT);
        try (Workbook workbook = WorkbookFactory.create(inputFile)) {
            boolean changed = false;
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                SheetBounds bounds = findSheetBounds(sheet);
                if (bounds == null) {
                    continue;
                }
                workbook.setPrintArea(i, bounds.minColumn, bounds.maxColumn, bounds.minRow, bounds.maxRow);
                sheet.setFitToPage(true);
                sheet.setAutobreaks(true);
                PrintSetup printSetup = sheet.getPrintSetup();
                printSetup.setFitWidth((short) 1);
                // 只适配宽度，高度放开自然分页，避免长表格被压缩到单页后无法阅读。
                printSetup.setFitHeight((short) 0);
                printSetup.setLandscape(bounds.contentWidth >= bounds.contentHeight);
                changed = true;
            }
            if (!changed) {
                return null;
            }
            File temporaryFile = File.createTempFile("kk-preview-spreadsheet-", suffix);
            try (FileOutputStream outputStream = new FileOutputStream(temporaryFile)) {
                workbook.write(outputStream);
            }
            return temporaryFile;
        } catch (Exception e) {
            logger.warn("预处理电子表格打印设置失败，将使用原文件转换：{}", inputFile.getName(), e);
            return null;
        }
    }

    private static boolean isPoiSpreadsheet(String suffix) {
        return "xls".equalsIgnoreCase(suffix) || "xlsx".equalsIgnoreCase(suffix);
    }

    private static SheetBounds findSheetBounds(Sheet sheet) {
        int minRow = Integer.MAX_VALUE;
        int maxRow = -1;
        int minColumn = Integer.MAX_VALUE;
        int maxColumn = -1;
        double contentHeight = 0;

        for (Row row : sheet) {
            short firstCell = row.getFirstCellNum();
            short lastCell = row.getLastCellNum();
            if (firstCell < 0 || lastCell < 0) {
                continue;
            }
            minRow = Math.min(minRow, row.getRowNum());
            maxRow = Math.max(maxRow, row.getRowNum());
            minColumn = Math.min(minColumn, firstCell);
            maxColumn = Math.max(maxColumn, lastCell - 1);
        }

        if (maxRow < 0 || maxColumn < 0) {
            return null;
        }

        double contentWidth = 0;
        for (int column = minColumn; column <= maxColumn; column++) {
            contentWidth += sheet.getColumnWidthInPixels(column);
        }
        for (int rowIndex = minRow; rowIndex <= maxRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            float heightInPoints = row == null ? sheet.getDefaultRowHeightInPoints() : row.getHeightInPoints();
            contentHeight += heightInPoints * 96D / 72D;
        }
        return new SheetBounds(minRow, maxRow, minColumn, maxColumn, contentWidth, contentHeight);
    }

    private static class SheetBounds {
        private final int minRow;
        private final int maxRow;
        private final int minColumn;
        private final int maxColumn;
        private final double contentWidth;
        private final double contentHeight;

        private SheetBounds(int minRow, int maxRow, int minColumn, int maxColumn, double contentWidth, double contentHeight) {
            this.minRow = minRow;
            this.maxRow = maxRow;
            this.minColumn = minColumn;
            this.maxColumn = maxColumn;
            this.contentWidth = contentWidth;
            this.contentHeight = contentHeight;
        }
    }


    public void office2pdf(String inputFilePath, String outputFilePath, FileAttribute fileAttribute) throws OfficeException {
        if (null != inputFilePath) {
            File inputFile = new File(inputFilePath);
            // 判断目标文件路径是否为空
            if (null == outputFilePath) {
                // 转换后的文件路径
                String outputFilePath_end = getOutputFilePath(inputFilePath);
                if (inputFile.exists()) {
                    // 找不到源文件, 则返回
                    converterFile(inputFile, outputFilePath_end, fileAttribute);
                }
            } else {
                if (inputFile.exists()) {
                    // 找不到源文件, 则返回
                    converterFile(inputFile, outputFilePath, fileAttribute);
                }
            }
        }
    }

    public static String getOutputFilePath(String inputFilePath) {
        return inputFilePath.replaceAll("."+ getPostfix(inputFilePath), ".pdf");
    }

    public static String getPostfix(String inputFilePath) {
        return inputFilePath.substring(inputFilePath.lastIndexOf(".") + 1);
    }

}
