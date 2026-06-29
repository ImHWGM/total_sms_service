# =====================================================================
# WiseAd BE - 개발(dev) 비밀값을 AWS SSM Parameter Store 에 등록한다.
#   경로: /wisead/be/dev/<KEY>   타입: SecureString
#
# 사용법:
#   1) AWS CLI 설치 + `aws configure` 로 등록 권한 있는 자격 설정 (ap-northeast-2)
#   2) 아래 $values 해시테이블에 실제 dev 값 채우기
#   3) PowerShell 에서:  .\put-parameters-dev.ps1
#
# 주의:
#   - 이 스크립트에 실제 값을 채운 채로 커밋하지 말 것(커밋 전 값 비우기).
#   - --overwrite 라 재실행 시 값이 갱신됨.
#   - SecureString 기본 KMS 키(alias/aws/ssm) 사용. 환경별 CMK 쓰려면 --key-id 추가.
# =====================================================================

$ErrorActionPreference = "Stop"
$region = "ap-northeast-2"
$prefix = "/wisead/be/dev"

# application.properties 의 ${...} 플레이스홀더와 키 이름이 글자 그대로 일치해야 함.
$values = [ordered]@{
  "DB_PRIMARY_USERNAME"       = ""
  "DB_PRIMARY_PASSWORD"       = ""
  "DB_SMS_USERNAME"           = ""
  "DB_SMS_PASSWORD"           = ""
  "JWT_SECRET"                = ""
  "AUTH_SESSION_KEY_SECRET"   = ""
  "MAIL_HIWORKS_OFFICE_TOKEN" = ""
  "SMS_API_ID"                = ""
  "SMS_API_PW"                = ""
  "SMS_API_CLIENT_ID"         = ""
  "SMS_API_CLIENT_SECRET"     = ""
  "KCP_ENC_KEY"               = ""
  "ENC_DATA_KEY"              = ""
}

foreach ($key in $values.Keys) {
  $val = $values[$key]
  if ([string]::IsNullOrEmpty($val)) {
    Write-Warning "건너뜀(값 비어있음): $key"
    continue
  }
  $name = "$prefix/$key"
  aws ssm put-parameter `
    --region $region `
    --name $name `
    --type SecureString `
    --value $val `
    --overwrite | Out-Null
  Write-Host "등록: $name"
}

Write-Host "`n완료. 확인:"
Write-Host "  aws ssm get-parameters-by-path --region $region --path $prefix --recursive --with-decryption --query `"Parameters[].Name`""
