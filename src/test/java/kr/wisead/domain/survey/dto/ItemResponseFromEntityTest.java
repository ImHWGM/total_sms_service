package kr.wisead.domain.survey.dto;

import static org.assertj.core.api.Assertions.assertThat;

import kr.wisead.domain.survey.entity.OtherType;
import kr.wisead.domain.survey.entity.SurveyItem;
import org.junit.jupiter.api.Test;

/** ItemResponse.from(SurveyItem) — otherType 매핑 검증 (plan §3 Phase B-5, AC-7). */
class ItemResponseFromEntityTest {

  @Test
  void from_withExplicitOtherType_preservesValue() {
    SurveyItem item =
        SurveyItem.builder()
            .itemSeq(1)
            .item("이름")
            .otherYn("Y")
            .otherType(OtherType.NE)
            .build();

    ItemResponse response = ItemResponse.from(item);

    assertThat(response.getOtherType()).isEqualTo(OtherType.NE);
    assertThat(response.getOtherYn()).isEqualTo("Y");
  }

  @Test
  void from_withNullOtherType_defaultsToSA() {
    SurveyItem item = SurveyItem.builder().itemSeq(2).item("기타").otherYn("Y").build();

    ItemResponse response = ItemResponse.from(item);

    assertThat(response.getOtherType()).isEqualTo(OtherType.SA);
  }

  @Test
  void from_eachOtherType_roundTrips() {
    for (OtherType type : OtherType.values()) {
      SurveyItem item = SurveyItem.builder().itemSeq(3).otherType(type).build();
      assertThat(ItemResponse.from(item).getOtherType()).isEqualTo(type);
    }
  }
}
