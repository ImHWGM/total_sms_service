package kr.wisead.domain.user.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.user.dto.UserResponse;
import kr.wisead.domain.user.entity.User;
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
}
