package kr.wisead.domain.survey.controller;

import jakarta.validation.Valid;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.survey.dto.*;
import kr.wisead.domain.survey.service.FrontAuthService;
import kr.wisead.domain.survey.service.KcpAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** 설문 참여자 인증 Controller (프론트) - QR 사용자 자동 생성 - 휴대폰 번호 검증 - 가상 키패드 */
@Slf4j
@RestController
@RequestMapping("/api/front/auth")
@RequiredArgsConstructor
public class FrontAuthController {

  private final FrontAuthService frontAuthService;
  private final KcpAuthService kcpAuthService;

  /** QR 코드 접근 시 사용자 생성 - 인증이 필요 없는 설문에서 QR 접근 시 사용자 자동 생성 */
  @PostMapping("/qr/user")
  public ApiResponse<SurveyUserResponse> createQrUser(@RequestParam String authCodeUrl) {
    SurveyUserResponse response = frontAuthService.createQrUser(authCodeUrl);
    return ApiResponse.success(response);
  }

  /** 이벤트 코드로 사용자 생성 - NA(무인증) 설문에서 Access Link 접근 시 익명 사용자 자동 생성 */
  @PostMapping("/event/user")
  public ApiResponse<SurveyUserResponse> createEventUser(@RequestParam String eventCode) {
    SurveyUserResponse response = frontAuthService.createEventUser(eventCode);
    return ApiResponse.success(response);
  }

  /** 휴대폰 번호로 사용자 검증 - 재발송 시나리오에서 휴대폰 번호로 기존 사용자 조회 */
  @PostMapping("/validate/phone")
  public ApiResponse<SurveyUserResponse> validatePhone(
      @Valid @RequestBody PhoneValidationRequest request) {
    SurveyUserResponse response = frontAuthService.validatePhone(request);
    return ApiResponse.success(response);
  }

  /** 휴대폰 번호 존재 여부 확인 */
  @GetMapping("/check/phone")
  public ApiResponse<Boolean> checkPhoneExists(
      @RequestParam String eventCode, @RequestParam String phone) {
    boolean exists = frontAuthService.checkPhoneExists(eventCode, phone);
    return ApiResponse.success(exists);
  }

  /** 가상 키패드 데이터 생성 - 주민번호 등 민감정보 입력 시 RSA 암호화용 공개키 발급 */
  @GetMapping("/keypad")
  public ApiResponse<KeypadResponse> getKeypadData() {
    KeypadResponse response = frontAuthService.generateKeypad();
    return ApiResponse.success(response);
  }

  /** 가상 키패드 입력값 복호화 (테스트용) - 실제로는 서버 내부에서 처리하므로 필요 시에만 사용 */
  @PostMapping("/keypad/decrypt")
  public ApiResponse<String> decryptKeypadInput(
      @RequestParam String keypadId, @RequestParam String encryptedData) {
    String decrypted = frontAuthService.decryptKeypadInput(keypadId, encryptedData);
    return ApiResponse.<String>builder()
        .success(true)
        .code("SUCCESS")
        .message("복호화 완료")
        .data(decrypted)
        .build();
  }

  /**
   * 가상 키패드 입력 주민번호 즉시 변환 - 키패드 RSA ciphertext를 받아 즉시 RSA 복호화 후 AES256+Base64 ciphertext로 변환하여 반환.
   * FE는 응답 ciphertext만 보관 후 설문 제출 시 answer 필드에 첨부한다 (keypad TTL과 설문 제출 시점 분리).
   */
  @PostMapping("/jumin/encrypt")
  public ApiResponse<JuminEncryptResponse> encryptJumin(
      @Valid @RequestBody JuminEncryptRequest request) {
    JuminEncryptResponse response = frontAuthService.encryptJumin(request);
    return ApiResponse.success(response);
  }

  // ==================== KCP 실명인증 ====================

  /** KCP 인증 시작 데이터 생성 - 모바일 앱에서 WebView로 KCP 인증 페이지 호출 전 필요한 데이터 생성 */
  @GetMapping("/kcp/init")
  public ApiResponse<KcpAuthResponse> initKcpAuth(
      @RequestParam(required = false) String eventCode,
      @RequestParam(required = false) String userKey) {
    KcpAuthResponse response = kcpAuthService.generateAuthData(eventCode, userKey);
    return ApiResponse.success(response);
  }

  /** KCP 인증 결과 처리 - KCP 인증 완료 후 암호화된 데이터를 복호화하여 사용자 정보 반환 */
  @PostMapping("/kcp/result")
  public ApiResponse<KcpAuthResponse> processKcpAuthResult(@RequestBody KcpAuthRequest request) {
    KcpAuthResponse response = kcpAuthService.processAuthResult(request);
    return ApiResponse.success(response);
  }

  /** KCP 인증 결과로 설문 사용자 조회 - KCP 인증 완료 후 전화번호로 기존 사용자 조회 */
  @GetMapping("/kcp/user")
  public ApiResponse<SurveyUserResponse> findUserByKcpAuth(
      @RequestParam String eventCode, @RequestParam String phoneNo) {
    SurveyUserResponse response = kcpAuthService.findUserByKcpAuth(eventCode, phoneNo);
    return ApiResponse.success(response);
  }

  /** KCP 인증 콜백 (팝업 방식) - KCP 서버에서 인증 완료 후 호출 - form-urlencoded로 수신, HTML로 응답 (postMessage 포함) */
  @PostMapping(
      value = "/kcp/callback",
      consumes = org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE,
      produces = org.springframework.http.MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public String processKcpCallback(
      @RequestParam("res_cd") String resCd,
      @RequestParam("res_msg") String resMsg,
      @RequestParam(value = "cert_no", required = false) String certNo,
      @RequestParam(value = "enc_cert_data2", required = false) String encCertData2,
      @RequestParam(value = "dn_hash", required = false) String dnHash,
      @RequestParam(value = "ordr_idxx", required = false) String ordrIdxx) {
    return kcpAuthService.processCallback(resCd, resMsg, certNo, encCertData2, dnHash, ordrIdxx);
  }
}
