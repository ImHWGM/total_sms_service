package kr.wisead.domain.survey.entity;

/**
 * 설문 객관식 문항의 "기타" 항목 답변 유형 (plan §3 Phase B-1, AC-13).
 *
 * <p>관리자가 항목 생성 시 지정하며, 응답자는 해당 유형의 입력 위젯/검증을 적용받는다. SO는 RSA 키패드 + AES256 암호화 흐름을 따른다.
 */
public enum OtherType {
  /** 주관식 (default) */
  SA,
  /** 이름 */
  NE,
  /** 주민번호 (RSA + AES256) */
  SO,
  /** 이메일 */
  EM,
  /** 주소 */
  AD,
  /** 커스텀 */
  CU
}
