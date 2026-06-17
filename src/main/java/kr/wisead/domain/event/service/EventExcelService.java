package kr.wisead.domain.event.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.excel.service.ExcelService;
import kr.wisead.domain.survey.entity.SurveyMaster;
import kr.wisead.mapper.primary.SurveyMasterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 행사 엑셀 다운로드 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventExcelService {

  private final EventParticipantService participantService;
  private final SurveyMasterMapper surveyMasterMapper;
  private final ExcelService excelService;
  private final EventAccessValidator eventAccessValidator;

  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  /** 참가자 목록 엑셀 생성 */
  @Transactional(readOnly = true)
  public byte[] createParticipantExcel(Integer eventSeq, String userId) {
    eventAccessValidator.validateEventReadAccess(eventSeq, userId);

    // 이벤트 정보 조회
    SurveyMaster event =
        surveyMasterMapper
            .selectByEventSeq(eventSeq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "행사 정보를 찾을 수 없습니다."));

    // 참가자 데이터 조회
    List<Map<String, Object>> participants =
        participantService.getParticipantsForExcelAsMap(eventSeq);

    // 엑셀 생성
    try (SXSSFWorkbook workbook = excelService.createWorkbook()) {
      Sheet sheet = excelService.createSheet(workbook, event.getEventName() + " 참가자 목록");

      // 헤더 스타일
      CellStyle headerStyle = excelService.createHeaderStyle(workbook, 11, true, 200, 220, 240);
      CellStyle dataStyle = excelService.createBorderedStyle(workbook);

      // 헤더 생성
      List<String> headers =
          Arrays.asList(
              "번호", "이름", "연락처", "이메일", "소속", "직책", "참가자 유형", "체크코드", "등록구분", "명찰 출력", "액션 현황",
              "메모", "등록일");
      excelService.createHeaderRow(sheet, 0, headers, headerStyle);

      // 데이터 행 생성
      int rowNum = 1;
      for (Map<String, Object> participant : participants) {
        List<Object> rowData =
            Arrays.asList(
                rowNum,
                participant.get("이름"),
                participant.get("연락처"),
                participant.get("이메일"),
                participant.get("소속"),
                participant.get("직책"),
                participant.get("참가자 유형"),
                participant.get("체크코드"),
                participant.get("등록구분"),
                participant.get("명찰 출력"),
                participant.get("액션 현황"),
                participant.get("메모"),
                participant.get("등록일"));
        excelService.createDataRow(sheet, rowNum++, rowData, dataStyle);
      }

      // 컬럼 너비 자동 조정 (SXSSF에서는 trackColumnsForAutoSizing 사용)
      for (int i = 0; i < headers.size(); i++) {
        sheet.setColumnWidth(i, getColumnWidth(i));
      }

      return excelService.toByteArray(workbook);
    } catch (Exception e) {
      log.error("참가자 엑셀 생성 실패: eventSeq={}", eventSeq, e);
      throw new BusinessException(ErrorCode.FILE_WRITE_FAILED, "엑셀 파일 생성에 실패했습니다.");
    }
  }

  /** 통계 엑셀 생성 */
  @Transactional(readOnly = true)
  public byte[] createStatisticsExcel(Integer eventSeq, String userId) {
    eventAccessValidator.validateEventReadAccess(eventSeq, userId);

    // 이벤트 정보 조회
    SurveyMaster event =
        surveyMasterMapper
            .selectByEventSeq(eventSeq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "행사 정보를 찾을 수 없습니다."));

    // 통계 데이터 조회
    var statistics = participantService.getStatistics(eventSeq, userId);

    try (SXSSFWorkbook workbook = excelService.createWorkbook()) {
      // 요약 시트
      createSummarySheet(workbook, event, statistics);

      // 액션별 통계 시트
      createActionStatsSheet(workbook, statistics);

      // 참가자 유형별 통계 시트
      createParticipantTypeStatsSheet(workbook, statistics);

      return excelService.toByteArray(workbook);
    } catch (Exception e) {
      log.error("통계 엑셀 생성 실패: eventSeq={}", eventSeq, e);
      throw new BusinessException(ErrorCode.FILE_WRITE_FAILED, "엑셀 파일 생성에 실패했습니다.");
    }
  }

  private void createSummarySheet(
      SXSSFWorkbook workbook,
      SurveyMaster event,
      kr.wisead.domain.event.dto.EventStatisticsResponse statistics) {
    Sheet sheet = excelService.createSheet(workbook, "요약");
    CellStyle headerStyle = excelService.createHeaderStyle(workbook, 11, true, 200, 220, 240);
    CellStyle dataStyle = excelService.createBorderedStyle(workbook);

    int rowNum = 0;

    // 행사 정보
    createLabelValueRow(sheet, rowNum++, "행사명", event.getEventName(), headerStyle, dataStyle);
    createLabelValueRow(
        sheet, rowNum++, "생성일", LocalDateTime.now().format(DATE_FORMATTER), headerStyle, dataStyle);
    rowNum++; // 빈 행

    // 참가자 현황
    createLabelValueRow(
        sheet,
        rowNum++,
        "전체 참가자",
        String.valueOf(statistics.getParticipantSummary().getTotalCount()),
        headerStyle,
        dataStyle);
    createLabelValueRow(
        sheet,
        rowNum++,
        "체크인 완료",
        String.valueOf(statistics.getParticipantSummary().getCheckedInCount()),
        headerStyle,
        dataStyle);
    createLabelValueRow(
        sheet,
        rowNum++,
        "미체크인",
        String.valueOf(statistics.getParticipantSummary().getNotCheckedInCount()),
        headerStyle,
        dataStyle);
    createLabelValueRow(
        sheet,
        rowNum++,
        "체크인율",
        statistics.getParticipantSummary().getCheckedInRate() + "%",
        headerStyle,
        dataStyle);
    rowNum++;

    // 명찰 현황
    createLabelValueRow(
        sheet,
        rowNum++,
        "명찰 출력 완료",
        String.valueOf(statistics.getNametagSummary().getPrintedCount()),
        headerStyle,
        dataStyle);
    createLabelValueRow(
        sheet,
        rowNum++,
        "명찰 미출력",
        String.valueOf(statistics.getNametagSummary().getNotPrintedCount()),
        headerStyle,
        dataStyle);
    createLabelValueRow(
        sheet,
        rowNum++,
        "출력율",
        statistics.getNametagSummary().getPrintRate() + "%",
        headerStyle,
        dataStyle);

    sheet.setColumnWidth(0, 5000);
    sheet.setColumnWidth(1, 8000);
  }

  private void createActionStatsSheet(
      SXSSFWorkbook workbook, kr.wisead.domain.event.dto.EventStatisticsResponse statistics) {
    Sheet sheet = excelService.createSheet(workbook, "액션별 통계");
    CellStyle headerStyle = excelService.createHeaderStyle(workbook, 11, true, 200, 220, 240);
    CellStyle dataStyle = excelService.createBorderedStyle(workbook);

    // 헤더
    List<String> headers = Arrays.asList("액션 코드", "액션명", "완료 수", "전체 수", "완료율");
    excelService.createHeaderRow(sheet, 0, headers, headerStyle);

    // 데이터
    int rowNum = 1;
    for (var action : statistics.getActionStatistics()) {
      List<Object> rowData =
          Arrays.asList(
              action.getActionCode(),
              action.getActionName(),
              action.getCompletedCount(),
              action.getTotalCount(),
              action.getCompletionRate() + "%");
      excelService.createDataRow(sheet, rowNum++, rowData, dataStyle);
    }

    for (int i = 0; i < headers.size(); i++) {
      sheet.setColumnWidth(i, 4000);
    }
  }

  private void createParticipantTypeStatsSheet(
      SXSSFWorkbook workbook, kr.wisead.domain.event.dto.EventStatisticsResponse statistics) {
    Sheet sheet = excelService.createSheet(workbook, "참가자 유형별");
    CellStyle headerStyle = excelService.createHeaderStyle(workbook, 11, true, 200, 220, 240);
    CellStyle dataStyle = excelService.createBorderedStyle(workbook);

    // 헤더
    List<String> headers = Arrays.asList("참가자 유형", "인원 수", "체크인 수");
    excelService.createHeaderRow(sheet, 0, headers, headerStyle);

    // 데이터
    int rowNum = 1;
    for (var typeStat : statistics.getParticipantSummary().getByType()) {
      List<Object> rowData =
          Arrays.asList(
              typeStat.getParticipantType(), typeStat.getCount(), typeStat.getCheckedInCount());
      excelService.createDataRow(sheet, rowNum++, rowData, dataStyle);
    }

    for (int i = 0; i < headers.size(); i++) {
      sheet.setColumnWidth(i, 4000);
    }
  }

  private void createLabelValueRow(
      Sheet sheet,
      int rowNum,
      String label,
      String value,
      CellStyle labelStyle,
      CellStyle valueStyle) {
    Row row = sheet.createRow(rowNum);
    Cell labelCell = row.createCell(0);
    labelCell.setCellValue(label);
    labelCell.setCellStyle(labelStyle);

    Cell valueCell = row.createCell(1);
    valueCell.setCellValue(value);
    valueCell.setCellStyle(valueStyle);
  }

  private int getColumnWidth(int columnIndex) {
    switch (columnIndex) {
      case 0:
        return 1500; // 번호
      case 1:
        return 3500; // 이름
      case 2:
        return 4500; // 연락처
      case 3:
        return 7000; // 이메일
      case 4:
        return 5000; // 소속
      case 5:
        return 4000; // 직책
      case 6:
        return 4000; // 참가자 유형
      case 7:
        return 9000; // 체크코드
      case 8:
        return 4000; // 등록구분
      case 9:
        return 3500; // 명찰 출력
      case 10:
        return 10000; // 액션 현황
      case 11:
        return 6000; // 메모
      case 12:
        return 5000; // 등록일
      default:
        return 3000;
    }
  }
}
