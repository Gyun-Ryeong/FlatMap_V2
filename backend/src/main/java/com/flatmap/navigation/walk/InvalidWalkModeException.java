package com.flatmap.navigation.walk;

/** 요청의 보행 유형 값이 GENERAL/WEAK 가 아닐 때. 컨트롤러가 400 으로 바꾼다. */
public class InvalidWalkModeException extends IllegalArgumentException {

    public InvalidWalkModeException() {
        super("알 수 없는 보행 유형");
    }
}
