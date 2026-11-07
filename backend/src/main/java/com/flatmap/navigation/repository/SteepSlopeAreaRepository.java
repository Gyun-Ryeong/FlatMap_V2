package com.flatmap.navigation.repository;

import com.flatmap.navigation.entity.SteepSlopeArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SteepSlopeAreaRepository extends JpaRepository<SteepSlopeArea, Long> {

    List<SteepSlopeArea> findBySource(String source);

    List<SteepSlopeArea> findByRegionCode(String regionCode);

    List<SteepSlopeArea> findByRegionCodeStartingWith(String regionCodePrefix);

    long countBySource(String source);

    void deleteBySource(String source);
}
