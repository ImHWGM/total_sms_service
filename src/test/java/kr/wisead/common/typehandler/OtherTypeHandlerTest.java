package kr.wisead.common.typehandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import kr.wisead.domain.survey.entity.OtherType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OtherTypeHandlerTest {

  @Mock private ResultSet rs;
  @Mock private PreparedStatement ps;

  private final OtherTypeHandler handler = new OtherTypeHandler();

  @Test
  void getNullableResult_nullColumn_returnsSA() throws SQLException {
    when(rs.getString("OTHER_TYPE")).thenReturn(null);
    assertThat(handler.getNullableResult(rs, "OTHER_TYPE")).isEqualTo(OtherType.SA);
  }

  @Test
  void getNullableResult_emptyColumn_returnsSA() throws SQLException {
    when(rs.getString("OTHER_TYPE")).thenReturn("");
    assertThat(handler.getNullableResult(rs, "OTHER_TYPE")).isEqualTo(OtherType.SA);
  }

  @Test
  void getNullableResult_blankColumn_returnsSA() throws SQLException {
    when(rs.getString("OTHER_TYPE")).thenReturn("  ");
    assertThat(handler.getNullableResult(rs, "OTHER_TYPE")).isEqualTo(OtherType.SA);
  }

  @Test
  void getNullableResult_invalidValue_returnsSA() throws SQLException {
    when(rs.getString("OTHER_TYPE")).thenReturn("XX");
    assertThat(handler.getNullableResult(rs, "OTHER_TYPE")).isEqualTo(OtherType.SA);
  }

  @Test
  void getNullableResult_eachValidCode_roundTrips() throws SQLException {
    for (OtherType type : OtherType.values()) {
      when(rs.getString("OTHER_TYPE")).thenReturn(type.name());
      assertThat(handler.getNullableResult(rs, "OTHER_TYPE")).isEqualTo(type);
    }
  }

  @Test
  void getNullableResult_byColumnIndex_works() throws SQLException {
    when(rs.getString(1)).thenReturn("SO");
    assertThat(handler.getNullableResult(rs, 1)).isEqualTo(OtherType.SO);
  }

  @Test
  void setNonNullParameter_eachValidCode_writesName() throws SQLException {
    for (OtherType type : OtherType.values()) {
      handler.setNonNullParameter(ps, 1, type, null);
      verify(ps).setString(1, type.name());
    }
  }
}
