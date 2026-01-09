package org.zstack.core.gc;

import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.Platform;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.EventFacade;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.core.db.SQL;
import org.zstack.core.errorcode.ErrorFacade;
import org.zstack.core.thread.SyncThread;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.utils.FieldUtils;
import org.zstack.utils.TaskContext;
import org.zstack.utils.Utils;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.zstack.core.Platform.inerr;

/**
 * Created by xing5 on 2017/3/3.
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public abstract class GarbageCollector {
    static final CLogger logger = Utils.getLogger(GarbageCollector.class);

    @Autowired
    protected DatabaseFacade dbf;
    @Autowired
    protected CloudBus bus;
    @Autowired
    protected ErrorFacade errf;
    @Autowired
    protected EventFacade evtf;
    @Autowired
    GarbageCollectorManagerImpl gcMgr;

    Runnable canceller;
    private AtomicBoolean lockJob = new AtomicBoolean(false);

    // override in the subclass to give a name to the GC job
    public String NAME = getClass().getName();
    public int EXECUTED_TIMES;
    String uuid;

    public String getUuid() {
        return uuid;
    }

    protected abstract void triggerNow(GCCompletion completion);

    protected boolean lock() {
        return lockJob.compareAndSet(false, true);
    }

    protected void unlock() {
        lockJob.set(false);
    }

    protected void success() {
        assert uuid != null;
        assert canceller != null;

        logger.debug(String.format("[GC] a job[name:%s, id:%s] completes successfully", NAME, uuid));
        canceller.run();

        SQL.New(GarbageCollectorVO.class)
                .eq(GarbageCollectorVO_.uuid, uuid)
                .set(GarbageCollectorVO_.status, GCStatus.Done).update();
        gcMgr.deregisterGC(this);
    }

    protected void cancel() {
        assert uuid != null;
        assert canceller != null;

        logger.debug(String.format("[GC] a job[name:%s, id:%s] is cancelled by itself", NAME, uuid));
        canceller.run();

        SQL.New(GarbageCollectorVO.class)
                .eq(GarbageCollectorVO_.uuid, uuid)
                .set(GarbageCollectorVO_.status, GCStatus.Done).update();

        gcMgr.deregisterGC(this);
    }

    protected void fail(ErrorCode err) {
        assert uuid != null;

        unlock();

        logger.debug(String.format("[GC] a job[name:%s, id:%s] failed because %s", NAME, uuid,err));
        GarbageCollectorVO vo = dbf.findByUuid(uuid, GarbageCollectorVO.class);
        if (vo == null) {
            logger.warn(String.format("[GC] cannot find a job[name:%s, id:%s], assume it's deleted", NAME, uuid));
            cancel();
            return;
        }

        vo.setStatus(GCStatus.Idle);
        dbf.update(vo);
    }

    protected String buildContext() {
        Map context = new HashMap<>();

        for (Field f : FieldUtils.getAllFields(getClass())) {
            if (!f.isAnnotationPresent(GC.class)) {
                continue;
            }

            try {
                f.setAccessible(true);
                context.put(f.getName(), f.get(this));
            } catch (IllegalAccessException e) {
                throw new CloudRuntimeException(e);
            }
        }
        return JSONObjectUtil.toJsonString(context);
    }

    final protected void cleanThreadContext() {
        ThreadContext.clearAll();
        TaskContext.removeTaskContext();
    }

    final protected void saveToDb() {
        GarbageCollectorVO vo = new GarbageCollectorVO();
        vo.setUuid(Platform.getUuid());
        vo.setContext(buildContext());
        vo.setRunnerClass(getClass().getName());
        vo.setManagementNodeUuid(Platform.getManagementServerId());
        vo.setStatus(GCStatus.Idle);
        if (this instanceof EventBasedGarbageCollector) {
            vo.setType(GarbageCollectorType.EventBased.toString());
        } else if (this instanceof CycleBasedGarbageCollector) {
            vo.setType(GarbageCollectorType.CycleBased.toString());
        } else {
            vo.setType(GarbageCollectorType.TimeBased.toString());
        }
        vo.setName(NAME);
        vo = dbf.persistAndRefresh(vo);
        uuid = vo.getUuid();

        logger.debug(String.format("[GC] saved a job[name:%s, id:%s] to DB", NAME, uuid));
    }

    public void updateContext() {
        SQL.New(GarbageCollectorVO.class).eq(GarbageCollectorVO_.uuid, uuid)
                .set(GarbageCollectorVO_.context, buildContext())
                .update();
    }

    public static <T extends GarbageCollector> String updateContext(String uuid, String context, Class<T> clazz, Consumer<T> consumer) {
        T gc  = JSONObjectUtil.toObject(context, clazz);
        consumer.accept(gc);
        context = gc.buildContext();
        SQL.New(GarbageCollectorVO.class).eq(GarbageCollectorVO_.uuid, uuid)
                .set(GarbageCollectorVO_.context, context)
                .update();

        return context;
    }

    /**
     * 从 GarbageCollectorVO 恢复 GC 实例（孤儿加载 / 手动触发场景）。
     *
     * <p>使用乐观锁（条件更新）认领 GC，防止多个线程/MN 并发加载同一个孤儿 GC。
     * 只有 managementNodeUuid 仍为 NULL 的 VO 才会被认领成功。</p>
     *
     * @param vo GC 数据库记录
     * @return true=认领成功，false=已被其他线程/MN 认领
     */
    boolean loadFromVO(GarbageCollectorVO vo) {
        Object dataObj = JSONObjectUtil.toObject(vo.getContext(), getClass());

        for (Field f : FieldUtils.getAllFields(getClass())) {
            if (!f.isAnnotationPresent(GC.class)) {
                continue;
            }

            try {
                f.setAccessible(true);
                f.set(this, f.get(dataObj));
            } catch (Exception e) {
                throw new CloudRuntimeException(e);
            }
        }

        uuid = vo.getUuid();

        // 乐观锁：只认领 managementNodeUuid 为 NULL 的 GC（防止并发加载同一孤儿）
        int updated = SQL.New(GarbageCollectorVO.class)
                .eq(GarbageCollectorVO_.uuid, vo.getUuid())
                .isNull(GarbageCollectorVO_.managementNodeUuid)
                .set(GarbageCollectorVO_.status, GCStatus.Idle)
                .set(GarbageCollectorVO_.managementNodeUuid, Platform.getManagementServerId())
                .update();

        if (updated == 0) {
            logger.debug(String.format("[GC] job[name:%s, id:%s] already claimed by another node, skip",
                    vo.getName(), vo.getUuid()));
            return false;
        }

        gcMgr.registerGC(this);
        return true;
    }

    @SyncThread(level = 50)
    void runTrigger() {
        GarbageCollector self = this;
        EXECUTED_TIMES++;

        boolean isExisting = Q.New(GarbageCollectorVO.class).eq(GarbageCollectorVO_.uuid, getUuid()).isExists();
        if (!isExisting) {
            canceller.run();
            gcMgr.deregisterGC(self);
            return;
        }

        try {
            triggerNow(new GCCompletion(null) {
                @Override
                public void cancel() {
                    self.cancel();
                }

                @Override
                public void success() {
                    self.success();
                }

                @Override
                public void fail(ErrorCode errorCode) {
                    self.fail(errorCode);
                }
            });
        } catch (Throwable t) {
            logger.warn(String.format("[GC] unhandled exception happened when" +
                    " running a GC job[name:%s, id:%s]", NAME, uuid), t);
            fail(inerr(t.getMessage()));
        }
    }

    public boolean existedAndNotCompleted() {
        return Q.New(GarbageCollectorVO.class).eq(GarbageCollectorVO_.name, NAME).notEq(GarbageCollectorVO_.status, GCStatus.Done).isExists();
    }

    public boolean existedAndNotCompletedByLike(String nameExp) {
        return Q.New(GarbageCollectorVO.class).like(GarbageCollectorVO_.name, nameExp).notEq(GarbageCollectorVO_.status, GCStatus.Done).isExists();
    }
}
