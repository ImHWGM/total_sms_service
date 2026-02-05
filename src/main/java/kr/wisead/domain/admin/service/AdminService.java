package kr.wisead.domain.admin.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.admin.dto.AdminAccountRequest;
import kr.wisead.domain.payment.entity.UserServiceRate;
import kr.wisead.domain.payment.service.StandardRateService;
import kr.wisead.domain.payment.service.WalletService;
import kr.wisead.domain.user.dto.UserResponse;
import kr.wisead.domain.user.entity.PasswordHint;
import kr.wisead.domain.user.entity.User;
import kr.wisead.mapper.primary.CustomerCompanyMapper;
import kr.wisead.mapper.primary.PasswordHintMapper;
import kr.wisead.mapper.primary.UserMapper;
import kr.wisead.mapper.primary.UserServiceRateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 서비스 (리팩토링 버전) - WalletService 사용 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

  private final UserMapper userMapper;
  private final WalletService walletService;
  private final UserServiceRateMapper userServiceRateMapper;
  private final StandardRateService standardRateService;
  private final PasswordHintMapper passwordHintMapper;
  private final CustomerCompanyMapper customerCompanyMapper;
  private final PasswordEncoder passwordEncoder;

  /** 관리자 계정 생성 - 회원 등록 - 지갑 초기화 - 서비스 단가 등록 */
  @Transactional
  public UserResponse createAdminAccount(AdminAccountRequest request, String creatorId) {
    // 비밀번호 확인
    if (!request.getUserPass().equals(request.getUserPassChk())) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호와 비밀번호 확인이 동일하지 않습니다.");
    }

    // 아이디 중복 확인
    if (userMapper.existsByUserId(request.getUserId())) {
      throw new BusinessException(ErrorCode.DUPLICATE_USER_ID);
    }

    // 비밀번호 암호화
    String encodedPassword = passwordEncoder.encode(request.getUserPass());

    // 개인정보 암호화 처리 (person, phone, email)
    String encryptedPerson = null;
    String encryptedPhone = null;
    String encryptedEmail = null;

    try {
      if (request.getPerson() != null && !request.getPerson().isBlank()) {
        encryptedPerson = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(request.getPerson()));
      }
      if (request.getPhone() != null && !request.getPhone().isBlank()) {
        String phone = request.getPhone().replace("-", "");
        encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(phone));
      }
      if (request.getEmail() != null && !request.getEmail().isBlank()) {
        encryptedEmail = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(request.getEmail()));
      }
    } catch (Exception e) {
      log.error("개인정보 암호화 실패: {}", e.getMessage());
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "개인정보 암호화에 실패했습니다.");
    }

    // 회원 정보 생성
    User user =
        User.builder()
            .userId(request.getUserId())
            .userPass(encodedPassword)
            .corpName(request.getCorpName())
            .corpAddr(request.getCorpAddr())
            .bizNum(request.getBizNum())
            .bizTel(request.getBizTel())
            .person(encryptedPerson)
            .phone(encryptedPhone)
            .email(encryptedEmail)
            .userLevel(request.getUserLevel())
            .useYn("Y")
            .allowIpYn("Y")
            .status("승인") // 관리자 계정은 자동 승인
            .regId(creatorId)
            .loginFailureCnt(0)
            .build();

    // 회원 등록 (INSERT 후 user.seq에 자동 생성된 키가 주입됨)
    userMapper.insert(user);
    Integer userSeq = user.getSeq();
    log.info(
        "관리자 계정 생성: userId={}, userSeq={}, level={}",
        request.getUserId(),
        userSeq,
        request.getUserLevel());

    // 지갑 초기화 (userSeq 사용)
    walletService.initializeWallet(userSeq);
    log.info("지갑 초기화 완료: userSeq={}", userSeq);

    // 서비스 단가 등록 (standard_rate 기준, userSeq 사용)
    initializeUserServiceRates(userSeq);
    log.info("서비스 단가 등록 완료: userSeq={}", userSeq);

    // 비밀번호 힌트 등록
    registerPasswordHint(user.getSeq(), request.getHintQuestion(), request.getHintAnswer());

    return getUserResponse(user);
  }

  /** 관리자 권한 확인 */
  public boolean isAdmin(String userId) {
    return userMapper.findByUserId(userId).map(User::isAdmin).orElse(false);
  }

  /** 최고 관리자 권한 확인 (레벨 99) */
  public boolean isSuperAdmin(String userId) {
    return userMapper
        .findByUserId(userId)
        .map(user -> user.getUserLevel() != null && user.getUserLevel() >= 99)
        .orElse(false);
  }

  /**
   * 사용자 권한 레벨 조회
   *
   * @param userSeq JWT에서 추출한 값 (seq)
   */
  public Integer getUserLevel(String userSeq) {
    // 숫자인 경우 seq로 조회, 아닌 경우 userId로 조회
    try {
      Integer seq = Integer.parseInt(userSeq);
      return userMapper.findBySeq(seq).map(User::getUserLevel).orElse(0);
    } catch (NumberFormatException e) {
      return userMapper.findByUserId(userSeq).map(User::getUserLevel).orElse(0);
    }
  }

  /** 권한 레벨 명칭 조회 */
  public String getUserLevelName(Integer level) {
    if (level == null) return "알 수 없음";

    return switch (level) {
      case 10 -> "기업관리자";
      case 50 -> "운영관리자(B)";
      case 60 -> "운영관리자(A)";
      case 90 -> "최고관리자(B)";
      case 99 -> "최고관리자(A)";
      default -> "일반사용자";
    };
  }

  /**
   * A 레벨 여부 확인 (전체 쓰기 권한) - 99: 최고관리자(A) - 전체 데이터 수정/삭제 가능 - 60: 운영관리자(A) - 관리 범위 내 데이터 수정/삭제 가능
   */
  public boolean isLevelA(Integer userLevel) {
    return userLevel != null && (userLevel == 99 || userLevel == 60);
  }

  /**
   * B 레벨 여부 확인 (본인 데이터만 쓰기 권한) - 90: 최고관리자(B) - 전체 조회 가능, 본인 데이터만 수정/삭제 - 50: 운영관리자(B) - 관리 범위 조회
   * 가능, 본인 데이터만 수정/삭제 - 10: 기업관리자 - 본인 데이터만 조회/수정/삭제
   */
  public boolean isLevelB(Integer userLevel) {
    return userLevel != null && (userLevel == 90 || userLevel == 50 || userLevel == 10);
  }

  /**
   * 대상 데이터에 대한 수정/삭제 권한 확인
   *
   * @param currentUserId 현재 로그인한 사용자 ID
   * @param currentLevel 현재 사용자 권한 레벨
   * @param targetOwnerId 대상 데이터 소유자 ID
   * @return 수정/삭제 가능 여부
   */
  public boolean canModify(String currentUserId, Integer currentLevel, String targetOwnerId) {
    if (currentUserId == null || currentLevel == null || targetOwnerId == null) {
      return false;
    }

    // 본인 데이터는 항상 수정 가능
    if (currentUserId.equals(targetOwnerId)) {
      return true;
    }

    // A 레벨은 조회 가능 범위 내 데이터 수정 가능
    if (isLevelA(currentLevel)) {
      // 최고관리자(A) - 전체 수정 가능
      if (currentLevel == 99) {
        return true;
      }
      // 운영관리자(A) - 관리 계정 데이터만 수정 가능
      if (currentLevel == 60) {
        List<String> managedUserIds = customerCompanyMapper.selectManagedUserIds(currentUserId);
        return managedUserIds != null && managedUserIds.contains(targetOwnerId);
      }
    }

    // B 레벨은 본인 데이터만 수정 가능 (위에서 이미 체크됨)
    return false;
  }

  /** 수정/삭제 권한 검증 (권한 없으면 예외 발생) */
  public void validateModifyPermission(
      String currentUserId, Integer currentLevel, String targetOwnerId) {
    if (!canModify(currentUserId, currentLevel, targetOwnerId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED, "해당 데이터를 수정/삭제할 권한이 없습니다.");
    }
  }

  /**
   * 권한별 조회 대상 사용자 ID 결정 - 레벨 90 이상: "ALL" (전체 조회) - 레벨 50-89: 본인 + 관리하는 계정들 (콤마 구분) - 레벨 50 미만: 본인만
   *
   * @param userId 현재 로그인한 사용자 ID
   * @param userLevel 사용자 권한 레벨
   * @return 조회 대상 사용자 ID (단일 ID, 콤마 구분 목록, 또는 "ALL")
   */
  public String determineQueryUserIds(String userId, Integer userLevel) {
    if (userLevel == null) return userId;

    if (userLevel >= 90) {
      // 90 이상: 모든 데이터 조회
      log.info("권한 레벨 {}로 모든 데이터 조회 허용: userId={}", userLevel, userId);
      return "ALL";
    } else if (userLevel >= 50) {
      // 50-89: 관리하는 계정들 조회
      List<String> managedUserIds = customerCompanyMapper.selectManagedUserIds(userId);

      if (managedUserIds == null || managedUserIds.isEmpty()) {
        log.info("권한 레벨 {} - 관리 계정 없음, 본인만 조회: {}", userLevel, userId);
        return userId;
      }

      // 본인 ID 추가
      if (!managedUserIds.contains(userId)) {
        managedUserIds.add(userId);
      }

      String result = String.join(",", managedUserIds);
      log.info("권한 레벨 {} - 관리 계정 조회: userId={}, 조회대상={}", userLevel, userId, result);
      return result;
    } else {
      // 50 미만: 본인만
      log.info("권한 레벨 {}로 본인만 조회: {}", userLevel, userId);
      return userId;
    }
  }

  /** SEQ로 USER_ID 조회 */
  public String getUserIdBySeq(String userSeq) {
    try {
      Integer seq = Integer.parseInt(userSeq);
      return userMapper.findUserIdBySeq(seq);
    } catch (NumberFormatException e) {
      // userSeq가 이미 userId인 경우
      return userSeq;
    }
  }

  /**
   * 권한별 선택 가능한 사용자 아이디 목록 조회
   *
   * @param userId 현재 로그인 사용자 ID
   * @param userLevel 사용자 권한 레벨
   * @return 선택 가능한 사용자 아이디 목록
   */
  public List<String> getSelectableUserIds(String userId, Integer userLevel) {
    if (userLevel == null) {
      log.warn("userLevel이 null입니다. userId: {}", userId);
      return java.util.Collections.singletonList(userId);
    }

    if (userLevel >= 90) {
      // 최고관리자 (90, 99): 전체 사용자 목록
      log.info("최고관리자 권한으로 전체 사용자 목록 조회: userId={}, level={}", userId, userLevel);
      return userMapper.findAllUserIds();
    } else if (userLevel >= 50) {
      // 운영관리자 (50, 60): 본인 + 관리 계정
      List<String> managedUserIds = customerCompanyMapper.selectManagedUserIds(userId);

      // 본인 아이디 추가
      if (managedUserIds == null) {
        managedUserIds = new java.util.ArrayList<>();
      } else {
        managedUserIds = new java.util.ArrayList<>(managedUserIds); // 불변 리스트 방지
      }

      if (!managedUserIds.contains(userId)) {
        managedUserIds.add(0, userId); // 본인 아이디를 맨 앞에 추가
      }

      log.info(
          "운영관리자 권한으로 관리 계정 조회: userId={}, level={}, count={}",
          userId,
          userLevel,
          managedUserIds.size());

      return managedUserIds.stream()
          .distinct()
          .sorted()
          .collect(java.util.stream.Collectors.toList());
    } else {
      // 기업 (10) 및 기타: 본인만
      log.info("기업 권한으로 본인만 조회: userId={}, level={}", userId, userLevel);
      return java.util.Collections.singletonList(userId);
    }
  }

  // ==================== Private Methods ====================

  /** 비밀번호 힌트 등록 */
  private void registerPasswordHint(Integer seq, String hintQuestion, String hintAnswer) {
    if (hintQuestion == null
        || hintQuestion.isBlank()
        || hintAnswer == null
        || hintAnswer.isBlank()) {
      log.debug("비밀번호 힌트 미입력: seq={}", seq);
      return;
    }

    PasswordHint passwordHint =
        PasswordHint.builder()
            .userSeq(seq)
            .hintQuestion(hintQuestion)
            .hintAnswer(hintAnswer)
            .build();

    passwordHintMapper.insert(passwordHint);
    log.info("비밀번호 힌트 등록 완료: seq={}", seq);
  }

  private void initializeUserServiceRates(Integer userSeq) {
    LocalDate today = LocalDate.now();

    BigDecimal surveyRate = standardRateService.getStandardRateWithVat("survey");
    BigDecimal smsRate = standardRateService.getStandardRateWithVat("msg_sms");
    BigDecimal lmsRate = standardRateService.getStandardRateWithVat("msg_lms");
    BigDecimal mmsRate = standardRateService.getStandardRateWithVat("msg_mms");
    BigDecimal qrRate = standardRateService.getStandardRateWithVat("qr_code");

    userServiceRateMapper.insert(UserServiceRate.create(userSeq, "survey", surveyRate, today));
    userServiceRateMapper.insert(UserServiceRate.create(userSeq, "msg_sms", smsRate, today));
    userServiceRateMapper.insert(UserServiceRate.create(userSeq, "msg_lms", lmsRate, today));
    userServiceRateMapper.insert(UserServiceRate.create(userSeq, "msg_mms", mmsRate, today));
    userServiceRateMapper.insert(UserServiceRate.create(userSeq, "qr_code", qrRate, today));
  }

  private UserResponse getUserResponse(User user) {
    // UserResponse.from()을 사용하여 개인정보 복호화 포함
    UserResponse response = UserResponse.from(user);

    // 사용 가능 금액 조회
    BigDecimal availableBalance = walletService.getWalletSummary(user.getSeq()).getTotal();

    // userLevelName 추가를 위해 builder 재구성
    return UserResponse.builder()
        .seq(response.getSeq())
        .userId(response.getUserId())
        .corpName(response.getCorpName())
        .corpAddr(response.getCorpAddr())
        .bizNum(response.getBizNum())
        .bizTel(response.getBizTel())
        .person(response.getPerson())
        .phone(response.getPhone())
        .email(response.getEmail())
        .userLevel(response.getUserLevel())
        .userLevelName(getUserLevelName(user.getUserLevel()))
        .useYn(response.getUseYn())
        .status(response.getStatus())
        .regDate(response.getRegDate())
        .availableBalance(availableBalance)
        .build();
  }
}
