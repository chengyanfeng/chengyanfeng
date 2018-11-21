package util;

import org.apache.poi.xssf.streaming.SXSSFRow;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

public enum ExcelUtil {
    INSTANCE;

    private String filePath = "";
    private Integer nextRowNum = 0;

    /**
     * 得到一个工作薄对象
     */
    public SXSSFWorkbook returnWorkBookGivenFileHandle(String filePath, String defaultSheetName) {
        if (null == filePath || "".equals(filePath)) {
            return null;
        }
        this.filePath = filePath;
        if (null == defaultSheetName || "".equals(defaultSheetName)) {
            defaultSheetName = "Sheet1";
        }
        SXSSFWorkbook wb = null;
        File file = this.createFileIfAbsent(filePath);
        try {
            if (null != file) {
                if (file.length() > 0) {
                    wb = new SXSSFWorkbook(new XSSFWorkbook(new FileInputStream(file)));
                } else {
                    this.createEmptySXSSFWorkbook(file, defaultSheetName);
                    wb = new SXSSFWorkbook(new XSSFWorkbook(new FileInputStream(file)));
                }
                wb.setCompressTempFiles(true);
                wb.setCompressTempFiles(true);
            }
        } catch (IOException e) {
            e.printStackTrace();
            this.resetAttribute();
        }
        return wb;
    }

    /**
     * 获取工作簿表对象
     */
    public SXSSFSheet returnSheetFromWorkBook(SXSSFWorkbook wb) {
        if (null == wb) {
            return null;
        }
        SXSSFSheet sheet = wb.getSheetAt(0);
        sheet.setRandomAccessWindowSize(-1);
        return sheet;
    }

    /**
     * 表中插入一行新的数据
     */
    public void insertRows(SXSSFSheet sheet, Integer rowIndex, Map<Integer, String> rowCells) {
        if (null == rowIndex || null == rowCells || rowCells.isEmpty()) {
            return;
        }
        SXSSFRow row = createRow(sheet, rowIndex);
        createCell(row, rowCells);
        this.nextRowNum++;
    }

    /**
     * 保存工作薄并重置excel参数
     */
    public void saveExcelAndReset(SXSSFWorkbook wb, String filePath) {
        FileOutputStream fileOut;
        try {
            fileOut = new FileOutputStream(filePath);
            wb.write(fileOut);
            fileOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            this.resetAttribute();
        }
    }

    /**
     * 保存工作薄
     */
    public void saveExcel(SXSSFWorkbook wb, String filePath) {
        FileOutputStream fileOut;
        try {
            fileOut = new FileOutputStream(filePath);
            wb.write(fileOut);
            fileOut.close();
        } catch (IOException e) {
            e.printStackTrace();
            this.resetAttribute();
        }
    }

    /**
     * 重置excel属性
     */
    public void resetAttribute() {
        this.filePath = "";
        this.nextRowNum = 0;
    }

    public String getFilePath() {
        return filePath;
    }

    public Integer getNextRowNum() {
        return nextRowNum;
    }

    /**
     * 创建空白excel的workbook
     */
    private void createEmptySXSSFWorkbook(File file, String sheetName) {
        try {
            SXSSFWorkbook wb = new SXSSFWorkbook();
            wb.createSheet(sheetName);
            wb.write(new FileOutputStream(file));
        } catch (IOException e) {
            e.printStackTrace();
            this.resetAttribute();
        }
    }

    /**
     * 找到需要插入的行数，并新建一个POI的row对象
     */
    private SXSSFRow createRow(SXSSFSheet sheet, Integer rowIndex) {
        SXSSFRow row;
        if (sheet.getRow(rowIndex) != null) {
            int lastRowNo = sheet.getLastRowNum();
            sheet.shiftRows(rowIndex, lastRowNo, 1);
        }
        row = sheet.createRow(rowIndex);
        return row;
    }

    /**
     * 创建要添加的行中单元格
     */
    private void createCell(SXSSFRow row, Map<Integer, String> rowCells) {
        if (null == rowCells || rowCells.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, String> entry : rowCells.entrySet()) {
            Integer index = entry.getKey();
            if (null == index) {
                continue;
            }
            String value = entry.getValue() == null ? "" : entry.getValue();
            row.createCell(index).setCellValue(value);
        }
    }

    /**
     * 返回指定文件路径的文件对象。如果不存在则创建
     */
    private File createFileIfAbsent(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                boolean success = file.createNewFile();
                if (success) {
                    return file;
                }
            } else {
                return file;
            }
        } catch (IOException e) {
            e.printStackTrace();
            this.resetAttribute();
        }
        return null;
    }

}
