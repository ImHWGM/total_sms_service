package kr.wisead.domain.user.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.common.util.CommonUtils;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.user.dto.FindPasswordRequest;
import kr.wisead.domain.user.dto.FindPasswordResponse;
import kr.wisead.domain.user.dto.UserResponse;
import kr.wisead.domain.user.entity.User;
import kr.wisead.mapper.primary.PasswordHintMapper;
import kr.wisead.mapper.primary.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private final PasswordEncoder passwordEncoder;

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

        // 3. 비밀번호 변경
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
     * 아이디 찾기
     */
    @Transactional(readOnly = true)
    public String findUserId(String email, String person) {
        User user = userMapper.findByEmailAndPerson(email, person)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "일치하는 회원 정보가 없습니다."));

        // 아이디 일부 마스킹 처리
        String userId = user.getUserId();
        if (userId.length() <= 3) {
            return userId.charAt(0) + "**";
        }
        return userId.substring(0, 3) + "*".repeat(userId.length() - 3);
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
     * @param userId 대상 사용자 ID
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
     * 비밀번호 찾기 (힌트 기반)
     * @param request 비밀번호 찾기 요청
     * @return 비밀번호 찾기 결과
     */
    @Transactional
    public FindPasswordResponse findPassword(FindPasswordRequest request) {
        try {
            // 1. 담당자명, 연락처 복호화
            String decryptedPerson = CryptoUtils.getDecryptedAES256Data(request.getPerson());
            String decryptedPhone = CryptoUtils.getDecryptedAES256Data(request.getPhone());

            // 암호화된 값으로 DB 조회를 위해 다시 암호화
            String encryptedPerson = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(decryptedPerson));
            String encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(decryptedPhone));

            // 2. 회원 정보 조회
            User user = userMapper.findByUserIdAndCorpNameAndPersonAndPhone(
                    request.getUserId(),
                    request.getCorpName(),
                    encryptedPerson,
                    encryptedPhone
            ).orElse(null);

            if (user == null) {
                log.warn("비밀번호 찾기 실패 - 계정 정보 없음: userId={}", request.getUserId());
                return FindPasswordResponse.accountNotFound();
            }

            // 3. 비밀번호 힌트 검증
            int hintResult = passwordHintMapper.verifyHint(
                    user.getSeq(),
                    request.getHintQuestion(),
                    request.getHintAnswer()
            );

            if (hintResult == 0) {
                log.warn("비밀번호 찾기 실패 - 힌트 불일치: userId={}", request.getUserId());
                return FindPasswordResponse.hintMismatch();
            }

            // 4. 임시 비밀번호 생성 및 업데이트
            String tempPassword = CommonUtils.randomCode(12);
            String encodedPassword = passwordEncoder.encode(tempPassword);
            userMapper.updatePassword(request.getUserId(), encodedPassword);

            log.info("비밀번호 찾기 성공 - 임시 비밀번호 발급: userId={}", request.getUserId());
            return FindPasswordResponse.success(tempPassword);

        } catch (Exception e) {
            log.error("비밀번호 찾기 중 오류 발생: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "비밀번호 찾기 처리 중 오류가 발생했습니다.");
        }
    }

    /**
     * 회원 삭제 (단건)
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
     * @param user 수정할 회원 정보
     * @param operatorId 수정자 ID
     */
    @Transactional
    public void updateMemberInfo(User user, String operatorId) {
        // 회원 존재 여부 확인
        userMapper.findBySeq(user.getSeq())
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

        // 수정용 User 객체 생성 (uptId, phone 포함)
        User updateUser = User.builder()
                .seq(user.getSeq())
                .corpName(user.getCorpName())
                .corpAddr(user.getCorpAddr())
                .bizNum(user.getBizNum())
                .bizTel(user.getBizTel())
                .person(user.getPerson())
                .phone(encryptedPhone)
                .email(user.getEmail())
                .userLevel(user.getUserLevel())
                .allowIpYn(user.getAllowIpYn())
                .allowIp(user.getAllowIp())
                .status(user.getStatus())
                .callback(user.getCallback())
                .uptId(operatorId)
                .build();

        int result = userMapper.updateMemberInfo(updateUser);
        if (result == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "회원 정보 수정에 실패했습니다.");
        }

        log.info("회원 정보 수정 완료: seq={}, operatorId={}", user.getSeq(), operatorId);
    }
}
