package org.zstack.identity;

import org.zstack.core.db.Q;
import org.zstack.core.db.QueryMore;
import org.zstack.header.identity.AccessLevel;
import org.zstack.header.identity.AccountResourceRefVO;
import org.zstack.header.identity.AccountResourceRefVO_;
import org.zstack.header.vo.ResourceVO;
import org.zstack.header.vo.ResourceVO_;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ResourceHelper {
    private ResourceHelper() {}

    public static <R extends ResourceVO> long countOwnResources(Class<R> resourceType, String accountUuid) {
        Long count = Q.New(resourceType, AccountResourceRefVO.class)
                .table0()
                    .selectThisTable()
                    .eq(ResourceVO_.uuid).table1(AccountResourceRefVO_.resourceUuid)
                .table1()
                    .eq(AccountResourceRefVO_.accountUuid, accountUuid)
                    .eq(AccountResourceRefVO_.type, AccessLevel.Own)
                .count();
        return count == null ? 0L : count;
    }

    public static <R extends ResourceVO> long countOwnResources(Class<R> resourceType, String accountUuid, Consumer<QueryMore> consumer) {
        QueryMore q = Q.New(resourceType, AccountResourceRefVO.class)
                .table1()
                    .eq(AccountResourceRefVO_.accountUuid, accountUuid)
                    .eq(AccountResourceRefVO_.type, AccessLevel.Own)
                .table0()
                    .selectThisTable()
                    .eq(ResourceVO_.uuid).table1(AccountResourceRefVO_.resourceUuid);

        consumer.accept(q);
        return q.count();
    }

    public static <R extends ResourceVO> List<String> findOwnResourceUuidList(Class<R> resourceType, String accountUuid) {
        return Q.New(resourceType, AccountResourceRefVO.class)
                .table0()
                    .select(ResourceVO_.uuid)
                    .eq(ResourceVO_.uuid).table1(AccountResourceRefVO_.resourceUuid)
                .table1()
                    .eq(AccountResourceRefVO_.accountUuid, accountUuid)
                    .eq(AccountResourceRefVO_.type, AccessLevel.Own)
                    .eq(AccountResourceRefVO_.resourceType, resourceType.getSimpleName())
                .list();
    }

    public static <R extends ResourceVO> List<String> findOwnResourceUuidList(Class<R> resourceType, List<String> accountUuids) {
        return Q.New(resourceType, AccountResourceRefVO.class)
                .table0()
                    .select(ResourceVO_.uuid)
                    .eq(ResourceVO_.uuid).table1(AccountResourceRefVO_.resourceUuid)
                .table1()
                    .in(AccountResourceRefVO_.accountUuid, accountUuids)
                    .eq(AccountResourceRefVO_.type, AccessLevel.Own)
                    .eq(AccountResourceRefVO_.resourceType, resourceType.getSimpleName())
                .list();
    }

    public static <R extends ResourceVO> List<String> findOwnResourceUuidList(
            Class<R> resourceType, List<String> accountUuids, Consumer<QueryMore> consumer) {
        QueryMore q = Q.New(resourceType, AccountResourceRefVO.class)
                .table1()
                    .in(AccountResourceRefVO_.accountUuid, accountUuids)
                    .eq(AccountResourceRefVO_.type, AccessLevel.Own)
                .table0()
                    .select(ResourceVO_.uuid)
                    .eq(ResourceVO_.uuid).table1(AccountResourceRefVO_.resourceUuid);
        consumer.accept(q);
        return q.list();
    }

    public static <R extends ResourceVO> List<R> findOwnResources(Class<R> resourceType, String accountUuid) {
        return Q.New(resourceType, AccountResourceRefVO.class)
                .table0()
                    .selectThisTable()
                    .eq(ResourceVO_.uuid).table1(AccountResourceRefVO_.resourceUuid)
                .table1()
                    .eq(AccountResourceRefVO_.accountUuid, accountUuid)
                    .eq(AccountResourceRefVO_.type, AccessLevel.Own)
                    .eq(AccountResourceRefVO_.resourceType, resourceType.getSimpleName())
                .list();
    }

    public static <R extends ResourceVO> List<R> findOwnResources(Class<R> resourceType, List<String> accountUuids) {
        if (accountUuids.isEmpty()) {
            return new ArrayList<>();
        }

        return Q.New(resourceType, AccountResourceRefVO.class)
                .table0()
                    .selectThisTable()
                    .eq(ResourceVO_.uuid).table1(AccountResourceRefVO_.resourceUuid)
                .table1()
                    .in(AccountResourceRefVO_.accountUuid, accountUuids)
                    .eq(AccountResourceRefVO_.type, AccessLevel.Own)
                    .eq(AccountResourceRefVO_.resourceType, resourceType.getSimpleName())
                .list();
    }

    public static <R extends ResourceVO> List<R> findOwnResources(Class<R> resourceType, String accountUuid, Consumer<QueryMore> consumer) {
        QueryMore q = Q.New(resourceType, AccountResourceRefVO.class)
                .table1()
                    .eq(AccountResourceRefVO_.accountUuid, accountUuid)
                    .eq(AccountResourceRefVO_.type, AccessLevel.Own)
                    .eq(AccountResourceRefVO_.resourceType, resourceType.getSimpleName())
                .table0()
                    .selectThisTable()
                    .eq(ResourceVO_.uuid).table1(AccountResourceRefVO_.resourceUuid);

        consumer.accept(q);
        return q.list();
    }

    public static String findResourceOwner(String resourceUuid) {
        return Account.getAccountUuidOfResource(resourceUuid);
    }
}
