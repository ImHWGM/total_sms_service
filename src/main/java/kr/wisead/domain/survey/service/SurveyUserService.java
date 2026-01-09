package kr.wisead.domain.survey.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CommonUtils;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.survey.dto.SurveyUserRequest;
import kr.wisead.domain.survey.dto.SurveyUserResponse;
import kr.wisead.domain.survey.entity.SurveyMaster;
import kr.wisead.domain.survey.entity.SurveyUser;
import kr.wisead.mapper.primary.SurveyMasterMapper;
import kr.wisead.mapper.primary.SurveyUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.wisead.common.response.PageResponse;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 설문 사용자 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SurveyUserService {

    private final SurveyUserMapper surveyUserMapper;
    private final SurveyMasterMapper surveyMasterMapper;

    /**
     * 이벤트별 참여자 목록 조회
     */
    @Transactional(readOnly = true)
    public List<SurveyUserResponse> getUsersByEventSeq(Integer eventSeq) {
        List<SurveyUser> users = surveyUserMapper.selectByEventSeq(eventSeq);
        return users.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 검색 조건으로 참여자 목록 조회 (페이징)
     *
     * 개인정보취합(P) searchType: number, customerName, eventName, winnerName, phoneNumber, rrn, address, depositDate, shipmentDate
     * 설문조사(S) searchType: number, customerName, eventName, phoneNumber, userKey, lastAccessDate, completionDate
     */
    @Transactional(readOnly = true)
    public PageResponse<SurveyUserResponse> searchUsers(
            Integer eventSeq,
            String eventType,
            String searchType,
            String keyword,
            String startDate,
            String endDate,
            String status,
            int page,
            int size) {

        Map<String, Object> params = new HashMap<>();
        params.put("eventSeq", eventSeq);
        params.put("eventType", eventType);
        params.put("searchType", searchType);
        params.put("startDate", startDate);
        params.put("endDate", endDate);
        params.put("status", status);
        params.put("offset", (page - 1) * size);
        params.put("size", size);

        // 검색 키워드 처리 (암호화 필요한 필드)
        if (!CommonUtils.isNullOrEmpty(keyword) && !CommonUtils.isNullOrEmpty(searchType)) {
            switch (searchType) {
                case "winnerName":
                    // 당첨자명 암호화
                    try {
                        String encryptedName = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(keyword));
                        params.put("keyword", encryptedName);
                    } catch (Exception e) {
                        log.error("이름 암호화 실패", e);
                        params.put("keyword", keyword);
                    }
                    break;
                case "phoneNumber":
                    // 전화번호 암호화
                    try {
                        String encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(keyword));
                        params.put("keyword", encryptedPhone);
                    } catch (Exception e) {
                        log.error("전화번호 암호화 실패", e);
                        params.put("keyword", keyword);
                    }
                    break;
                case "rrn":
                    // 주민등록번호 암호화
                    try {
                        String encryptedRrn = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(keyword));
                        params.put("keyword", encryptedRrn);
                    } catch (Exception e) {
                        log.error("주민등록번호 암호화 실패", e);
                        params.put("keyword", keyword);
                    }
                    break;
                case "address":
                    // 주소 암호화
                    try {
                        String encryptedAddress = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(keyword));
                        params.put("keyword", encryptedAddress);
                    } catch (Exception e) {
                        log.error("주소 암호화 실패", e);
                        params.put("keyword", keyword);
                    }
                    break;
                default:
                    // 나머지 필드는 암호화 없이 사용
                    params.put("keyword", keyword);
                    break;
            }
        }

        int total = surveyUserMapper.countWithSearch(params);
        List<SurveyUser> users = surveyUserMapper.selectWithSearch(params);

        List<SurveyUserResponse> content = users.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResponse.of(content, page, size, total);
    }

    /**
     * 설문 완료자 목록 조회
     */
    @Transactional(readOnly = true)
    public List<SurveyUserResponse> getCompletedUsers(Integer eventSeq) {
        List<SurveyUser> users = surveyUserMapper.selectCompletedByEventSeq(eventSeq);
        return users.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 미참여/접속자 목록 조회
     */
    @Transactional(readOnly = true)
    public List<SurveyUserResponse> getAbsenteesAndLurkers(Integer eventSeq) {
        List<SurveyUser> users = surveyUserMapper.selectAbsenteesAndLurkers(eventSeq);
        return users.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 사용자 상세 조회 (시퀀스)
     */
    @Transactional(readOnly = true)
    public SurveyUserResponse getUserBySeq(Integer userSeq) {
        SurveyUser user = surveyUserMapper.selectBySeq(userSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."));
        return toResponse(user);
    }

    /**
     * 사용자 상세 조회 (사용자 키)
     */
    @Transactional(readOnly = true)
    public SurveyUserResponse getUserByUserKey(String userKey) {
        SurveyUser user = surveyUserMapper.selectByUserKey(userKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."));
        return toResponse(user);
    }

    /**
     * 이벤트별 참여자 수 조회
     */
    @Transactional(readOnly = true)
    public int countByEventSeq(Integer eventSeq) {
        return surveyUserMapper.countByEventSeq(eventSeq);
    }

    /**
     * 설문 완료자 수 조회
     */
    @Transactional(readOnly = true)
    public int countCompletedByEventSeq(Integer eventSeq) {
        return surveyUserMapper.countCompletedByEventSeq(eventSeq);
    }

    /**
     * 미참여자 수 조회
     */
    @Transactional(readOnly = true)
    public int countAbsenteesByEventSeq(Integer eventSeq) {
        return surveyUserMapper.countAbsenteesByEventSeq(eventSeq);
    }

    /**
     * 접속자 수 조회 (접속만 하고 미완료)
     */
    @Transactional(readOnly = true)
    public int countLurkersByEventSeq(Integer eventSeq) {
        return surveyUserMapper.countLurkersByEventSeq(eventSeq);
    }

    /**
     * 참여자 등록
     */
    @Transactional
    public SurveyUserResponse createUser(SurveyUserRequest request, String regId) {
        // 이벤트 확인
        SurveyMaster event = surveyMasterMapper.selectByEventSeq(request.getEventSeq())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        // 사용자 키 생성
        String userKey = generateUserKey();

        // 전화번호 암호화
        String encryptedPhone = null;
        if (!CommonUtils.isNullOrEmpty(request.getUserPhone())) {
            try {
                encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(request.getUserPhone()));
            } catch (Exception e) {
                log.error("전화번호 암호화 실패", e);
            }
        }

        SurveyUser user = SurveyUser.builder()
                .eventSeq(request.getEventSeq())
                .userKey(userKey)
                .userPhone(encryptedPhone)
                .resendUserPhone(encryptedPhone)
                .delYn("N")
                .regId(regId)
                .build();

        surveyUserMapper.insert(user);
        log.info("설문 참여자 등록 완료: eventSeq={}, userKey={}", request.getEventSeq(), userKey);

        return toResponse(user);
    }

    /**
     * 참여자 일괄 등록
     */
    @Transactional
    public int createUsersBatch(Integer eventSeq, List<SurveyUserRequest> requests, String regId) {
        // 이벤트 확인
        SurveyMaster event = surveyMasterMapper.selectByEventSeq(eventSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        List<SurveyUser> users = new ArrayList<>();
        for (SurveyUserRequest request : requests) {
            String userKey = generateUserKey();

            // 전화번호 암호화
            String encryptedPhone = null;
            if (!CommonUtils.isNullOrEmpty(request.getUserPhone())) {
                try {
                    encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(request.getUserPhone()));
                } catch (Exception e) {
                    log.error("전화번호 암호화 실패", e);
                }
            }

            users.add(SurveyUser.builder()
                    .eventSeq(eventSeq)
                    .userKey(userKey)
                    .userPhone(encryptedPhone)
                    .resendUserPhone(encryptedPhone)
                    .regId(regId)
                    .build());
        }

        int result = surveyUserMapper.insertBatch(users);
        log.info("설문 참여자 일괄 등록 완료: eventSeq={}, count={}", eventSeq, result);

        return result;
    }

    /**
     * 참여자 정보 수정
     */
    @Transactional
    public SurveyUserResponse updateUser(Integer userSeq, SurveyUserRequest request, String uptId) {
        SurveyUser existingUser = surveyUserMapper.selectBySeq(userSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 전화번호 암호화
        String encryptedPhone = null;
        if (!CommonUtils.isNullOrEmpty(request.getUserPhone())) {
            try {
                encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(request.getUserPhone()));
            } catch (Exception e) {
                log.error("전화번호 암호화 실패", e);
            }
        }

        // 이름 암호화
        String encryptedName = null;
        if (!CommonUtils.isNullOrEmpty(request.getUserName())) {
            try {
                encryptedName = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(request.getUserName()));
            } catch (Exception e) {
                log.error("이름 암호화 실패", e);
                encryptedName = request.getUserName();
            }
        }

        SurveyUser updateUser = SurveyUser.builder()
                .seq(userSeq)
                .userName(encryptedName)
                .userPhone(encryptedPhone)
                .resendUserPhone(encryptedPhone)
                .userEmail(request.getUserEmail())
                .address(request.getAddress())
                .address2(request.getAddress2())
                .uptId(uptId)
                .build();

        surveyUserMapper.update(updateUser);
        log.info("설문 참여자 수정 완료: userSeq={}", userSeq);

        return getUserBySeq(userSeq);
    }

    /**
     * 재발송 전화번호 수정
     */
    @Transactional
    public void updateResendPhone(Integer userSeq, String phone, String uptId) {
        // 전화번호 암호화
        String encryptedPhone = null;
        try {
            encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(phone));
        } catch (Exception e) {
            log.error("전화번호 암호화 실패", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "전화번호 암호화 실패");
        }

        surveyUserMapper.updateResendPhone(userSeq, encryptedPhone, uptId);
        log.info("재발송 전화번호 수정 완료: userSeq={}", userSeq);
    }

    /**
     * 입금일자/출고일자 수정
     */
    @Transactional
    public void updatePaymentInfo(Integer userSeq, LocalDate depositDate, LocalDate shipmentDate, String uptId) {
        surveyUserMapper.updatePaymentInfo(userSeq, depositDate, shipmentDate, uptId);
        log.info("입금/출고 정보 수정 완료: userSeq={}", userSeq);
    }

    /**
     * 참여자 삭제 (소프트 삭제)
     */
    @Transactional
    public void deleteUser(Integer userSeq, String uptId) {
        surveyUserMapper.softDelete(userSeq, uptId);
        log.info("설문 참여자 삭제 완료: userSeq={}", userSeq);
    }

    /**
     * 이벤트의 모든 참여자 삭제 (소프트 삭제)
     */
    @Transactional
    public void deleteUsersByEventSeq(Integer eventSeq, String uptId) {
        surveyUserMapper.softDeleteByEventSeq(eventSeq, uptId);
        log.info("이벤트 참여자 전체 삭제 완료: eventSeq={}", eventSeq);
    }

    /**
     * 범용인증 상태 확인
     */
    @Transactional(readOnly = true)
    public Map<String, Object> checkGeneralAuthStatus(String authCodeUrl, String generalAuthCode) {
        return surveyUserMapper.checkGeneralAuthStatus(authCodeUrl, generalAuthCode);
    }

    /**
     * 범용인증으로 사용자 조회
     */
    @Transactional(readOnly = true)
    public SurveyUserResponse getUserByGeneralAuthCode(String authCodeUrl, String generalAuthCode) {
        SurveyUser user = surveyUserMapper.selectByGeneralAuthCode(authCodeUrl, generalAuthCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "인증코드로 사용자를 찾을 수 없습니다."));
        return toResponse(user);
    }

    /**
     * 전화번호 존재 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean existsByEventCodeAndPhone(String eventCode, String phone) {
        try {
            String encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(phone));
            return surveyUserMapper.existsByEventCodeAndResendPhone(eventCode, encryptedPhone);
        } catch (Exception e) {
            log.error("전화번호 암호화 실패", e);
            return false;
        }
    }

    /**
     * 사용자 키 유효성 검증
     */
    @Transactional(readOnly = true)
    public boolean validateUserKey(Integer eventSeq, String userKey) {
        return surveyUserMapper.selectByEventSeqAndUserKey(eventSeq, userKey).isPresent();
    }

    /**
     * 설문 접속 시간 기록
     */
    @Transactional
    public void recordStartTime(String userKey) {
        surveyUserMapper.updateStartTime(userKey);
    }

    /**
     * 설문 인증 시간 기록
     */
    @Transactional
    public void recordAuthTime(String userKey) {
        surveyUserMapper.updateAuthTime(userKey);
    }

    // ==================== Private Methods ====================

    /**
     * 사용자 키 생성
     */
    private String generateUserKey() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * Entity to Response 변환 (복호화 + 마스킹 포함)
     */
    private SurveyUserResponse toResponse(SurveyUser user) {
        // 복호화
        String decryptedPhone = decryptData(user.getResendUserPhone());
        String decryptedName = decryptData(user.getUserName());

        // 상태 결정
        String status;
        if (user.getSubmissionDate() != null) {
            status = "참여완료";
        } else if (user.getSurveyStartTime() != null) {
            status = "접속중";
        } else {
            status = "미참여";
        }

        return SurveyUserResponse.builder()
                .userSeq(user.getSeq())
                .eventSeq(user.getEventSeq())
                .userKey(user.getUserKey())
                .userName(maskName(decryptedName))
                .userPhone(maskPhone(decryptedPhone))
                .resendUserPhone(maskPhone(decryptedPhone))
                .userEmail(maskEmail(user.getUserEmail()))
                .address(maskAddress(user.getAddress()))
                .depositDate(user.getDepositDate())
                .shipmentDate(user.getShipmentDate())
                .submissionDate(user.getSubmissionDate())
                .surveyStartTime(user.getSurveyStartTime())
                .regDate(user.getRegDate())
                .status(status)
                .eventCode(user.getEventCode())
                .eventType(user.getEventType())
                .eventName(user.getEventName())
                .generalAuthCode(user.getGeneralAuthCode())
                .privacyPolicyYn(user.getPrivacyPolicyYn())
                .privacyPolicyTtl(user.getPrivacyPolicyTtl())
                .privacyPolicyDesc(user.getPrivacyPolicyDesc())
                .corpName(user.getCorpName())
                .build();
    }

    /**
     * 전화번호 마스킹 (뒷 4자리만 표시)
     */
    private String maskPhone(String phone) {
        if (CommonUtils.isNullOrEmpty(phone) || phone.length() < 4) {
            return phone;
        }
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }

    /**
     * 이름 마스킹 (첫 글자 + 마스킹)
     */
    private String maskName(String name) {
        if (CommonUtils.isNullOrEmpty(name)) {
            return name;
        }
        if (name.length() == 1) {
            return "*";
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        // 3자 이상: 첫 글자 + 마스킹 + 마지막 글자
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    /**
     * 이메일 마스킹 (아이디 앞 3자 + *** + @도메인)
     */
    private String maskEmail(String email) {
        if (CommonUtils.isNullOrEmpty(email) || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf("@");
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        if (localPart.length() <= 3) {
            return localPart.charAt(0) + "***" + domain;
        }
        return localPart.substring(0, 3) + "***" + domain;
    }

    /**
     * 주소 마스킹 (앞 10자만 표시)
     */
    private String maskAddress(String address) {
        if (CommonUtils.isNullOrEmpty(address)) {
            return address;
        }
        if (address.length() <= 10) {
            return address;
        }
        return address.substring(0, 10) + "***";
    }

    /**
     * 데이터 복호화
     */
    private String decryptData(String encryptedData) {
        if (CommonUtils.isNullOrEmpty(encryptedData)) {
            return null;
        }
        try {
            return CryptoUtils.decryptAES256(CryptoUtils.decodeBase64(encryptedData));
        } catch (Exception e) {
            log.debug("데이터 복호화 실패: {}", e.getMessage());
            return encryptedData;
        }
    }
}
