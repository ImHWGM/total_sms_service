package kr.wisead.common.util;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 비밀번호 복잡도 검증 유틸리티
 */
public class PasswordValidator {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 20;
    private static final int MIN_CHAR_TYPES = 3;
    private static final int MAX_CONSECUTIVE = 3;

    // 정규식 패턴
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");

    // 연속 문자 패턴 (키보드 순서)
    private static final String[] SEQUENTIAL_CHARS = {
            "abc", "bcd", "cde", "def", "efg", "fgh", "ghi", "hij", "ijk",
            "jkl", "klm", "lmn", "mno", "nop", "opq", "pqr", "qrs", "rst",
            "stu", "tuv", "uvw", "vwx", "wxy", "xyz",
            "012", "123", "234", "345", "456", "567", "678", "789",
            "qwe", "wer", "ert", "rty", "tyu", "yui", "uio", "iop",
            "asd", "sdf", "dfg", "fgh", "ghj", "hjk", "jkl",
            "zxc", "xcv", "cvb", "vbn", "bnm"
    };

    private PasswordValidator() {
        // 유틸리티 클래스
    }

    /**
     * 비밀번호 복잡도 검증
     *
     * @param password 검증할 비밀번호
     * @throws BusinessException 복잡도 요구사항 미충족 시
     */
    public static void validate(String password) {
        List<String> errors = validateAndGetErrors(password);
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, String.join(" ", errors));
        }
    }

    /**
     * 비밀번호 복잡도 검증 (오류 메시지 목록 반환)
     *
     * @param password 검증할 비밀번호
     * @return 오류 메시지 목록 (비어있으면 통과)
     */
    public static List<String> validateAndGetErrors(String password) {
        List<String> errors = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            errors.add("비밀번호를 입력해주세요.");
            return errors;
        }

        // 1. 길이 검사
        if (password.length() < MIN_LENGTH) {
            errors.add(String.format("비밀번호는 %d자 이상이어야 합니다.", MIN_LENGTH));
        }
        if (password.length() > MAX_LENGTH) {
            errors.add(String.format("비밀번호는 %d자 이하여야 합니다.", MAX_LENGTH));
        }

        // 2. 문자 종류 검사 (대문자, 소문자, 숫자, 특수문자 중 3가지 이상)
        int charTypes = 0;
        if (UPPERCASE.matcher(password).find()) charTypes++;
        if (LOWERCASE.matcher(password).find()) charTypes++;
        if (DIGIT.matcher(password).find()) charTypes++;
        if (SPECIAL.matcher(password).find()) charTypes++;

        if (charTypes < MIN_CHAR_TYPES) {
            errors.add("비밀번호는 영문 대문자, 소문자, 숫자, 특수문자 중 3가지 이상을 포함해야 합니다.");
        }

        // 3. 동일 문자 연속 검사
        if (hasConsecutiveSameChars(password)) {
            errors.add("동일 문자를 3개 이상 연속 사용할 수 없습니다.");
        }

        // 4. 연속 문자/숫자 검사 (abc, 123 등)
        if (hasSequentialChars(password)) {
            errors.add("연속된 문자나 숫자(abc, 123 등)를 3개 이상 사용할 수 없습니다.");
        }

        return errors;
    }

    /**
     * 비밀번호 유효성 여부 반환
     */
    public static boolean isValid(String password) {
        return validateAndGetErrors(password).isEmpty();
    }

    /**
     * 동일 문자 연속 검사
     */
    private static boolean hasConsecutiveSameChars(String password) {
        if (password.length() < MAX_CONSECUTIVE) {
            return false;
        }

        char prevChar = password.charAt(0);
        int count = 1;

        for (int i = 1; i < password.length(); i++) {
            char currentChar = password.charAt(i);
            if (currentChar == prevChar) {
                count++;
                if (count >= MAX_CONSECUTIVE) {
                    return true;
                }
            } else {
                count = 1;
                prevChar = currentChar;
            }
        }
        return false;
    }

    /**
     * 연속 문자/숫자 검사
     */
    private static boolean hasSequentialChars(String password) {
        String lowerPassword = password.toLowerCase();

        for (String seq : SEQUENTIAL_CHARS) {
            if (lowerPassword.contains(seq)) {
                return true;
            }
            // 역순도 검사
            String reversed = new StringBuilder(seq).reverse().toString();
            if (lowerPassword.contains(reversed)) {
                return true;
            }
        }
        return false;
    }
}
