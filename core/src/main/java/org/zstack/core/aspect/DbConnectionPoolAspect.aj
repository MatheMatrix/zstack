package org.zstack.core.aspect;

import javax.persistence.EntityManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.zstack.utils.logging.CLogger;
import org.zstack.utils.Utils;
import org.zstack.core.db.DatabaseFacade;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 */
public aspect DbConnectionPoolAspect {
    private final CLogger logger = Utils.getLogger(DbConnectionPoolAspect.class);

    pointcut connRetry1() : call(* org.zstack.core.db.DatabaseFacade+.findByUuid(..));
    pointcut connRetry2() : call(* org.zstack.core.db.DatabaseFacade+.findById(..));
    pointcut connRetry3() : call(* org.zstack.core.db.DatabaseFacade+.find(..));
    pointcut connRetry4() : call(* org.zstack.core.db.DatabaseFacade+.reload(..));
    pointcut connRetry5() : call(* org.zstack.core.db.DatabaseFacade+.listAll(..));
    pointcut connRetry6() : call(* org.zstack.core.db.DatabaseFacade+.listByPrimaryKeys(..));
    pointcut connRetry7() : call(* org.zstack.core.db.DatabaseFacade+.isExist(..));

    Object around() : connRetry1() || connRetry2() || connRetry3() || connRetry4() || connRetry5() || connRetry6() || connRetry7() {
        int retryCount = 3;
        do {
            try {
                logger.warn("enter test aj");
                return proceed();
            } catch (TransactionSystemException | CannotCreateTransactionException e) {
                logger.warn(String.format("meet a db transaction exception, we will retry again %s times. Details: %s", retryCount, e.getMessage()));
                e.printStackTrace();
                retryCount--;
                if (retryCount > 0) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(400, 600));
                    } catch (InterruptedException e1) {
                        logger.warn(e1.getMessage());
                    }
                }
            }
        } while (retryCount > 0);
        return proceed();
    }

    Object around() : call(* org.zstack.core.db.DatabaseFacade+.testFindByUuid2(..))  {
        int retryCount = 3;
        do {
            try {
                logger.warn("enter test aj");
                return proceed();
            } catch (TransactionSystemException | CannotCreateTransactionException e) {
                logger.warn(String.format("meet a db transaction exception, we will retry again %s times. Details: %s", retryCount, e.getMessage()));
                e.printStackTrace();
                retryCount--;
                if (retryCount > 0) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(400, 600));
                    } catch (InterruptedException e1) {
                        logger.warn(e1.getMessage());
                    }
                }
            }
        } while (retryCount > 0);
        return proceed();
    }

    Object around() : call(* org.zstack.core.db.DatabaseFacade+.testFindByUuid3(..))  {
        int retryCount = 3;
        do {
            try {
                logger.warn("enter test aj");
                return proceed();
            } catch (TransactionSystemException | CannotCreateTransactionException e) {
                logger.warn(String.format("meet a db transaction exception, we will retry again %s times. Details: %s", retryCount, e.getMessage()));
                e.printStackTrace();
                retryCount--;
                if (retryCount > 0) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(400, 600));
                    } catch (InterruptedException e1) {
                        logger.warn(e1.getMessage());
                    }
                }
            }
        } while (retryCount > 0);
        return proceed();
    }

    Object around() : execution(* org.zstack.core.db.DatabaseFacade+.testFindByUuid4(..))  {
        try {
            logger.warn("4enter test aj");
            return proceed();
        } catch (TransactionSystemException | CannotCreateTransactionException e) {
            logger.warn("4meet a db exception, we will retry it once: " + e.getMessage());
            e.printStackTrace();
            try {
                TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(400, 600));
            } catch (InterruptedException e1) {
                logger.warn(e1.getMessage());
            }
            return proceed();
        } catch (Throwable t) {
            logger.warn("4yyyyyy " + t.getClass().getName());
            logger.warn("4zzzzz " + t.getMessage());
            try {
                TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(400, 600));
            } catch (InterruptedException e1) {
                logger.warn(e1.getMessage());
            }
            return proceed();
        }
    }

    Object around() : execution(* org.zstack.core.db.DatabaseFacade+.testFindByUuid5(..))  {
        try {
            logger.warn("5enter test aj");
            return proceed();
        } catch (TransactionSystemException | CannotCreateTransactionException e) {
            logger.warn("5meet a db exception, we will retry it once: " + e.getMessage());
            e.printStackTrace();
            try {
                TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(400, 600));
            } catch (InterruptedException e1) {
                logger.warn(e1.getMessage());
            }
            return proceed();
        } catch (Throwable t) {
            logger.warn("5yyyyyy " + t.getClass().getName());
            logger.warn("5zzzzz " + t.getMessage());
            try {
                TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(400, 600));
            } catch (InterruptedException e1) {
                logger.warn(e1.getMessage());
            }
            return proceed();
        }
    }
}
