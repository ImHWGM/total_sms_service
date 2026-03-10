package kr.wisead.common.util;

/**
 * 전화번호 포맷 관련 유틸리티
 */
public class PhoneUtils {

    private PhoneUtils() {
        // 유틸리티 클래스 인스턴스화 방지
    }

    /**
     * 전화번호 하이픈 추가 (표준 포맷)
     * 예: 01012345678 -> 010-1234-5678, 021234567 -> 02-123-4567
     * 
     * @param phone 원본 전화번호
     * @return 하이픈이 포함된 전화번호
     */
    public static String format(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return "";
        }

        // 숫자만 추출
        String clean = phone.replaceAll("[^0-9]", "");

        if (clean.length() == 11) {
            // 010-1234-5678
            return clean.substring(0, 3) + "-" + clean.substring(3, 7) + "-" + clean.substring(7);
        } else if (clean.length() == 10) {
            if (clean.startsWith("02")) {
                // 02-1234-5678
                return clean.substring(0, 2) + "-" + clean.substring(2, 6) + "-" + clean.substring(6);
            } else {
                // 010-123-4567, 031-123-4567
                return clean.substring(0, 3) + "-" + clean.substring(3, 6) + "-" + clean.substring(6);
            }
        } else if (clean.length() == 9 && clean.startsWith("02")) {
            // 02-123-4567
            return clean.substring(0, 2) + "-" + clean.substring(2, 5) + "-" + clean.substring(5);
        } else if (clean.length() == 8) {
            // 1588-1234 등 대표번호
            return clean.substring(0, 4) + "-" + clean.substring(4);
        }

        // 처리 불가능한 길이는 원본 또는 정제된 번호 반환
        return clean.isEmpty() ? phone : clean;
    }

    /**
     * 전화번호 마스킹 처리 (010-****-1234 형식)
     * 
     * @param phone 원본 전화번호
     * @return 마스킹된 전화번호
     */
    public static String mask(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return "";
        }

        // 먼저 표준 포맷으로 변환 시도
        String formatted = format(phone);

        // 010-1234-5678 형태라면 가운데 4자리 마스킹
        if (formatted.contains("-")) {
            String[] parts = formatted.split("-");
            if (parts.length == 3) {
                // 일반적인 3단 구성 (010-1234-5678)
                return parts[0] + "-****-" + parts[2];
            } else if (parts.length == 2) {
                // 2단 구성 (1588-1234 -> 1588-****)
                return parts[0] + "-****";
            }
        }

        // 포맷팅이 안 된 경우 수동 마스킹
        if (phone.length() >= 7) {
            return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
        }

        return phone;
    }
}
