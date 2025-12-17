package kr.wisead.domain.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 사업자등록번호 유효성 검증 서비스
 * bizno.net 크롤링을 통한 검증
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessNoValidationService {

    /**
     * 사업자등록번호 유효성 검증 (크롤링 방식)
     *
     * @param bizNo 사업자등록번호 (하이픈 제외)
     * @return 검증 결과
     */
    public Map<String, Object> validateBizNo(String bizNo) {
        Map<String, Object> response = new HashMap<>();
        String url = "https://bizno.net/article/" + bizNo;

        try {
            Connection connection = Jsoup.connect(url)
                    .header("Accept",
                            "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Accept-Language", "ko-KR,kr;q=0.5")
                    .header("Connection", "keep-alive")
                    .header("Host", "bizno.net")
                    .header("Referer", "https://bizno.net/")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .timeout(10000);

            Document doc = connection.get();

            // 회사 이름 가져오기
            String companyName = getTextContent(doc, "div.titles h1");

            // 사업자 현재 상태 가져오기
            String companyStatus = getTextContent(doc, "tr:contains(사업자 현재 상태) td span");

            // 사업자등록번호 가져오기
            String bizNumber = getTextContent(doc, "tr:contains(사업자등록번호) td span");

            // 사업자 전화번호 가져오기
            String bizTel = getTextContent(doc, "tr:contains(전화번호) td a");

            // 회사 주소 가져오기
            Element addressElement = doc.selectFirst("tr:contains(회사주소) td");
            String address = extractAddress(addressElement);

            Map<String, String> data = new HashMap<>();

            if (companyName != null && companyStatus != null && address != null && bizNumber != null) {
                if (companyStatus.contains("폐업")) {
                    response.put("status_code", "ERROR");
                    data.put("error", "폐업자로 조회되는 사업자등록번호입니다");
                } else if (companyStatus.contains("휴업")) {
                    response.put("status_code", "ERROR");
                    data.put("error", "휴업자로 조회되는 사업자등록번호입니다");
                } else {
                    data.put("companyName", companyName);
                    data.put("companyStatus", companyStatus);
                    data.put("address", address);
                    data.put("bizNumber", bizNumber);
                    data.put("bizTell", bizTel);
                    response.put("status_code", "OK");
                }
            } else {
                String noResult = getTextContent(doc, "h4 span[style='color: blue;font-size:10pt']");

                if (noResult != null && noResult.contains("국세청에 등록되지 않은 사업자등록번호")) {
                    response.put("status_code", "ERROR");
                    data.put("error", "국세청에 등록되지 않은 사업자등록번호입니다");
                } else if (noResult != null && noResult.contains("사업자상태 : 계속사업자")) {
                    response.put("status_code", "OK");
                    data.put("bizNumber", bizNumber);
                    data.put("companyStatus", "계속사업자");
                } else {
                    response.put("status_code", "ERROR");
                    data.put("error", "알 수 없는 오류가 발생했습니다");
                }
            }

            response.put("data", data);

        } catch (IOException e) {
            log.error("사업자등록번호 검증 크롤링 중 오류 발생: {}", e.getMessage());
            response.put("status_code", "ERROR");
            Map<String, String> data = new HashMap<>();
            data.put("error", "사업자등록번호 조회 중 오류가 발생했습니다");
            response.put("data", data);
        }

        return response;
    }

    /**
     * 주소 추출 (br 태그 이전까지)
     */
    private String extractAddress(Element addressElement) {
        if (addressElement != null) {
            StringBuilder address = new StringBuilder();
            for (Node node : addressElement.childNodes()) {
                if (node.nodeName().equals("br")) {
                    break;
                }
                address.append(node.toString());
            }
            return address.toString().trim();
        }
        return null;
    }

    /**
     * CSS 선택자로 텍스트 추출
     */
    private String getTextContent(Document doc, String cssSelector) {
        Element element = doc.selectFirst(cssSelector);
        if (element != null) {
            log.debug("CSS Selector: {} -> Text: {}", cssSelector, element.text());
        } else {
            log.debug("CSS Selector: {} -> Element not found", cssSelector);
        }
        return element != null ? element.text() : null;
    }
}
