package jp.co.housekeeping.person_management.repository;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jp.co.housekeeping.person_management.model.RegisterRecord;

@Repository
public interface RegisterRecordRepository extends CrudRepository<RegisterRecord, Long> {

    @Query("SELECT * FROM register_records WHERE work_month = :workMonth ORDER BY created_at DESC")
    List<RegisterRecord> findByWorkMonth(@Param("workMonth") String workMonth);

    /**
     * 指定した年の記録をすべて取得する（work_monthは "yyyy-MM" 形式）。
     *
     * 以前は {@code work_month LIKE :yearPrefix} で、呼び出し側が "2026%" のように
     * ワイルドカードを付けて渡す約束になっていた。
     * この形は、渡す値に利用者の入力が混ざった瞬間に、"%" や "_" が
     * ワイルドカードとして解釈されて意図しない範囲が取れてしまう
     * （プレースホルダを使っていてもLIKEのワイルドカードは防げない）。
     *
     * そこでLIKEをやめ、先頭4文字を年として突き合わせる形にした。
     * ワイルドカードという概念自体が無くなるので、エスケープ漏れが起こりえない。
     */
    @Query("SELECT * FROM register_records WHERE substring(work_month from 1 for 4) = :year "
         + "ORDER BY work_month, created_at")
    List<RegisterRecord> findByYear(@Param("year") String year);

    @Query("SELECT * FROM register_records WHERE person_id = :personId")
    List<RegisterRecord> findByPersonId(@Param("personId") Long personId);
}
