-- ============================================================================
-- V2__rename_legacy_facility_sources.sql : 01 단계(ingestion)
--
-- data_source 코드값을 소스별 고정 영문 코드값으로 통일한다.
--  * 옛 수집 코드가 GYEONGGI_DATADREAM_SECRTLGT / GYEONGGI_DATADREAM_CCTV 로 적재한 행을
--    새 코드값(GG_SECURITY_LIGHT / GG_CCTV)으로 이름만 바꾼다. 데이터는 지우지 않는다.
--  * 같은 좌표를 새 수집이 다시 만나면 ON CONFLICT DO NOTHING 으로 건너뛰므로 중복이 생기지 않는다.
--  * 새로 만든 DB(V1 직후)에는 행이 없으므로 아무 일도 하지 않는다.
-- ============================================================================
UPDATE safety_facility SET data_source = 'GG_SECURITY_LIGHT', updated_at = now()
WHERE data_source = 'GYEONGGI_DATADREAM_SECRTLGT';

UPDATE safety_facility SET data_source = 'GG_CCTV', updated_at = now()
WHERE data_source = 'GYEONGGI_DATADREAM_CCTV';
