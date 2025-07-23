package org.zstack.storage.snapshot.group;

import org.zstack.core.ScatteredValidator;
import org.zstack.header.exception.CloudRuntimeException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Created by MaJin on 2019/7/31.
 */
public class VolumeSnapshotGroupOperationValidator extends ScatteredValidator {
    private static final List<Method> creationValidateMethods;
    private static final List<Method> reversionValidateMethods;

    public enum SnapshotGroupOperation {
        CREATE,
        REVERT,
    }

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface VolumeSnapshotGroupCreationValidatorMethod {
    }

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface VolumeSnapshotGroupReversionValidatorMethod {
    }

    public static List<Method> getInvokeMethods(SnapshotGroupOperation operation) {
        if (SnapshotGroupOperation.CREATE.equals(operation)) {
            return creationValidateMethods;
        } else if (SnapshotGroupOperation.REVERT.equals(operation)) {
            return reversionValidateMethods;
        }

        throw new CloudRuntimeException(String.format("invalid operation %s", operation.toString()));
    }
    
    static {
        // method signature: static void xxx(String vmUuid, String volumeUuid)
        creationValidateMethods = collectValidatorMethods(VolumeSnapshotGroupCreationValidatorMethod.class, String.class);
        reversionValidateMethods = collectValidatorMethods(VolumeSnapshotGroupReversionValidatorMethod.class, String.class);
    }

    public static void validate(String vmUuid, SnapshotGroupOperation operation) {
        new VolumeSnapshotGroupOperationValidator().invokeValidatorMethods(getInvokeMethods(operation), vmUuid);
    }
}
