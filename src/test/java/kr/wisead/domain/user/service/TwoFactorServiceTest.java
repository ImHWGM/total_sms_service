package kr.wisead.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.sms.service.SmsAuthService;
import kr.wisead.domain.user.dto.TwoFactorSettingsResponse;
import kr.wisead.domain.user.entity.User;
import kr.wisead.mapper.primary.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * TwoFactorService 단위 테스트 — 마이페이지 SMS 2FA 설정.
 *
 * <p>스펙 인수기준 C2/C3/C4/C10 검증.
 *
 * <p>plan v5 §4 Phase E-6.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TwoFactorService (마이페이지 SMS 2FA)")
class TwoFactorServiceTest {

  private static final String USERNAME = "wisead01";
  private static final Integer SEQ = 42;
  private static final String PHONE = "01012345678";
  private static final String CODE = "123456";

  @Mock private UserMapper userMapper;
  @Mock private SmsAuthService smsAuthService;

  @InjectMocks private TwoFactorService sut;

  // ==================== sendSmsRegisterCode (C2) ====================

  @Test
  @DisplayName("sendSmsRegisterCode_validPhone_callsSmsAuthService")
  void sendSmsRegisterCode_validPhone_callsSmsAuthService() {
    when(userMapper.findByUserId(USERNAME)).thenReturn(Optional.of(userOf(null, "EMAIL")));

    sut.sendSmsRegisterCode(USERNAME, PHONE);

    verify(smsAuthService, times(1)).sendVerificationCode(eq(SEQ), eq(PHONE));
  }

  @Test
  @DisplayName("sendSmsRegisterCode_invalidPhoneFormat_throws")
  void sendSmsRegisterCode_invalidPhoneFormat_throws() {
    when(userMapper.findByUserId(USERNAME)).thenReturn(Optional.of(userOf(null, "EMAIL")));

    assertThatThrownBy(() -> sut.sendSmsRegisterCode(USERNAME, "0211112222"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("휴대폰");

    verify(smsAuthService, never()).sendVerificationCode(anyInt(), anyString());
  }

  @Test
  @DisplayName("sendSmsRegisterCode_userNotFound_throws")
  void sendSmsRegisterCode_userNotFound_throws() {
    when(userMapper.findByUserId(USERNAME)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.sendSmsRegisterCode(USERNAME, PHONE))
        .isInstanceOf(BusinessException.class);

    verify(smsAuthService, never()).sendVerificationCode(anyInt(), anyString());
  }

  // ==================== verifySmsRegisterCode (C3/C4) ====================

  @Test
  @DisplayName("verifySmsRegisterCode_success_updatesLoginPhoneAndDefault (C3)")
  void verifySmsRegisterCode_success_updatesLoginPhoneAndDefault() {
    when(userMapper.findByUserId(USERNAME)).thenReturn(Optional.of(userOf(null, "EMAIL")));
    when(smsAuthService.verifyCodeAndGetPhone(SEQ, CODE)).thenReturn(PHONE);

    sut.verifySmsRegisterCode(USERNAME, CODE);

    // login_phone: AES256+Base64 암호화된 값으로 저장되어야 함
    String expectedEncrypted = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(PHONE));
    verify(userMapper, times(1)).updateLoginPhone(SEQ, expectedEncrypted);
    verify(userMapper, times(1)).updateDefaultTwoFactorMethod(SEQ, "SMS");
  }

  @Test
  @DisplayName("verifySmsRegisterCode_failure_doesNotUpdate (C4) — verifyCode throws → rollback")
  void verifySmsRegisterCode_failure_doesNotUpdate() {
    when(userMapper.findByUserId(USERNAME)).thenReturn(Optional.of(userOf(null, "EMAIL")));
    when(smsAuthService.verifyCodeAndGetPhone(SEQ, CODE))
        .thenThrow(
            new BusinessException(
                kr.wisead.common.response.ErrorCode.INVALID_INPUT_VALUE, "인증 코드가 일치하지 않습니다."));

    assertThatThrownBy(() -> sut.verifySmsRegisterCode(USERNAME, CODE))
        .isInstanceOf(BusinessException.class);

    verify(userMapper, never()).updateLoginPhone(anyInt(), anyString());
    verify(userMapper, never()).updateDefaultTwoFactorMethod(anyInt(), anyString());
  }

  @Test
  @DisplayName("verifySmsRegisterCode_expired_doesNotUpdate (C4)")
  void verifySmsRegisterCode_expired_doesNotUpdate() {
    when(userMapper.findByUserId(USERNAME)).thenReturn(Optional.of(userOf(null, "EMAIL")));
    when(smsAuthService.verifyCodeAndGetPhone(SEQ, CODE))
        .thenThrow(
            new BusinessException(
                kr.wisead.common.response.ErrorCode.INVALID_INPUT_VALUE, "인증 코드가 만료되었습니다."));

    assertThatThrownBy(() -> sut.verifySmsRegisterCode(USERNAME, CODE))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("만료");

    verify(userMapper, never()).updateLoginPhone(anyInt(), anyString());
    verify(userMapper, never()).updateDefaultTwoFactorMethod(anyInt(), anyString());
  }

  @Test
  @DisplayName("verifySmsRegisterCode_phoneMissing_doesNotUpdate")
  void verifySmsRegisterCode_phoneMissing_doesNotUpdate() {
    when(userMapper.findByUserId(USERNAME)).thenReturn(Optional.of(userOf(null, "EMAIL")));
    when(smsAuthService.verifyCodeAndGetPhone(SEQ, CODE)).thenReturn(null);

    assertThatThrownBy(() -> sut.verifySmsRegisterCode(USERNAME, CODE))
        .isInstanceOf(BusinessException.class);

    verify(userMapper, never()).updateLoginPhone(anyInt(), any());
    verify(userMapper, never()).updateDefaultTwoFactorMethod(anyInt(), anyString());
  }

  // ==================== deactivateSms ====================

  @Test
  @DisplayName("deactivateSms_clearsLoginPhone_andSetsDefaultEmail")
  void deactivateSms_clearsLoginPhone_andSetsDefaultEmail() {
    when(userMapper.findByUserId(USERNAME)).thenReturn(Optional.of(userOf("encrypted", "SMS")));

    sut.deactivateSms(USERNAME);

    verify(userMapper, times(1)).updateLoginPhone(SEQ, null);
    verify(userMapper, times(1)).updateDefaultTwoFactorMethod(SEQ, "EMAIL");
  }

  // ==================== setDefaultChannel (C10) ====================

  @Test
  @DisplayName("setDefaultChannel_emailToSms_withLoginPhone_succeeds")
  void setDefaultChannel_emailToSms_withLoginPhone_succeeds() {
    when(userMapper.findByUserId(USERNAME))
        .thenReturn(Optional.of(userOf("encryptedPhone", "EMAIL")));

    sut.setDefaultChannel(USERNAME, "SMS");

    verify(userMapper, times(1)).updateDefaultTwoFactorMethod(SEQ, "SMS");
  }

  @Test
  @DisplayName("setDefaultChannel_emailToSms_withoutLoginPhone_throws (C10)")
  void setDefaultChannel_emailToSms_withoutLoginPhone_throws() {
    when(userMapper.findByUserId(USERNAME)).thenReturn(Optional.of(userOf(null, "EMAIL")));

    assertThatThrownBy(() -> sut.setDefaultChannel(USERNAME, "SMS"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("휴대폰");

    verify(userMapper, never()).updateDefaultTwoFactorMethod(anyInt(), anyString());
  }

  @Test
  @DisplayName("setDefaultChannel_smsToEmail_alwaysSucceeds")
  void setDefaultChannel_smsToEmail_alwaysSucceeds() {
    when(userMapper.findByUserId(USERNAME)).thenReturn(Optional.of(userOf("encrypted", "SMS")));

    sut.setDefaultChannel(USERNAME, "EMAIL");

    verify(userMapper, times(1)).updateDefaultTwoFactorMethod(SEQ, "EMAIL");
  }

  @Test
  @DisplayName("setDefaultChannel_invalidChannel_throws")
  void setDefaultChannel_invalidChannel_throws() {
    assertThatThrownBy(() -> sut.setDefaultChannel(USERNAME, "INVALID"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("채널");

    verify(userMapper, never()).updateDefaultTwoFactorMethod(anyInt(), anyString());
  }

  // ==================== getSettings ====================

  @Test
  @DisplayName("getSettings_smsRegistered_returnsMaskedPhone")
  void getSettings_smsRegistered_returnsMaskedPhone() {
    String encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(PHONE));
    String encryptedEmail = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256("user@example.com"));
    when(userMapper.findByUserId(USERNAME))
        .thenReturn(Optional.of(userOf(encryptedPhone, "SMS", encryptedEmail)));

    TwoFactorSettingsResponse response = sut.getSettings(USERNAME);

    assertThat(response.getDefaultChannel()).isEqualTo("SMS");
    assertThat(response.isSmsRegistered()).isTrue();
    assertThat(response.getMaskedPhone()).isNotNull();
    assertThat(response.getMaskedPhone()).contains("*");
    assertThat(response.getMaskedEmail()).isNotNull();
    assertThat(response.getMaskedEmail()).contains("*");
  }

  @Test
  @DisplayName("getSettings_smsNotRegistered_returnsNullPhone")
  void getSettings_smsNotRegistered_returnsNullPhone() {
    String encryptedEmail = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256("user@example.com"));
    when(userMapper.findByUserId(USERNAME))
        .thenReturn(Optional.of(userOf(null, "EMAIL", encryptedEmail)));

    TwoFactorSettingsResponse response = sut.getSettings(USERNAME);

    assertThat(response.getDefaultChannel()).isEqualTo("EMAIL");
    assertThat(response.isSmsRegistered()).isFalse();
    assertThat(response.getMaskedPhone()).isNull();
    assertThat(response.getMaskedEmail()).contains("*");
  }

  // ==================== Helpers ====================

  private User userOf(String loginPhone, String defaultChannel) {
    return userOf(loginPhone, defaultChannel, null);
  }

  private User userOf(String loginPhone, String defaultChannel, String email) {
    return User.builder()
        .seq(SEQ)
        .userId(USERNAME)
        .loginPhone(loginPhone)
        .defaultTwoFactorMethod(defaultChannel)
        .email(email)
        .build();
  }
}
