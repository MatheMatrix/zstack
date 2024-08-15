package org.zstack.identity;

import org.zstack.core.db.Q;
import org.zstack.header.identity.AccessLevel;
import org.zstack.header.identity.AccountResourceRefVO;
import org.zstack.header.identity.AccountResourceRefVO_;
import org.zstack.header.vo.ResourceVO;
import org.zstack.header.vo.ResourceVO_;

import javax.persistence.Tuple;
import javax.persistence.metamodel.SingularAttribute;
import java.util.ArrayList;
import java.util.List;

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

    @SuppressWarnings("rawtypes")
    public static <R extends ResourceVO> List<Tuple> findOwnResourceTuples(Class<R> resourceType, String accountUuid, SingularAttribute... attributes) {
        return Q.New(resourceType, AccountResourceRefVO.class)
                .table0()
                    .select(attributes)
                    .eq(ResourceVO_.uuid).table1(AccountResourceRefVO_.resourceUuid)
                .table1()
                    .eq(AccountResourceRefVO_.accountUuid, accountUuid)
                    .eq(AccountResourceRefVO_.type, AccessLevel.Own)
                    .eq(AccountResourceRefVO_.resourceType, resourceType.getSimpleName())
                .listTuple();
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

    public static String findResourceOwner(String resourceUuid) {
        return Account.getAccountUuidOfResource(resourceUuid);
    }
}
