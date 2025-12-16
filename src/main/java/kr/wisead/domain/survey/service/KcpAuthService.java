package kr.wisead.domain.survey.service;

import kr.co.kcp.CT_CLI;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CommonUtils;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.survey.dto.KcpAuthRequest;
import kr.wisead.domain.survey.dto.KcpAuthResponse;
import kr.wisead.domain.survey.dto.SurveyUserResponse;
import kr.wisead.domain.survey.entity.SurveyUser;
import kr.wisead.mapper.primary.SurveyUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * KCP 실명인증 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KcpAuthService {

    private final SurveyUserMapper surveyUserMapper;

    @Value("${kcp.site_cd:T0000}")
    private String siteCd;

    @Value("${kcp.enc_key:}")
    private String encKey;

    /**
     * KCP 인증 시작 데이터 생성
     * - 모바일 앱에서 WebView로 KCP 인증 페이지 호출 시 필요한 데이터
     */
    public KcpAuthResponse generateAuthData(String eventCode, String userKey) {
        try {
            CT_CLI cc = new CT_CLI();

            // 주문번호 생성 (유니크)
            String ordrIdxx = "WISEAD" + System.currentTimeMillis();

            // up_hash 생성 (본인확인용)
            String upHash = cc.makeHashData(encKey, siteCd + ordrIdxx + "" + "" + "00" + "00" + "00" + "" + "");

            log.info("KCP 인증 시작 데이터 생성 - siteCd: {}, ordrIdxx: {}", siteCd, ordrIdxx);

            return KcpAuthResponse.builder()
                    .success(true)
                    .resultCode("0000")
                    .resultMessage("인증 데이터 생성 완료")
                    .eventCode(eventCode)
                    .userKey(userKey)
                    .build();

        } catch (Exception e) {
            log.error("KCP 인증 시작 데이터 생성 실패", e);
            return KcpAuthResponse.fail("9999", "인증 데이터 생성에 실패했습니다.");
        }
    }

    /**
     * KCP 인증 결과 처리
     * - KCP 인증 완료 후 암호화된 데이터를 복호화하여 사용자 정보 추출
     */
    @Transactional
    public KcpAuthResponse processAuthResult(KcpAuthRequest request) {
        CT_CLI cc = null;

        try {
            cc = new CT_CLI();

            String siteCd = request.getSiteCd();
            String ordrIdxx = request.getOrdrIdxx();
            String certNo = request.getCertNo();
            String encCertData2 = request.getEncCertData2();
            String dnHash = request.getDnHash();

            // dn_hash 검증 (위변조 방지)
            if (!cc.checkValidHash(encKey, dnHash, siteCd + ordrIdxx + certNo)) {
                log.warn("KCP 인증 결과 검증 실패 - dn_hash 불일치");
                return KcpAuthResponse.fail("9001", "인증 데이터 검증에 실패했습니다.");
            }

            // 인증 데이터 복호화
            cc.decryptEncCert(encKey, siteCd, certNo, encCertData2);

            // 복호화된 데이터 추출
            String phoneNo = cc.getKeyValue("phone_no");
            String userName = cc.getKeyValue("user_name");
            String birthDay = cc.getKeyValue("birth_day");
            String sexCode = cc.getKeyValue("sex_code");
            String localCode = cc.getKeyValue("local_code");
            String commId = cc.getKeyValue("comm_id");
            String ci = cc.getKeyValue("ci");
            String di = cc.getKeyValue("di");
            String resCd = cc.getKeyValue("res_cd");
            String resMsg = cc.getKeyValue("res_msg");

            log.info("KCP 인증 완료 - phoneNo: {}, userName: {}, birthDay: {}",
                    CommonUtils.maskingPhone(phoneNo),
                    CommonUtils.maskingName(userName),
                    birthDay);

            // 설문 연동 처리 (eventCode와 userKey가 있는 경우)
            String userKey = request.getUserKey();
            if (!CommonUtils.isNullOrEmpty(request.getEventCode()) && !CommonUtils.isNullOrEmpty(phoneNo)) {
                // 전화번호로 사용자 조회 및 연동
                String encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(phoneNo));
                surveyUserMapper.selectByEventCodeAndResendPhone(request.getEventCode(), encryptedPhone)
                        .ifPresent(user -> {
                            log.info("KCP 인증 사용자 연동 - userKey: {}", user.getUserKey());
                        });
            }

            return KcpAuthResponse.builder()
                    .success(true)
                    .resultCode("0000")
                    .resultMessage("인증이 완료되었습니다.")
                    .phoneNo(phoneNo)
                    .userName(userName)
                    .birthDay(birthDay)
                    .sexCode(sexCode)
                    .localCode(localCode)
                    .commId(commId)
                    .ci(ci)
                    .di(di)
                    .userKey(userKey)
                    .eventCode(request.getEventCode())
                    .build();

        } catch (Exception e) {
            log.error("KCP 인증 결과 처리 실패", e);
            return KcpAuthResponse.fail("9999", "인증 처리 중 오류가 발생했습니다.");

        } finally {
            cc = null; // 객체 반납
        }
    }

    /**
     * KCP 인증 결과로 설문 사용자 조회
     * - 전화번호로 기존 사용자 조회
     */
    @Transactional(readOnly = true)
    public SurveyUserResponse findUserByKcpAuth(String eventCode, String phoneNo) {
        try {
            // 전화번호 암호화
            String encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(phoneNo));

            SurveyUser user = surveyUserMapper.selectByEventCodeAndResendPhone(eventCode, encryptedPhone)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                            "해당 이벤트에 등록된 사용자가 아닙니다."));

            if (user.isSubmitted()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 설문에 참여하셨습니다.");
            }

            return SurveyUserResponse.from(user);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("KCP 인증 사용자 조회 실패", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "사용자 조회 중 오류가 발생했습니다.");
        }
    }
}
