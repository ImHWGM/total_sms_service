package kr.wisead.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원가입 요청 DTO */
@Getter
@NoArgsConstructor
public class SignUpRequest {

  @NotBlank(message = "아이디를 입력해주세요.")
  @Size(min = 4, max = 20, message = "아이디는 4~20자로 입력해주세요.")
  @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "아이디는 영문, 숫자만 사용 가능합니다.")
  private String userId;

  @NotBlank(message = "비밀번호를 입력해주세요.")
  @Size(min = 8, max = 20, message = "비밀번호는 8~20자로 입력해주세요.")
  @Pattern(
      regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]+$",
      message = "비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다.")
  private String userPass;

  @NotBlank(message = "비밀번호 확인을 입력해주세요.")
  private String userPassConfirm;

  @NotBlank(message = "기업명을 입력해주세요.")
  @Size(max = 30, message = "기업명은 30자 이하로 입력해주세요.")
  private String corpName;

  @NotBlank(message = "기업 주소를 입력해주세요.")
  @Size(max = 250, message = "주소는 250자 이하로 입력해주세요.")
  private String corpAddr;

  @NotBlank(message = "사업자등록번호를 입력해주세요.")
  @Pattern(
      regexp = "^\\d{3}-?\\d{2}-?\\d{5}$",
      message = "사업자등록번호 형식이 올바르지 않습니다. (예: 2158719169 또는 215-87-19169)")
  private String bizNum;

  @NotBlank(message = "사업자 전화번호를 입력해주세요.")
  @Size(max = 20, message = "전화번호는 20자 이하로 입력해주세요.")
  private String bizTel;

  @NotBlank(message = "담당자명을 입력해주세요.")
  @Size(max = 100, message = "담당자명은 100자 이하로 입력해주세요.")
  private String person;

  @NotBlank(message = "담당자 연락처를 입력해주세요.")
  @Pattern(regexp = "^010-?[2-9]\\d{3}-?\\d{4}$", message = "연락처 형식이 올바르지 않습니다. (예: 010-2345-6789)")
  private String phone;

  @NotBlank(message = "이메일을 입력해주세요.")
  @Email(message = "이메일 형식이 올바르지 않습니다.")
  @Size(max = 100, message = "이메일은 100자 이하로 입력해주세요.")
  private String email;

  // 비밀번호 힌트
  private String hintQuestion;
  private String hintAnswer;

  @Builder
  public SignUpRequest(
      String userId,
      String userPass,
      String userPassConfirm,
      String corpName,
      String corpAddr,
      String bizNum,
      String bizTel,
      String person,
      String phone,
      String email,
      String hintQuestion,
      String hintAnswer) {
    this.userId = userId;
    this.userPass = userPass;
    this.userPassConfirm = userPassConfirm;
    this.corpName = corpName;
    this.corpAddr = corpAddr;
    this.bizNum = bizNum;
    this.bizTel = bizTel;
    this.person = person;
    this.phone = phone;
    this.email = email;
    this.hintQuestion = hintQuestion;
    this.hintAnswer = hintAnswer;
  }

  /** 비밀번호 일치 여부 확인 */
  public boolean isPasswordMatched() {
    return userPass != null && userPass.equals(userPassConfirm);
  }
}
