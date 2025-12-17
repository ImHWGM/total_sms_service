package kr.wisead.mapper.primary;

import kr.wisead.domain.user.entity.PasswordHint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * 비밀번호 힌트 Mapper
 */
@Mapper
public interface PasswordHintMapper {

    /**
     * 사용자 시퀀스로 비밀번호 힌트 조회
     */
    Optional<PasswordHint> findByMngSeq(@Param("mngSeq") Long mngSeq);

    /**
     * 비밀번호 힌트 등록
     */
    int insert(PasswordHint passwordHint);

    /**
     * 비밀번호 힌트 수정
     */
    int update(PasswordHint passwordHint);

    /**
     * 비밀번호 힌트 검증 (질문과 답변이 일치하는지 확인)
     */
    int verifyHint(@Param("mngSeq") Long mngSeq,
                   @Param("hintQuestion") String hintQuestion,
                   @Param("hintAnswer") String hintAnswer);
}
