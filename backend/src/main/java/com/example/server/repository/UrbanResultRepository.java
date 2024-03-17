package com.example.server.repository;

import com.example.server.entity.TimeMapping;
import com.example.server.entity.UrbanResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UrbanResultRepository extends JpaRepository<UrbanResult, String> {

    @Query(
            value = "SELECT vid FROM urban_results WHERE search_string(frame_by_obj_str, :searchStr) = True",
            nativeQuery=true
    )
    List<UrbanMapping> findSearchString(String searchStr);

    @Query(
            value = "SELECT vid FROM urban_results WHERE search_bits(id, :bitNum) = True",
            nativeQuery=true
    )
    List<UrbanMapping> findSearchBit(int bitNum);

    @Query(
            value = "SELECT vid FROM urban_results WHERE search_bitstring(id, :bitNum) = True",
            nativeQuery=true
    )
    List<UrbanMapping> findSearchBitNum(int[] bitNum);

    @Query(
            value = "SELECT vid FROM urban_results WHERE search_final(id, :num, :numArr) = True",
            nativeQuery=true
    )
    List<String> searchFinal(int num, int[] numArr);

    @Query(
            value = "SELECT search_times(id, :num, :numArr) FROM urban_results WHERE vid = :id ",
            nativeQuery=true
    )
    String searchTime(String id, int num, int[] numArr);
}
