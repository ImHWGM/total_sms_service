package kr.wisead.common.util;

import java.security.SecureRandom;

public class MemberUtil {

    private static final SecureRandom random = new SecureRandom();

    public static String generateStoreCode() {
        // 첫 자리는 1~9, 나머지는 0~9로 5자리 생성
        int firstDigit = random.nextInt(9) + 1;
        int remainingDigits = random.nextInt(10000);
        return String.format("%d%04d", firstDigit, remainingDigits);
    }
}
