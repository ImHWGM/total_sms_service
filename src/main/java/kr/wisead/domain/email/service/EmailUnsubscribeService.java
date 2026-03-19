package kr.wisead.domain.email.service;

import java.util.HashMap;
import java.util.Map;
import kr.wisead.common.util.CommonUtils;
import kr.wisead.domain.email.entity.EmailUnsubscribe;
import kr.wisead.mapper.primary.EmailUnsubscribeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 이메일 수신거부 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailUnsubscribeService {

  private final EmailUnsubscribeMapper emailUnsubscribeMapper;

  /**
   * 이메일 수신거부 등록
   *
   * @param email 수신거부할 이메일 주소
   * @return 처리 결과
   */
  @Transactional
  public Map<String, String> unsubscribe(String email) {
    Map<String, String> response = new HashMap<>();

    try {
      if (email == null || email.trim().isEmpty()) {
        response.put("status", "error");
        response.put("message", "이메일 주소가 비어있습니다.");
        return response;
      }

      // 이메일 형식 검증
      if (!isValidEmail(email)) {
        response.put("status", "error");
        response.put("message", "유효하지 않은 이메일 형식입니다.");
        return response;
      }

      EmailUnsubscribe unsubscribe =
          EmailUnsubscribe.builder().eEmail(email.trim().toLowerCase()).build();

      emailUnsubscribeMapper.insert(unsubscribe);

      log.info("이메일 수신거부 등록 완료: {}", CommonUtils.maskingEmail(email));
      response.put("status", "success");
      response.put("message", "You have successfully unsubscribed.");

    } catch (Exception e) {
      log.error("이메일 수신거부 처리 중 오류 발생: {}", e.getMessage());
      response.put("status", "error");
      response.put("message", "There was an error processing your request.");
    }

    return response;
  }

  /**
   * 수신거부 여부 확인
   *
   * @param email 이메일 주소
   * @return 수신거부 여부
   */
  @Transactional(readOnly = true)
  public boolean isUnsubscribed(String email) {
    if (email == null || email.trim().isEmpty()) {
      return false;
    }
    return emailUnsubscribeMapper.existsByEmail(email.trim().toLowerCase());
  }

  /** 이메일 형식 검증 */
  private boolean isValidEmail(String email) {
    String emailRegex =
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
    return email.matches(emailRegex);
  }
}
