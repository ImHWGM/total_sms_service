package kr.wisead.domain.excel.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.excel.dto.ExcelReadOption;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Color;
import java.io.*;
import java.util.*;

/**
 * Excel 서비스
 * - Excel 파일 읽기/쓰기 유틸리티
 */
@Slf4j
@Service
public class ExcelService {

    /**
     * Excel 파일 읽기
     */
    public List<Map<String, String>> readExcel(ExcelReadOption option) {
        List<Map<String, String>> result = new ArrayList<>();

        try (Workbook workbook = getWorkbook(option.getFilePath())) {
            Sheet sheet = workbook.getSheetAt(0);
            int numOfRows = sheet.getPhysicalNumberOfRows();

            for (int rowIndex = option.getStartRow() - 1; rowIndex < numOfRows; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                Cell firstCell = row.getCell(0);
                if (firstCell == null || getCellValue(firstCell).isEmpty()) continue;

                Map<String, String> rowData = new HashMap<>();
                int numOfCells = row.getPhysicalNumberOfCells();

                for (int cellIndex = 0; cellIndex < numOfCells; cellIndex++) {
                    Cell cell = row.getCell(cellIndex);
                    String columnName = getColumnName(cellIndex);

                    if (option.getOutputColumns() != null &&
                            !option.getOutputColumns().contains(columnName)) {
                        continue;
                    }

                    rowData.put(columnName, getCellValue(cell));
                }

                result.add(rowData);
            }
        } catch (IOException e) {
            log.error("Excel 파일 읽기 실패: {}", option.getFilePath(), e);
            throw new BusinessException(ErrorCode.FILE_READ_FAILED, "Excel 파일을 읽을 수 없습니다.");
        }

        return result;
    }

    /**
     * MultipartFile에서 Excel 읽기
     */
    public List<Map<String, String>> readExcel(MultipartFile file, int startRow, String... outputColumns) {
        List<Map<String, String>> result = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = getWorkbook(is, file.getOriginalFilename())) {

            Sheet sheet = workbook.getSheetAt(0);
            int numOfRows = sheet.getPhysicalNumberOfRows();
            List<String> columns = Arrays.asList(outputColumns);

            for (int rowIndex = startRow - 1; rowIndex < numOfRows; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                Cell firstCell = row.getCell(0);
                if (firstCell == null || getCellValue(firstCell).isEmpty()) continue;

                Map<String, String> rowData = new HashMap<>();
                int numOfCells = row.getPhysicalNumberOfCells();

                for (int cellIndex = 0; cellIndex < numOfCells; cellIndex++) {
                    Cell cell = row.getCell(cellIndex);
                    String columnName = getColumnName(cellIndex);

                    if (!columns.isEmpty() && !columns.contains(columnName)) {
                        continue;
                    }

                    rowData.put(columnName, getCellValue(cell));
                }

                result.add(rowData);
            }
        } catch (IOException e) {
            log.error("Excel 파일 읽기 실패", e);
            throw new BusinessException(ErrorCode.FILE_READ_FAILED, "Excel 파일을 읽을 수 없습니다.");
        }

        return result;
    }

    /**
     * 새 Workbook 생성 (스트리밍 방식 - 대용량 데이터용)
     */
    public SXSSFWorkbook createWorkbook() {
        return new SXSSFWorkbook();
    }

    /**
     * 시트 생성
     */
    public Sheet createSheet(Workbook workbook, String sheetName) {
        return workbook.createSheet(safeSheetName(sheetName));
    }

    /**
     * 헤더 스타일 생성
     */
    public CellStyle createHeaderStyle(Workbook workbook, int fontSize, boolean bold, int r, int g, int b) {
        CellStyle style = workbook.createCellStyle();
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        if (style instanceof XSSFCellStyle) {
            XSSFCellStyle xssfStyle = (XSSFCellStyle) style;
            XSSFColor color = new XSSFColor(new Color(r, g, b), null);
            xssfStyle.setFillForegroundColor(color);
        }

        Font font = workbook.createFont();
        font.setBold(bold);
        font.setFontHeightInPoints((short) fontSize);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setAlignment(HorizontalAlignment.CENTER);

        return style;
    }

    /**
     * 테두리가 있는 스타일 생성
     */
    public CellStyle createBorderedStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /**
     * 숫자 포맷 스타일 생성
     */
    public CellStyle createNumberStyle(Workbook workbook, String format) {
        CellStyle style = workbook.createCellStyle();
        DataFormat df = workbook.createDataFormat();
        style.setDataFormat(df.getFormat(format));
        return style;
    }

    /**
     * 헤더 행 생성
     */
    public void createHeaderRow(Sheet sheet, int rowIndex, List<String> headers, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers.get(i));
            if (style != null) {
                cell.setCellStyle(style);
            }
        }
    }

    /**
     * 데이터 행 생성
     */
    public void createDataRow(Sheet sheet, int rowIndex, List<Object> data, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < data.size(); i++) {
            Cell cell = row.createCell(i);
            Object value = data.get(i);

            if (value == null) {
                cell.setCellValue("");
            } else if (value instanceof Number) {
                cell.setCellValue(((Number) value).doubleValue());
            } else if (value instanceof Date) {
                cell.setCellValue((Date) value);
            } else if (value instanceof Boolean) {
                cell.setCellValue((Boolean) value);
            } else {
                cell.setCellValue(value.toString());
            }

            if (style != null) {
                cell.setCellStyle(style);
            }
        }
    }

    /**
     * 셀 병합
     */
    public void mergeCells(Sheet sheet, int firstRow, int lastRow, int firstCol, int lastCol) {
        sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, firstCol, lastCol));
    }

    /**
     * Workbook을 바이트 배열로 변환
     */
    public byte[] toByteArray(Workbook workbook) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            workbook.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            log.error("Workbook 변환 실패", e);
            throw new BusinessException(ErrorCode.FILE_WRITE_FAILED, "Excel 파일 생성에 실패했습니다.");
        }
    }

    // ==================== Private Methods ====================

    private Workbook getWorkbook(String filePath) throws IOException {
        File file = new File(filePath);
        try (FileInputStream fis = new FileInputStream(file)) {
            if (filePath.endsWith(".xlsx")) {
                return new XSSFWorkbook(fis);
            } else if (filePath.endsWith(".xls")) {
                return new HSSFWorkbook(fis);
            } else {
                throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "지원하지 않는 Excel 파일 형식입니다.");
            }
        }
    }

    private Workbook getWorkbook(InputStream is, String fileName) throws IOException {
        if (fileName != null && fileName.endsWith(".xlsx")) {
            return new XSSFWorkbook(is);
        } else if (fileName != null && fileName.endsWith(".xls")) {
            return new HSSFWorkbook(is);
        } else {
            // 기본적으로 xlsx로 시도
            return new XSSFWorkbook(is);
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toString();
                }
                double numValue = cell.getNumericCellValue();
                if (numValue == Math.floor(numValue)) {
                    return String.valueOf((long) numValue);
                }
                return String.valueOf(numValue);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    private String getColumnName(int columnIndex) {
        StringBuilder sb = new StringBuilder();
        while (columnIndex >= 0) {
            sb.insert(0, (char) ('A' + columnIndex % 26));
            columnIndex = columnIndex / 26 - 1;
        }
        return sb.toString();
    }

    private String safeSheetName(String name) {
        if (name == null) return "Sheet";
        String safe = name.replaceAll("[\\\\/?*\\[\\]:]", "");
        return safe.length() > 31 ? safe.substring(0, 31) : safe;
    }
}
