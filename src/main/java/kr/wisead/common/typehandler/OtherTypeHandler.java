package kr.wisead.common.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import kr.wisead.domain.survey.entity.OtherType;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/**
 * MyBatis TypeHandler — VARCHAR(2) ↔ {@link OtherType} 매핑 (plan §3 Phase B-2).
 *
 * <p>안전망: null/empty/unknown 값은 모두 {@link OtherType#SA}로 fallback (NPE 방지). DB CHECK 제약이 6종 enum을 강제하지만
 * 마이그레이션 직전 잔존 데이터나 외부 클라이언트의 잘못된 입력에서도 NPE를 발생시키지 않도록 안전망을 둔다.
 */
@MappedTypes(OtherType.class)
public class OtherTypeHandler extends BaseTypeHandler<OtherType> {

  @Override
  public void setNonNullParameter(
      PreparedStatement ps, int i, OtherType parameter, JdbcType jdbcType) throws SQLException {
    ps.setString(i, parameter.name());
  }

  @Override
  public OtherType getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return parse(rs.getString(columnName));
  }

  @Override
  public OtherType getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return parse(rs.getString(columnIndex));
  }

  @Override
  public OtherType getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return parse(cs.getString(columnIndex));
  }

  private OtherType parse(String value) {
    if (value == null || value.isBlank()) {
      return OtherType.SA;
    }
    try {
      return OtherType.valueOf(value);
    } catch (IllegalArgumentException e) {
      return OtherType.SA;
    }
  }
}
