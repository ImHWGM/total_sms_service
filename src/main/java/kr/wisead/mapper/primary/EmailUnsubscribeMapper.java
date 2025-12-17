package kr.wisead.mapper.primary;

import kr.wisead.domain.email.entity.EmailUnsubscribe;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * 이메일 수신거부 Mapper
 */
@Mapper
public interface EmailUnsubscribeMapper {

    /**
     * 이메일로 수신거부 조회
     */
    Optional<EmailUnsubscribe> findByEmail(@Param("email") String email);

    /**
     * 수신거부 등록
     */
    int insert(EmailUnsubscribe emailUnsubscribe);

    /**
     * 수신거부 여부 확인
     */
    boolean existsByEmail(@Param("email") String email);
}
