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
import org.springframework.web.util.HtmlUtils;

/** KCP 실명인증 Service */
@Slf4j
@Service
@RequiredArgsConstructor
public class KcpAuthService {

  private final SurveyUserMapper surveyUserMapper;

  @Value("${kcp.site_cd:T0000}")
  private String siteCd;

  @Value("${kcp.enc_key:}")
  private String encKey;

  @Value("${kcp.web_siteid:}")
  private String webSiteid;

  @Value("${kcp.ret_url:}")
  private String retUrl;

  @Value("${kcp.gw_url:https://cert.kcp.co.kr/kcp_cert/cert_view.jsp}")
  private String gwUrl;

  /** KCP 인증 시작 데이터 생성 - 프론트엔드에서 KCP 팝업 인증 페이지 호출 시 필요한 데이터 */
  public KcpAuthResponse generateAuthData(String eventCode, String userKey) {
    try {
      CT_CLI cc = new CT_CLI();

      String ordrIdxx = "WISEAD" + System.currentTimeMillis();
      String upHash =
          cc.makeHashData(encKey, siteCd + ordrIdxx + "" + "" + "00" + "00" + "00" + "" + "");

      log.info("KCP 인증 시작 데이터 생성 - siteCd: {}, ordrIdxx: {}", siteCd, ordrIdxx);

      return KcpAuthResponse.builder()
          .success(true)
          .resultCode("0000")
          .resultMessage("인증 데이터 생성 완료")
          .eventCode(eventCode)
          .userKey(userKey)
          .ordrIdxx(ordrIdxx)
          .siteCd(siteCd)
          .reqTx("cert")
          .certMethod("01")
          .webSiteid(webSiteid)
          .fixCommid("")
          .retUrl(retUrl)
          .cerTradeType("")
          .upHash(upHash)
          .certOtpUse("Y")
          .certEncUseExt("Y")
          .kcpCertGwUrl(gwUrl)
          .build();

    } catch (Exception e) {
      log.error("KCP 인증 시작 데이터 생성 실패", e);
      return KcpAuthResponse.fail("9999", "인증 데이터 생성에 실패했습니다.");
    }
  }

  /** KCP 인증 결과 처리 - KCP 인증 완료 후 암호화된 데이터를 복호화하여 사용자 정보 추출 */
  @Transactional
  public KcpAuthResponse processAuthResult(KcpAuthRequest request) {
    try {
      CT_CLI cc = new CT_CLI();

      String siteCd = request.getSiteCd();
      String ordrIdxx = request.getOrdrIdxx();
      String certNo = request.getCertNo();
      String encCertData2 = request.getEncCertData2();
      String dnHash = request.getDnHash();

      if (!cc.checkValidHash(encKey, dnHash, siteCd + ordrIdxx + certNo)) {
        log.warn("KCP 인증 결과 검증 실패 - dn_hash 불일치");
        return KcpAuthResponse.fail("9001", "인증 데이터 검증에 실패했습니다.");
      }

      cc.decryptEncCert(encKey, siteCd, certNo, encCertData2);

      String phoneNo = cc.getKeyValue("phone_no");
      String userName = cc.getKeyValue("user_name");
      String birthDay = cc.getKeyValue("birth_day");
      String sexCode = cc.getKeyValue("sex_code");
      String localCode = cc.getKeyValue("local_code");
      String commId = cc.getKeyValue("comm_id");
      String ci = cc.getKeyValue("ci");
      String di = cc.getKeyValue("di");

      log.info(
          "KCP 인증 완료 - phoneNo: {}, userName: {}, birthDay: {}",
          CommonUtils.maskingPhone(phoneNo),
          CommonUtils.maskingName(userName),
          birthDay);

      String userKey = request.getUserKey();
      if (!CommonUtils.isNullOrEmpty(request.getEventCode())
          && !CommonUtils.isNullOrEmpty(phoneNo)) {
        String encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(phoneNo));
        surveyUserMapper
            .selectByEventCodeAndResendPhone(request.getEventCode(), encryptedPhone)
            .ifPresent(user -> log.info("KCP 인증 사용자 연동 - userKey: {}", user.getUserKey()));
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
    }
  }

  /** KCP 인증 결과로 설문 사용자 조회 - 전화번호로 기존 사용자 조회 */
  @Transactional(readOnly = true)
  public SurveyUserResponse findUserByKcpAuth(String eventCode, String phoneNo) {
    try {
      String encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(phoneNo));

      SurveyUser user =
          surveyUserMapper
              .selectByEventCodeAndResendPhone(eventCode, encryptedPhone)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          ErrorCode.RESOURCE_NOT_FOUND, "해당 이벤트에 등록된 사용자가 아닙니다."));

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

  /** KCP 콜백 처리 (팝업 방식) - KCP 서버에서 인증 완료 후 호출 - 결과를 HTML + postMessage로 반환 */
  public String processCallback(
      String resCd,
      String resMsg,
      String certNo,
      String encCertData2,
      String dnHash,
      String ordrIdxx) {

    if (!"0000".equals(resCd)) {
      log.warn("KCP 콜백 인증 실패 - resCd: {}, resMsg: {}", resCd, resMsg);
      return generateErrorHtml("인증에 실패했습니다: " + resMsg);
    }

    try {
      CT_CLI cc = new CT_CLI();

      if (!cc.checkValidHash(encKey, dnHash, siteCd + ordrIdxx + certNo)) {
        log.warn("KCP 콜백 dn_hash 검증 실패 - ordrIdxx: {}", ordrIdxx);
        return generateErrorHtml("인증 데이터 검증에 실패했습니다.");
      }

      cc.decryptEncCert(encKey, siteCd, certNo, encCertData2);

      String phoneNo = cc.getKeyValue("phone_no");
      String userName = cc.getKeyValue("user_name");
      String birthDay = cc.getKeyValue("birth_day");
      String sexCode = cc.getKeyValue("sex_code");
      String ci = cc.getKeyValue("ci");
      String di = cc.getKeyValue("di");

      log.info(
          "KCP 콜백 인증 완료 - phoneNo: {}, userName: {}",
          CommonUtils.maskingPhone(phoneNo),
          CommonUtils.maskingName(userName));

      return generateSuccessHtml(phoneNo, userName, birthDay, sexCode, ci, di);

    } catch (Exception e) {
      log.error("KCP 콜백 처리 실패", e);
      return generateErrorHtml("인증 처리 중 오류가 발생했습니다.");
    }
  }

  private String generateSuccessHtml(
      String phoneNo, String userName, String birthDay, String sexCode, String ci, String di) {
    return String.format(
        """
        <!DOCTYPE html>
        <html>
        <head><meta charset="UTF-8"><title>인증 완료</title></head>
        <body>
        <script>
            var resultData = {
                success: true,
                phoneNo: "%s",
                userName: "%s",
                birthDay: "%s",
                sexCode: "%s",
                ci: "%s",
                di: "%s"
            };

            if (window.opener) {
                window.opener.postMessage({
                    type: 'KCP_AUTH_RESULT',
                    data: resultData
                }, '*');
            }

            setTimeout(function() {
                window.close();
            }, 500);
        </script>
        <p>인증이 완료되었습니다. 창이 자동으로 닫힙니다.</p>
        </body>
        </html>
        """,
        escapeForJs(phoneNo),
        escapeForJs(userName),
        escapeForJs(birthDay),
        escapeForJs(sexCode),
        escapeForJs(ci),
        escapeForJs(di));
  }

  private String generateErrorHtml(String message) {
    String escaped = escapeForJs(message);
    return String.format(
        """
        <!DOCTYPE html>
        <html>
        <head><meta charset="UTF-8"><title>인증 실패</title></head>
        <body>
        <script>
            if (window.opener) {
                window.opener.postMessage({
                    type: 'KCP_AUTH_RESULT',
                    data: { success: false, message: "%s" }
                }, '*');
            }

            setTimeout(function() {
                window.close();
            }, 2000);
        </script>
        <p>%s</p>
        <p>창이 자동으로 닫힙니다.</p>
        </body>
        </html>
        """,
        escaped, HtmlUtils.htmlEscape(message == null ? "" : message));
  }

  /** JavaScript 문자열 및 HTML 이스케이프 */
  private String escapeForJs(String str) {
    if (str == null) {
      return "";
    }
    return HtmlUtils.htmlEscape(str).replace("\\", "\\\\").replace("'", "\\'");
  }
}
