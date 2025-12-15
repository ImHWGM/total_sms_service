package kr.wisead.domain.excel.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel 읽기 옵션 DTO
 */
@Data
@Builder
public class ExcelReadOption {

    private String filePath;                // 엑셀 파일 경로
    private List<String> outputColumns;     // 추출할 컬럼명 (A, B, C, ...)
    private int startRow;                   // 시작 행 번호 (1부터 시작)

    public static ExcelReadOption of(String filePath, int startRow, String... columns) {
        List<String> cols = new ArrayList<>();
        for (String col : columns) {
            cols.add(col);
        }
        return ExcelReadOption.builder()
                .filePath(filePath)
                .startRow(startRow)
                .outputColumns(cols)
                .build();
    }
}
