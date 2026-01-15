package kr.wisead.domain.user.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.common.util.PasswordValidator;
import kr.wisead.domain.email.service.EmailAuthService;
import kr.wisead.domain.email.service.EmailService;
import kr.wisead.domain.user.dto.FindIdRequest;
import kr.wisead.domain.user.dto.FindIdResponse;
import kr.wisead.domain.user.dto.FindPasswordRequest;
import kr.wisead.domain.user.dto.FindPasswordResponse;
import kr.wisead.domain.user.dto.UserResponse;
import kr.wisead.domain.user.entity.PasswordResetToken;
import kr.wisead.domain.user.entity.User;
import kr.wisead.mapper.primary.PasswordHintMapper;
import kr.wisead.mapper.primary.PasswordResetTokenMapper;
import kr.wisead.mapper.primary.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 회원 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordHintMapper passwordHintMapper;
    private final PasswordResetTokenMapper passwordResetTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailAuthService emailAuthService;
    private final EmailService emailService;

    @Value("${wisead.base-url:http://localhost:3000}")
    private String baseUrl;

    // 아이디 찾기용 임시 저장소 (이메일 -> User 정보)
    private final Map<String, User> findIdTempStore = new ConcurrentHashMap<>();

    // 토큰 유효 시간 (10분)
    private static final int TOKEN_EXPIRATION_MINUTES = 10;

    /**
     * 회원 정보 조회 (by SEQ)
     */
    @Transactional(readOnly = true)
    public UserResponse getUserBySeq(Long seq) {
        User user = userMapper.findBySeq(seq)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return UserResponse.from(user);
    }

    /**
     * 회원 정보 조회 (by USER_ID)
     */
    @Transactional(readOnly = true)
    public UserResponse getUserByUserId(String userId) {
        User user = userMapper.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return UserResponse.from(user);
    }

    /**
     * 회원 목록 조회 (페이징)
     */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsers(int page, int size) {
        int offset = page * size;
        List<User> users = userMapper.findAll(offset, size);
        long total = userMapper.count();

        List<UserResponse> content = users.stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());

        return PageResponse.of(content, page, size, total);
    }

    /**
     * 비밀번호 변경
     */
    @Transactional
    public void changePassword(String userId, String currentPassword, String newPassword) {
        // 1. 사용자 조회
        User user = userMapper.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 2. 현재 비밀번호 확인
        if (!passwordEncoder.matches(currentPassword, user.getUserPass())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD, "현재 비밀번호가 일치하지 않습니다.");
        }

        // 3. 새 비밀번호 복잡도 검증
        PasswordValidator.validate(newPassword);

        // 4. 현재 비밀번호와 동일한지 확인
        if (passwordEncoder.matches(newPassword, user.getUserPass())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }

        // 5. 비밀번호 변경
        String encodedPassword = passwordEncoder.encode(newPassword);
        userMapper.updatePassword(userId, encodedPassword);

        log.info("비밀번호 변경 완료: userId={}", userId);
    }

    /**
     * 회원 상태 변경 (관리자)
     */
    @Transactional
    public void updateStatus(String userId, String status) {
        // 유효한 상태인지 확인
        if (!isValidStatus(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 상태값입니다.");
        }

        // 사용자 존재 확인
        if (!userMapper.existsByUserId(userId)) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        userMapper.updateStatus(userId, status);
        log.info("회원 상태 변경: userId={}, status={}", userId, status);
    }

    /**
     * 아이디 찾기 1단계 - 정보 검증 및 이메일 인증코드 발송
     * 
     * @param request 아이디 찾기 요청 (기업명, 담당자명, 연락처)
     * @return 마스킹된 이메일 정보
     */
    @Transactional(readOnly = true)
    public FindIdResponse requestFindId(FindIdRequest request) {
        try {
            // 1. 평문 담당자명, 연락처를 암호화 (DB 저장 형식에 맞게)
            String encryptedPerson = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(request.getPerson()));
            String encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(request.getPhone()));

            // 2. 회원 정보 조회
            User user = userMapper.findByCorpNameAndPersonAndPhone(
                    request.getCorpName(),
                    encryptedPerson,
                    encryptedPhone).orElse(null);

            if (user == null) {
                log.warn("아이디 찾기 실패 - 계정 정보 없음: corpName={}", request.getCorpName());
                return FindIdResponse.accountNotFound();
            }

            // 3. 이메일로 인증코드 발송
            String email = user.getEmail();
            emailAuthService.sendVerificationCode(email);

            // 4. 임시 저장소에 사용자 정보 저장 (인증 완료 후 아이디 조회용)
            findIdTempStore.put(email.toLowerCase(), user);

            // 5. 마스킹된 이메일 반환
            String maskedEmail = maskEmail(email);
            log.info("아이디 찾기 인증코드 발송: email={}", maskedEmail);

            return FindIdResponse.requestSuccess(maskedEmail);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("아이디 찾기 중 오류 발생: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "아이디 찾기 처리 중 오류가 발생했습니다.");
        }
    }

    /**
     * 아이디 찾기 2단계 - 인증코드 검증 및 아이디 반환
     * 
     * @param email 이메일
     * @param code  인증코드
     * @return 마스킹된 아이디
     */
    @Transactional(readOnly = true)
    public FindIdResponse verifyAndGetUserId(String email, String code) {
        try {
            // 1. 인증코드 검증
            boolean verified = emailAuthService.verifyCode(email, code);
            if (!verified) {
                return FindIdResponse.verificationFailed();
            }

            // 2. 임시 저장소에서 사용자 정보 조회
            User user = findIdTempStore.remove(email.toLowerCase());
            if (user == null) {
                // 임시 저장소에 없으면 DB에서 직접 조회
                user = userMapper.findByEmail(email)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
            }

            // 3. 마스킹된 아이디 반환
            String maskedUserId = maskUserId(user.getUserId());
            log.info("아이디 찾기 완료: maskedUserId={}", maskedUserId);

            return FindIdResponse.verifySuccess(maskedUserId);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("아이디 찾기 인증 중 오류 발생: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "아이디 찾기 처리 중 오류가 발생했습니다.");
        }
    }

    /**
     * 아이디 찾기 (기존 방식 - 하위 호환용)
     */
    @Transactional(readOnly = true)
    public String findUserId(String email, String person) {
        User user = userMapper.findByEmailAndPerson(email, person)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "일치하는 회원 정보가 없습니다."));

        return maskUserId(user.getUserId());
    }

    /**
     * 유효한 상태값인지 확인
     */
    private boolean isValidStatus(String status) {
        return "미승인".equals(status) ||
                "승인".equals(status) ||
                "보류".equals(status) ||
                "탈퇴".equals(status);
    }

    /**
     * 계정 잠금 해제 (관리자)
     */
    @Transactional
    public int unlockAccount(String userId) {
        if (!userMapper.existsByUserId(userId)) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        int result = userMapper.unlockAccount(userId);
        log.info("계정 잠금 해제: userId={}", userId);
        return result;
    }

    /**
     * 비밀번호 만료일 연장 (180일)
     */
    @Transactional
    public int extendPasswordExpiry(String userId) {
        if (!userMapper.existsByUserId(userId)) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        int result = userMapper.extendPasswordExpiry(userId);
        log.info("비밀번호 만료일 연장: userId={}", userId);
        return result;
    }

    /**
     * 비밀번호 초기화 (관리자용)
     * 
     * @param userId      대상 사용자 ID
     * @param newPassword 새 비밀번호
     */
    @Transactional
    public int resetPassword(String userId, String newPassword) {
        if (!userMapper.existsByUserId(userId)) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        String encodedPassword = passwordEncoder.encode(newPassword);
        int result = userMapper.resetPassword(userId, encodedPassword);
        log.info("비밀번호 초기화 완료: userId={}", userId);
        return result;
    }

    /**
     * 만료된 비밀번호 변경
     */
    @Transactional
    public void changeExpiredPassword(String userId, String newPassword) {
        if (!userMapper.existsByUserId(userId)) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        String encodedPassword = passwordEncoder.encode(newPassword);
        userMapper.updatePassword(userId, encodedPassword);
        log.info("만료된 비밀번호 변경 완료: userId={}", userId);
    }

    /**
     * 비밀번호 찾기 (힌트 기반 + 이메일 링크 발송)
     * 
     * @param request 비밀번호 찾기 요청
     * @return 비밀번호 찾기 결과
     */
    @Transactional
    public FindPasswordResponse findPassword(FindPasswordRequest request) {
        try {
            // 1. 평문 담당자명, 연락처를 암호화 (DB 저장 형식에 맞게)
            String encryptedPerson = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(request.getPerson()));
            String encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(request.getPhone()));

            // 2. 회원 정보 조회
            User user = userMapper.findByUserIdAndCorpNameAndPersonAndPhone(
                    request.getUserId(),
                    request.getCorpName(),
                    encryptedPerson,
                    encryptedPhone).orElse(null);

            if (user == null) {
                log.warn("비밀번호 찾기 실패 - 계정 정보 없음: userId={}", request.getUserId());
                return FindPasswordResponse.accountNotFound();
            }

            // 3. 비밀번호 힌트 검증
            int hintResult = passwordHintMapper.verifyHint(
                    user.getSeq(),
                    request.getHintQuestion(),
                    request.getHintAnswer());

            if (hintResult == 0) {
                log.warn("비밀번호 찾기 실패 - 힌트 불일치: userId={}", request.getUserId());
                return FindPasswordResponse.hintMismatch();
            }

            // 4. 기존 토큰 무효화
            passwordResetTokenMapper.invalidateAllByUserId(request.getUserId());

            // 5. 새 토큰 생성 및 저장
            String token = UUID.randomUUID().toString();
            LocalDateTime expireDate = LocalDateTime.now().plusMinutes(TOKEN_EXPIRATION_MINUTES);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .userId(request.getUserId())
                    .token(token)
                    .expireDate(expireDate)
                    .usedYn("N")
                    .build();

            passwordResetTokenMapper.insert(resetToken);

            // 6. 비밀번호 재설정 이메일 발송
            String resetLink = baseUrl + "/reset-password?token=" + token;
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);

            // 7. 마스킹된 이메일 반환
            String maskedEmail = maskEmail(user.getEmail());
            log.info("비밀번호 찾기 성공 - 재설정 링크 발송: userId={}, email={}", request.getUserId(), maskedEmail);

            return FindPasswordResponse.success(maskedEmail);

        } catch (Exception e) {
            log.error("비밀번호 찾기 중 오류 발생: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "비밀번호 찾기 처리 중 오류가 발생했습니다.");
        }
    }

    /**
     * 비밀번호 재설정 토큰 유효성 검증
     * 
     * @param token 재설정 토큰
     * @return 토큰 정보 (userId 포함)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> validateResetToken(String token) {
        PasswordResetToken resetToken = passwordResetTokenMapper.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 토큰입니다."));

        if (!resetToken.isValid()) {
            if (resetToken.isUsed()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이미 사용된 토큰입니다.");
            }
            if (resetToken.isExpired()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "만료된 토큰입니다.");
            }
        }

        // 사용자 존재 확인
        User user = userMapper.findByUserId(resetToken.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        return Map.of(
                "valid", true,
                "userId", maskUserId(user.getUserId()));
    }

    /**
     * 토큰을 이용한 비밀번호 재설정
     * 
     * @param token       재설정 토큰
     * @param newPassword 새 비밀번호
     */
    @Transactional
    public void resetPasswordWithToken(String token, String newPassword) {
        // 1. 토큰 조회 및 유효성 검증
        PasswordResetToken resetToken = passwordResetTokenMapper.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 토큰입니다."));

        if (!resetToken.isValid()) {
            if (resetToken.isUsed()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이미 사용된 토큰입니다.");
            }
            if (resetToken.isExpired()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "만료된 토큰입니다. 비밀번호 찾기를 다시 진행해주세요.");
            }
        }

        // 2. 사용자 존재 확인
        if (!userMapper.existsByUserId(resetToken.getUserId())) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        // 3. 비밀번호 변경
        String encodedPassword = passwordEncoder.encode(newPassword);
        userMapper.updatePassword(resetToken.getUserId(), encodedPassword);

        // 4. 토큰 사용 처리
        passwordResetTokenMapper.markAsUsed(token);

        log.info("비밀번호 재설정 완료: userId={}", resetToken.getUserId());
    }

    /**
     * 회원 삭제 (단건)
     * 
     * @param seq 삭제할 회원 시퀀스
     */
    @Transactional
    public void deleteUser(Long seq) {
        int result = userMapper.deleteBySeq(seq);
        if (result == 0) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
        log.info("회원 삭제 완료: seq={}", seq);
    }

    /**
     * 회원 삭제 (일괄)
     * 
     * @param seqList 삭제할 회원 시퀀스 목록
     */
    @Transactional
    public void deleteUsers(List<Long> seqList) {
        if (seqList == null || seqList.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "삭제할 회원 목록이 비어있습니다.");
        }

        int result = userMapper.deleteBySeqList(seqList);
        log.info("회원 일괄 삭제 완료: count={}", result);
    }

    /**
     * 회원 정보 수정 (기업 정보)
     * 
     * @param user       수정할 회원 정보
     * @param operatorId 수정자 ID
     */
    @Transactional
    public void updateMemberInfo(User user, String operatorId) {
        // 회원 존재 여부 확인 및 기존 정보 조회
        User existingUser = userMapper.findBySeq(user.getSeq())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 연락처 암호화 처리 (이미 암호화되어 있지 않은 경우)
        String encryptedPhone = user.getPhone();
        try {
            if (user.getPhone() != null && user.getPhone().length() <= 13) {
                encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(user.getPhone()));
            }
        } catch (Exception e) {
            log.error("연락처 암호화 실패: {}", e.getMessage());
        }

        // 담당자명 암호화 처리
        String encryptedPerson = user.getPerson();
        try {
            if (user.getPerson() != null && !user.getPerson().isEmpty()) {
                encryptedPerson = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(user.getPerson()));
            }
        } catch (Exception e) {
            log.error("담당자명 암호화 실패: {}", e.getMessage());
        }

        // 수정용 User 객체 생성 - null인 필드는 기존 값 유지
        User updateUser = User.builder()
                .seq(user.getSeq())
                .corpName(user.getCorpName() != null ? user.getCorpName() : existingUser.getCorpName())
                .corpAddr(user.getCorpAddr() != null ? user.getCorpAddr() : existingUser.getCorpAddr())
                .bizNum(user.getBizNum() != null ? user.getBizNum() : existingUser.getBizNum())
                .bizTel(user.getBizTel() != null ? user.getBizTel() : existingUser.getBizTel())
                .person(encryptedPerson != null ? encryptedPerson : existingUser.getPerson())
                .phone(encryptedPhone != null ? encryptedPhone : existingUser.getPhone())
                .email(user.getEmail() != null ? user.getEmail() : existingUser.getEmail())
                .userLevel(user.getUserLevel() != null ? user.getUserLevel() : existingUser.getUserLevel())
                .allowIpYn(user.getAllowIpYn() != null ? user.getAllowIpYn() : existingUser.getAllowIpYn())
                .allowIp(user.getAllowIp() != null ? user.getAllowIp() : existingUser.getAllowIp())
                .status(user.getStatus() != null ? user.getStatus() : existingUser.getStatus())
                .callback(user.getCallback() != null ? user.getCallback() : existingUser.getCallback())
                .uptId(operatorId)
                .build();

        int result = userMapper.updateMemberInfo(updateUser);
        if (result == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "회원 정보 수정에 실패했습니다.");
        }

        log.info("회원 정보 수정 완료: seq={}, operatorId={}", user.getSeq(), operatorId);
    }

    // ==================== Private Helper Methods ====================

    /**
     * 이메일 마스킹 (예: te***@example.com)
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 2) {
            return email.charAt(0) + "***" + email.substring(atIndex);
        }
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }

    /**
     * 아이디 마스킹 (예: tes*****)
     */
    private String maskUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            return "***";
        }
        if (userId.length() <= 3) {
            return userId.charAt(0) + "**";
        }
        return userId.substring(0, 3) + "*".repeat(userId.length() - 3);
    }
}
