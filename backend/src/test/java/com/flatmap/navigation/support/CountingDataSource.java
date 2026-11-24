package com.flatmap.navigation.support;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DB 로 나가는 SQL 문 개수를 세는 DataSource 래퍼(테스트 전용). N+1 이 없음을 "쿼리 수"로 확인할 때 쓴다.
 * Connection.prepareStatement / createStatement 호출 횟수 = 실행한 SQL 문 수.
 */
public final class CountingDataSource {

    private CountingDataSource() {}

    public static Counted wrap(DataSource delegate) {
        AtomicInteger statements = new AtomicInteger();
        DataSource proxy = (DataSource) Proxy.newProxyInstance(
                CountingDataSource.class.getClassLoader(), new Class<?>[]{DataSource.class},
                (p, method, args) -> {
                    Object result = invoke(delegate, method, args);
                    if (result instanceof Connection connection) {
                        return Proxy.newProxyInstance(CountingDataSource.class.getClassLoader(),
                                new Class<?>[]{Connection.class}, (cp, cm, cargs) -> {
                                    String name = cm.getName();
                                    if (name.equals("prepareStatement") || name.equals("createStatement")
                                            || name.equals("prepareCall")) {
                                        statements.incrementAndGet();
                                    }
                                    return invoke(connection, cm, cargs);
                                });
                    }
                    return result;
                });
        return new Counted(proxy, statements);
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    public record Counted(DataSource dataSource, AtomicInteger statements) {
        public int count() {
            return statements.get();
        }

        public void reset() {
            statements.set(0);
        }
    }
}
