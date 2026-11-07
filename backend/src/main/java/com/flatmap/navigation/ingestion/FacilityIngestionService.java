package com.flatmap.navigation.ingestion;

import com.flatmap.navigation.model.SafetyFacility;
import com.flatmap.navigation.repository.SafetyFacilityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 안전시설 수집·적재. 소스마다 독립된 트랜잭션이라 한 소스가 실패해도 나머지는 진행된다.
 *
 * <ol>
 *   <li>읽기(외부 호출, 트랜잭션 밖): 성남 필터·소방서 제외 등은 리더가 한다.</li>
 *   <li>후보의 나쁜 행(빈 값/{@code ?}/깨진 문자) 비율이 5% 초과면 적재하지 않고 실패.</li>
 *   <li>한 트랜잭션에서 INSERT ... ON CONFLICT (facility_type, location) DO NOTHING 으로 적재(멱등).</li>
 *   <li>적재 후 그 소스의 DB 행 전체에 같은 검사를 하고, 5% 초과면 예외로 롤백한다.</li>
 * </ol>
 * 로그에는 키·URL·응답 본문을 남기지 않는다. 예외는 IngestionException 의 메시지 또는 클래스 이름만 기록한다.
 */
@Service
public class FacilityIngestionService {

    private static final Logger log = LoggerFactory.getLogger(FacilityIngestionService.class);

    private final Map<FacilitySource, FacilityReader> readers = new EnumMap<>(FacilitySource.class);
    private final SafetyFacilityRepository repository;
    private final TransactionTemplate tx;

    public FacilityIngestionService(List<FacilityReader> readerList, SafetyFacilityRepository repository,
                                    PlatformTransactionManager transactionManager) {
        for (FacilityReader r : readerList) readers.put(r.source(), r);
        this.repository = repository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** 모든 소스를 순서대로 동기화한다. 한 소스의 실패가 다른 소스를 막지 않는다. */
    public List<SourceResult> syncAll() {
        List<SourceResult> results = new ArrayList<>();
        for (FacilitySource source : FacilitySource.values()) results.add(sync(source));
        return results;
    }

    public SourceResult sync(FacilitySource source) {
        String code = source.code();
        FacilityReader reader = readers.get(source);
        if (reader == null) {
            log.error("[ingestion][{}] 리더가 등록되지 않음", code);
            return SourceResult.failed(source);
        }
        try {
            SourceReading reading = reader.read();
            log.info("[ingestion][{}] 읽음 {}건, 제외 {}건 {}, 적재 후보 {}건", code, reading.read(),
                    reading.excluded(), reading.excludedByReason(), reading.facilities().size());

            if (reading.facilities().isEmpty()) {
                throw new IngestionException("no rows accepted");
            }
            double batchBad = TextQuality.badRatio(reading.facilities(), source.addressRequired());
            if (batchBad > TextQuality.MAX_BAD_RATIO) {
                throw new IngestionException(String.format("text quality check failed on batch (bad=%.1f%%)", batchBad * 100));
            }

            int inserted = insertAndVerify(source, reading.facilities());
            log.info("[ingestion][{}] 적재 {}건 (중복 좌표로 건너뜀 {}건)", code, inserted,
                    reading.facilities().size() - inserted);
            return SourceResult.success(source, reading, inserted);
        } catch (Exception e) {
            // 메시지는 우리가 만든 IngestionException 만 남긴다. 그 외 예외 메시지에는 URL·SQL 이 섞일 수 있다.
            String reason = e instanceof IngestionException ? e.getMessage() : e.getClass().getSimpleName();
            log.error("[ingestion][{}] 동기화 실패 — {}", code, reason);
            return SourceResult.failed(source);
        }
    }

    private int insertAndVerify(FacilitySource source, List<SafetyFacility> rows) {
        Integer inserted = tx.execute(status -> {
            int n = repository.insertIgnoringDuplicates(rows);
            long[] counts = repository.badTextCounts(source.code(), source.addressRequired());
            if (TextQuality.exceedsLimit(counts[1], counts[0])) {
                // 예외로 트랜잭션을 롤백한다 — 이 소스의 이번 적재는 남지 않는다.
                throw new IngestionException(String.format(
                        "text quality check failed after load (bad=%d of %d)", counts[1], counts[0]));
            }
            return n;
        });
        return inserted == null ? 0 : inserted;
    }
}
