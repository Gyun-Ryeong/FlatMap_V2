package com.flatmap.navigation.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 경사도(grade, 단위 각도 °) 값만 일괄 갱신하기 위한 요청 페이로드.
 * 급경사지 테이블에는 위험등급 컬럼이 없으므로 등급 관련 필드는 받지 않는다.
 */
public class GradeUpdateRequest {

    private List<Item> items = new ArrayList<>();

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    public static class Item {
        private Long id;

        /** null이면 경사도를 비운다 (출처가 없는 레코드). 단위는 각도(°). */
        private Double grade;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Double getGrade() { return grade; }
        public void setGrade(Double grade) { this.grade = grade; }
    }
}
