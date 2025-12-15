package kr.wisead.domain.user.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.user.dto.LoginRequest;
import kr.wisead.domain.user.dto.LoginResponse;
import kr.wisead.domain.user.dto.SignUpRequest;
import kr.wisead.domain.user.entity.User;
import kr.wisead.mapper.primary.UserMapper;
import kr.wisead.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 인증 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 로그인
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        // 1. 사용자 조회
        User user = userMapper.findByUserId(request.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED, "아이디 또는 비밀번호가 일치하지 않습니다."));

        // 2. 계정 잠금 확인
        if (user.isLocked()) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "로그인 실패 횟수 초과로 계정이 잠겼습니다. 관리자에게 문의하세요.");
        }

        // 3. 계정 상태 확인
        if (!user.isActive()) {
            if ("미승인".equals(user.getStatus())) {
                throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "승인 대기 중인 계정입니다.");
            } else if ("탈퇴".equals(user.getStatus())) {
                throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "탈퇴된 계정입니다.");
            } else {
                throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "비활성화된 계정입니다.");
            }
        }

        // 4. 비밀번호 확인
        if (!passwordEncoder.matches(request.getUserPass(), user.getUserPass())) {
            userMapper.increaseLoginFailureCnt(request.getUserId());
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "아이디 또는 비밀번호가 일치하지 않습니다.");
        }

        // 5. 로그인 성공 처리
        userMapper.updateLastLogin(request.getUserId());

        // 6. JWT 토큰 생성 (사용자 이름 포함)
        Authentication authentication = createAuthentication(user);
        String accessToken = jwtTokenProvider.createAccessToken(authentication, user.getPerson());
        String refreshToken = jwtTokenProvider.createRefreshToken(authentication, user.getPerson());

        log.info("로그인 성공: userId={}", user.getUserId());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenValidityInSeconds())
                .user(LoginResponse.UserInfo.builder()
                        .seq(user.getSeq())
                        .userId(user.getUserId())
                        .corpName(user.getCorpName())
                        .person(user.getPerson())
                        .email(user.getEmail())
                        .userLevel(user.getUserLevel())
                        .status(user.getStatus())
                        .build())
                .build();
    }

    /**
     * 회원가입
     */
    @Transactional
    public void signUp(SignUpRequest request) {
        // 1. 비밀번호 확인
        if (!request.isPasswordMatched()) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 2. 아이디 중복 확인
        if (userMapper.existsByUserId(request.getUserId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL, "이미 사용 중인 아이디입니다.");
        }

        // 3. 이메일 중복 확인
        if (userMapper.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL, "이미 등록된 이메일입니다.");
        }

        // 4. 사용자 생성
        User user = User.builder()
                .userId(request.getUserId())
                .userPass(passwordEncoder.encode(request.getUserPass()))
                .corpName(request.getCorpName())
                .corpAddr(request.getCorpAddr())
                .bizNum(request.getBizNum())
                .bizTel(request.getBizTel())
                .person(request.getPerson())
                .phone(request.getPhone())
                .email(request.getEmail())
                .userLevel(1) // 일반 회원
                .useYn("Y")
                .status("미승인") // 가입 후 관리자 승인 필요
                .subtractUnitPrice(BigDecimal.valueOf(100.0))
                .regId(request.getUserId())
                .build();

        userMapper.insert(user);

        log.info("회원가입 완료: userId={}", request.getUserId());
    }

    /**
     * 토큰 갱신
     */
    public LoginResponse refreshToken(String refreshToken) {
        // 1. Refresh Token 유효성 검증
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "유효하지 않은 Refresh Token입니다.");
        }

        // 2. 토큰에서 사용자 정보 추출
        String userId = jwtTokenProvider.getUserId(refreshToken);

        // 3. 사용자 조회
        User user = userMapper.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 4. 계정 상태 확인
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 5. 새 토큰 발급 (사용자 이름 포함)
        Authentication authentication = createAuthentication(user);
        String newAccessToken = jwtTokenProvider.createAccessToken(authentication, user.getPerson());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(authentication, user.getPerson());

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenValidityInSeconds())
                .user(LoginResponse.UserInfo.builder()
                        .seq(user.getSeq())
                        .userId(user.getUserId())
                        .corpName(user.getCorpName())
                        .person(user.getPerson())
                        .email(user.getEmail())
                        .userLevel(user.getUserLevel())
                        .status(user.getStatus())
                        .build())
                .build();
    }

    /**
     * 아이디 중복 확인
     */
    public boolean checkUserIdDuplicate(String userId) {
        return userMapper.existsByUserId(userId);
    }

    /**
     * 이메일 중복 확인
     */
    public boolean checkEmailDuplicate(String email) {
        return userMapper.existsByEmail(email);
    }

    /**
     * Authentication 객체 생성
     */
    private Authentication createAuthentication(User user) {
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority(user.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER")
        );
        return new UsernamePasswordAuthenticationToken(user.getUserId(), null, authorities);
    }
}
