package kr.wisead.domain.admin.service;

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

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 관리자 서비스 (리팩토링 버전)
 * - WalletService 사용
 */
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

    /**
     * 관리자 계정 생성
     * - 회원 등록
     * - 지갑 초기화
     * - 서비스 단가 등록
     */
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
        User user = User.builder()
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
                .status("승인")  // 관리자 계정은 자동 승인
                .regId(creatorId)
                .loginFailureCnt(0)
                .build();

        // 회원 등록
        userMapper.insert(user);
        log.info("관리자 계정 생성: userId={}, level={}", request.getUserId(), request.getUserLevel());

        // 지갑 초기화
        walletService.initializeWallet(request.getUserId());
        log.info("지갑 초기화 완료: userId={}", request.getUserId());

        // 서비스 단가 등록 (standard_rate 기준)
        initializeUserServiceRates(request.getUserId());
        log.info("서비스 단가 등록 완료: userId={}", request.getUserId());

        // 비밀번호 힌트 등록
        registerPasswordHint(user.getSeq(), request.getHintQuestion(), request.getHintAnswer());

        return getUserResponse(user);
    }

    /**
     * 관리자 권한 확인
     */
    public boolean isAdmin(String userId) {
        return userMapper.findByUserId(userId)
                .map(User::isAdmin)
                .orElse(false);
    }

    /**
     * 최고 관리자 권한 확인 (레벨 99)
     */
    public boolean isSuperAdmin(String userId) {
        return userMapper.findByUserId(userId)
                .map(user -> user.getUserLevel() != null && user.getUserLevel() >= 99)
                .orElse(false);
    }

    /**
     * 사용자 권한 레벨 조회
     */
    public Integer getUserLevel(String userId) {
        return userMapper.findByUserId(userId)
                .map(User::getUserLevel)
                .orElse(0);
    }

    /**
     * 권한 레벨 명칭 조회
     */
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
     * 권한별 조회 대상 사용자 ID 결정
     * - 레벨 90 이상: "ALL" (전체 조회)
     * - 레벨 50-89: 본인 + 관리하는 계정들 (콤마 구분)
     * - 레벨 50 미만: 본인만
     *
     * @param userId    현재 로그인한 사용자 ID
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
            java.util.List<String> managedUserIds = customerCompanyMapper.selectManagedUserIds(userId);

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

    // ==================== Private Methods ====================

    /**
     * 비밀번호 힌트 등록
     */
    private void registerPasswordHint(Long mngSeq, String hintQuestion, String hintAnswer) {
        if (hintQuestion == null || hintQuestion.isBlank() ||
            hintAnswer == null || hintAnswer.isBlank()) {
            log.debug("비밀번호 힌트 미입력: mngSeq={}", mngSeq);
            return;
        }

        PasswordHint passwordHint = PasswordHint.builder()
                .mngSeq(mngSeq)
                .hintQuestion(hintQuestion)
                .hintAnswer(hintAnswer)
                .build();

        passwordHintMapper.insert(passwordHint);
        log.info("비밀번호 힌트 등록 완료: mngSeq={}", mngSeq);
    }

    private void initializeUserServiceRates(String userId) {
        LocalDate today = LocalDate.now();

        BigDecimal surveyRate = standardRateService.getStandardRateWithVat("survey");
        BigDecimal smsRate = standardRateService.getStandardRateWithVat("msg_sms");
        BigDecimal lmsRate = standardRateService.getStandardRateWithVat("msg_lms");
        BigDecimal mmsRate = standardRateService.getStandardRateWithVat("msg_mms");
        BigDecimal qrRate = standardRateService.getStandardRateWithVat("qr_code");

        userServiceRateMapper.insert(UserServiceRate.create(userId, "survey", surveyRate, today));
        userServiceRateMapper.insert(UserServiceRate.create(userId, "msg_sms", smsRate, today));
        userServiceRateMapper.insert(UserServiceRate.create(userId, "msg_lms", lmsRate, today));
        userServiceRateMapper.insert(UserServiceRate.create(userId, "msg_mms", mmsRate, today));
        userServiceRateMapper.insert(UserServiceRate.create(userId, "qr_code", qrRate, today));
    }

    private UserResponse getUserResponse(User user) {
        // UserResponse.from()을 사용하여 개인정보 복호화 포함
        UserResponse response = UserResponse.from(user);
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
                .build();
    }
}