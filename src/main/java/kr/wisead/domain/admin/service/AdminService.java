package kr.wisead.domain.admin.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.admin.dto.AdminAccountRequest;
import kr.wisead.domain.payment.entity.Balance;
import kr.wisead.domain.user.dto.UserResponse;
import kr.wisead.domain.user.entity.User;
import kr.wisead.mapper.primary.BalanceMapper;
import kr.wisead.mapper.primary.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 관리자 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserMapper userMapper;
    private final BalanceMapper balanceMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 관리자 계정 생성
     * - 회원 등록
     * - 충전 정보 등록
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

        // 회원 정보 생성
        User user = User.builder()
                .userId(request.getUserId())
                .userPass(encodedPassword)
                .corpName(request.getCorpName())
                .corpAddr(request.getCorpAddr())
                .bizNum(request.getBizNum())
                .bizTel(request.getBizTel())
                .person(request.getPerson())
                .phone(request.getPhone())
                .email(request.getEmail())
                .userLevel(request.getUserLevel())
                .useYn("Y")
                .allowIpYn("Y")
                .status("승인")  // 관리자 계정은 자동 승인
                .regId(creatorId)
                .subtractUnitPrice(BigDecimal.ZERO)
                .loginFailureCnt(0)
                .build();

        // 회원 등록
        userMapper.insert(user);
        log.info("관리자 계정 생성: userId={}, level={}", request.getUserId(), request.getUserLevel());

        // 충전 정보 등록 (초기 잔액 0)
        Balance balance = Balance.builder()
                .userId(request.getUserId())
                .balance(java.math.BigDecimal.ZERO)
                .totalBalance(java.math.BigDecimal.ZERO)
                .operation("P")  // 초기 등록
                .comment("계정 생성")
                .regId(request.getUserId())
                .build();

        balanceMapper.insertBalance(balance);
        log.info("충전 정보 등록: userId={}", request.getUserId());

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

    // ==================== Private Methods ====================

    private UserResponse getUserResponse(User user) {
        return UserResponse.builder()
                .seq(user.getSeq())
                .userId(user.getUserId())
                .corpName(user.getCorpName())
                .corpAddr(user.getCorpAddr())
                .bizNum(user.getBizNum())
                .bizTel(user.getBizTel())
                .person(user.getPerson())
                .phone(user.getPhone())
                .email(user.getEmail())
                .userLevel(user.getUserLevel())
                .userLevelName(getUserLevelName(user.getUserLevel()))
                .useYn(user.getUseYn())
                .status(user.getStatus())
                .regDate(user.getRegDate())
                .build();
    }
}
