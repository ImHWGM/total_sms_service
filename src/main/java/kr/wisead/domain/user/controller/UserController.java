package kr.wisead.domain.user.controller;

import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.user.dto.UserResponse;
import kr.wisead.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

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
     * 아이디 찾기
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
}
