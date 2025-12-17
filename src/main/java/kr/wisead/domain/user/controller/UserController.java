package kr.wisead.domain.user.controller;

import jakarta.validation.Valid;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.email.dto.EmailVerificationRequest;
import kr.wisead.domain.user.dto.FindIdRequest;
import kr.wisead.domain.user.dto.FindIdResponse;
import kr.wisead.domain.user.dto.FindPasswordRequest;
import kr.wisead.domain.user.dto.FindPasswordResponse;
import kr.wisead.domain.user.dto.MemberUpdateRequest;
import kr.wisead.domain.user.dto.PasswordResetConfirmRequest;
import kr.wisead.domain.user.dto.UserResponse;
import kr.wisead.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 회원 API 컨트롤러
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 내 정보 조회
     */
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyInfo(@AuthenticationPrincipal UserDetails userDetails) {
        UserResponse response = userService.getUserByUserId(userDetails.getUsername());
        return ApiResponse.success(response);
    }

    /**
     * 회원 정보 조회 (by SEQ) - 관리자 전용
     */
    @GetMapping("/{seq}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> getUserBySeq(@PathVariable Long seq) {
        UserResponse response = userService.getUserBySeq(seq);
        return ApiResponse.success(response);
    }

    /**
     * 회원 목록 조회 - 관리자 전용
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<UserResponse>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<UserResponse> response = userService.getUsers(page, size);
        return ApiResponse.success(response);
    }

    /**
     * 비밀번호 변경
     */
    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> request) {
        String currentPassword = request.get("currentPassword");
        String newPassword = request.get("newPassword");
        userService.changePassword(userDetails.getUsername(), currentPassword, newPassword);
        return ApiResponse.success("비밀번호가 변경되었습니다.");
    }

    /**
     * 회원 상태 변경 - 관리자 전용
     */
    @PutMapping("/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> updateStatus(
            @PathVariable String userId,
            @RequestBody Map<String, String> request) {
        String status = request.get("status");
        userService.updateStatus(userId, status);
        return ApiResponse.success("회원 상태가 변경되었습니다.");
    }

    /**
     * 아이디 찾기 1단계 - 정보 검증 및 이메일 인증코드 발송
     * POST /api/users/find-id/request
     */
    @PostMapping("/find-id/request")
    public ApiResponse<FindIdResponse> requestFindId(@Valid @RequestBody FindIdRequest request) {
        FindIdResponse response = userService.requestFindId(request);
        return ApiResponse.success(response);
    }

    /**
     * 아이디 찾기 2단계 - 인증코드 검증 및 아이디 반환
     * POST /api/users/find-id/verify
     */
    @PostMapping("/find-id/verify")
    public ApiResponse<FindIdResponse> verifyAndGetUserId(@Valid @RequestBody EmailVerificationRequest request) {
        FindIdResponse response = userService.verifyAndGetUserId(request.getEmail(), request.getCode());
        return ApiResponse.success(response);
    }

    /**
     * 아이디 찾기 (기존 방식 - 하위 호환용)
     */
    @PostMapping("/find-id")
    public ApiResponse<Map<String, String>> findUserId(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String person = request.get("person");
        String maskedUserId = userService.findUserId(email, person);
        return ApiResponse.success(Map.of("userId", maskedUserId));
    }

    /**
     * 비밀번호 초기화 (관리자용)
     * PUT /api/users/{userId}/password/reset
     */
    @PutMapping("/{userId}/password/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> resetPassword(
            @PathVariable String userId,
            @RequestBody Map<String, String> request) {
        String newPassword = request.get("newPassword");
        userService.resetPassword(userId, newPassword);
        return ApiResponse.success("비밀번호가 초기화되었습니다.");
    }

    /**
     * 계정 잠금 해제 (관리자용)
     * PUT /api/users/{userId}/unlock
     */
    @PutMapping("/{userId}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> unlockAccount(@PathVariable String userId) {
        userService.unlockAccount(userId);
        return ApiResponse.success("계정 잠금이 해제되었습니다.");
    }

    /**
     * 비밀번호 만료일 연장 (본인용)
     * PUT /api/users/me/password/extend
     */
    @PutMapping("/me/password/extend")
    public ApiResponse<Void> extendPasswordExpiry(@AuthenticationPrincipal UserDetails userDetails) {
        userService.extendPasswordExpiry(userDetails.getUsername());
        return ApiResponse.success("비밀번호 만료일이 180일 연장되었습니다.");
    }

    /**
     * 만료된 비밀번호 변경 (본인용)
     * PUT /api/users/me/password/expired
     */
    @PutMapping("/me/password/expired")
    public ApiResponse<Void> changeExpiredPassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> request) {
        String newPassword = request.get("newPassword");
        userService.changeExpiredPassword(userDetails.getUsername(), newPassword);
        return ApiResponse.success("비밀번호가 변경되었습니다.");
    }

    /**
     * 비밀번호 찾기 (힌트 기반 + 이메일 링크 발송)
     * POST /api/users/find-pw
     */
    @PostMapping("/find-pw")
    public ApiResponse<FindPasswordResponse> findPassword(@Valid @RequestBody FindPasswordRequest request) {
        FindPasswordResponse response = userService.findPassword(request);
        return ApiResponse.success(response);
    }

    /**
     * 비밀번호 재설정 토큰 유효성 검증
     * GET /api/users/password/reset-validate?token=xxx
     */
    @GetMapping("/password/reset-validate")
    public ApiResponse<Map<String, Object>> validateResetToken(@RequestParam String token) {
        Map<String, Object> result = userService.validateResetToken(token);
        return ApiResponse.success(result);
    }

    /**
     * 비밀번호 재설정 확인 (새 비밀번호 설정)
     * POST /api/users/password/reset-confirm
     */
    @PostMapping("/password/reset-confirm")
    public ApiResponse<Void> resetPasswordWithToken(@Valid @RequestBody PasswordResetConfirmRequest request) {
        userService.resetPasswordWithToken(request.getToken(), request.getNewPassword());
        return ApiResponse.success("비밀번호가 성공적으로 변경되었습니다.");
    }

    /**
     * 회원 삭제 (단건) - 관리자 전용
     * DELETE /api/users/{seq}
     */
    @DeleteMapping("/{seq}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteUser(@PathVariable Long seq) {
        userService.deleteUser(seq);
        return ApiResponse.success("회원이 삭제되었습니다.");
    }

    /**
     * 회원 삭제 (일괄) - 관리자 전용
     * DELETE /api/users
     */
    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteUsers(@RequestBody Map<String, List<Long>> request) {
        List<Long> seqList = request.get("seqList");
        userService.deleteUsers(seqList);
        return ApiResponse.success("회원들이 삭제되었습니다.");
    }

    /**
     * 회원 정보 수정 (기업 정보) - 관리자 전용
     * PUT /api/users/{seq}/info
     */
    @PutMapping("/{seq}/info")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> updateMemberInfo(
            @PathVariable Long seq,
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody MemberUpdateRequest request) {
        request.setSeq(seq);
        userService.updateMemberInfo(request.toEntity(), userDetails.getUsername());
        return ApiResponse.success("회원 정보가 수정되었습니다.");
    }
}
