package org.zstack.core.resnotify;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.Platform;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.EntityEvent;
import org.zstack.core.db.EntityLifeCycleCallback;
import org.zstack.core.db.Q;
import org.zstack.header.AbstractService;
import org.zstack.header.Component;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.Message;
import org.zstack.header.zwatch.resnotify.*;
import org.zstack.utils.BeanUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import javax.persistence.Entity;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ResNotifySubscriptionManagerImpl extends AbstractService implements Component {
    private static final CLogger logger = Utils.getLogger(ResNotifySubscriptionManagerImpl.class);

    @Autowired
    private CloudBus bus;
    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private ResNotifyWebhookDeliveryService deliveryService;

    private final Map<String, List<CallbackRegistration>> callbackRegistrations = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> entityClassByName = new HashMap<>();

    private static class CallbackRegistration {
        Class<?> entityClass;
        EntityEvent event;
        EntityLifeCycleCallback callback;
    }

    @Override
    public void handleMessage(Message msg) {
        if (msg instanceof APIMessage) {
            handleApiMessage((APIMessage) msg);
        } else {
            bus.dealWithUnknownMessage(msg);
        }
    }

    private void handleApiMessage(APIMessage msg) {
        if (msg instanceof APISubscribeResNotifyMsg) {
            handle((APISubscribeResNotifyMsg) msg);
        } else if (msg instanceof APIDeleteResNotifySubscriptionMsg) {
            handle((APIDeleteResNotifySubscriptionMsg) msg);
        } else if (msg instanceof APIUpdateResNotifySubscriptionMsg) {
            handle((APIUpdateResNotifySubscriptionMsg) msg);
        } else {
            bus.dealWithUnknownMessage(msg);
        }
    }

    private void handle(APISubscribeResNotifyMsg msg) {
        ResNotifySubscriptionVO vo = new ResNotifySubscriptionVO();
        vo.setUuid(msg.getResourceUuid() != null ? msg.getResourceUuid() : Platform.getUuid());
        vo.setName(msg.getName());
        vo.setDescription(msg.getDescription());
        vo.setResourceTypes(msg.getResourceTypes() != null ?
                String.join(",", msg.getResourceTypes()) : null);
        vo.setEventTypes(msg.getEventTypes() != null ?
                String.join(",", msg.getEventTypes()) : null);
        vo.setType(ResNotifyType.valueOf(msg.getType() != null ? msg.getType() : "WEBHOOK"));
        vo.setState(ResNotifySubscriptionState.Enabled);
        vo.setAccountUuid(msg.getSession().getAccountUuid());

        dbf.persist(vo);

        ResNotifyWebhookRefVO webhookRef = new ResNotifyWebhookRefVO();
        webhookRef.setUuid(vo.getUuid());
        webhookRef.setWebhookUrl(msg.getWebhookUrl());
        webhookRef.setSecret(msg.getSecret());
        webhookRef.setCustomHeaders(msg.getCustomHeaders());

        dbf.persist(webhookRef);

        vo = dbf.reload(vo);

        installCallbacksForSubscription(vo);

        APISubscribeResNotifyEvent evt = new APISubscribeResNotifyEvent(msg.getId());
        evt.setInventory(ResNotifySubscriptionInventory.valueOf(vo));
        bus.publish(evt);
    }

    private void handle(APIDeleteResNotifySubscriptionMsg msg) {
        uninstallCallbacksForSubscription(msg.getUuid());

        dbf.removeByPrimaryKey(msg.getUuid(), ResNotifySubscriptionVO.class);

        APIDeleteResNotifySubscriptionEvent evt = new APIDeleteResNotifySubscriptionEvent(msg.getId());
        bus.publish(evt);
    }

    private void handle(APIUpdateResNotifySubscriptionMsg msg) {
        ResNotifySubscriptionVO vo = dbf.findByUuid(msg.getUuid(), ResNotifySubscriptionVO.class);

        boolean needReinstallCallbacks = false;

        if (msg.getName() != null) {
            vo.setName(msg.getName());
        }
        if (msg.getDescription() != null) {
            vo.setDescription(msg.getDescription());
        }
        if (msg.getResourceTypes() != null) {
            vo.setResourceTypes(String.join(",", msg.getResourceTypes()));
            needReinstallCallbacks = true;
        }
        if (msg.getEventTypes() != null) {
            vo.setEventTypes(String.join(",", msg.getEventTypes()));
            needReinstallCallbacks = true;
        }
        if (msg.getState() != null) {
            ResNotifySubscriptionState newState = ResNotifySubscriptionState.valueOf(msg.getState());
            if (vo.getState() != newState) {
                needReinstallCallbacks = true;
            }
            vo.setState(newState);
        }

        dbf.update(vo);

        ResNotifyWebhookRefVO webhookRef = dbf.findByUuid(msg.getUuid(), ResNotifyWebhookRefVO.class);
        if (webhookRef != null) {
            boolean webhookUpdated = false;
            if (msg.getWebhookUrl() != null) {
                webhookRef.setWebhookUrl(msg.getWebhookUrl());
                webhookUpdated = true;
            }
            if (msg.getSecret() != null) {
                webhookRef.setSecret(msg.getSecret());
                webhookUpdated = true;
            }
            if (msg.getCustomHeaders() != null) {
                webhookRef.setCustomHeaders(msg.getCustomHeaders());
                webhookUpdated = true;
            }
            if (webhookUpdated) {
                dbf.update(webhookRef);
            }
        }

        if (needReinstallCallbacks) {
            uninstallCallbacksForSubscription(vo.getUuid());
            if (vo.getState() == ResNotifySubscriptionState.Enabled) {
                vo = dbf.reload(vo);
                installCallbacksForSubscription(vo);
            }
        }

        vo = dbf.reload(vo);

        APIUpdateResNotifySubscriptionEvent evt = new APIUpdateResNotifySubscriptionEvent(msg.getId());
        evt.setInventory(ResNotifySubscriptionInventory.valueOf(vo));
        bus.publish(evt);
    }

    private void installCallbacksForSubscription(ResNotifySubscriptionVO sub) {
        if (sub.getState() != ResNotifySubscriptionState.Enabled) {
            return;
        }

        List<String> resourceTypes = parseCommaSeparated(sub.getResourceTypes());
        List<EntityEvent> events = parseEntityEvents(sub.getEventTypes());

        if (events.isEmpty()) {
            events = Arrays.asList(EntityEvent.POST_PERSIST, EntityEvent.POST_UPDATE, EntityEvent.POST_REMOVE);
        }

        List<CallbackRegistration> registrations = new ArrayList<>();

        for (EntityEvent event : events) {
            EntityLifeCycleCallback callback = (evt, entity) -> {
                String entityType = entity.getClass().getSimpleName();
                if (!resourceTypes.isEmpty() && !resourceTypes.contains(entityType)) {
                    return;
                }
                deliveryService.deliverAsync(sub.getUuid(), evt, entity);
            };

            if (resourceTypes.isEmpty()) {
                dbf.installEntityLifeCycleCallback(null, event, callback);
            } else {
                for (String typeName : resourceTypes) {
                    Class<?> entityClass = resolveEntityClass(typeName);
                    if (entityClass != null) {
                        dbf.installEntityLifeCycleCallback(entityClass, event, callback);

                        CallbackRegistration reg = new CallbackRegistration();
                        reg.entityClass = entityClass;
                        reg.event = event;
                        reg.callback = callback;
                        registrations.add(reg);
                    }
                }
            }

            if (resourceTypes.isEmpty()) {
                CallbackRegistration reg = new CallbackRegistration();
                reg.entityClass = null;
                reg.event = event;
                reg.callback = callback;
                registrations.add(reg);
            }
        }

        callbackRegistrations.put(sub.getUuid(), registrations);
    }

    private void uninstallCallbacksForSubscription(String subscriptionUuid) {
        List<CallbackRegistration> registrations = callbackRegistrations.remove(subscriptionUuid);
        if (registrations != null) {
            for (CallbackRegistration reg : registrations) {
                dbf.uninstallEntityLifeCycleCallback(reg.entityClass, reg.event, reg.callback);
            }
        }
    }

    private List<String> parseCommaSeparated(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String s : value.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private List<EntityEvent> parseEntityEvents(String eventTypes) {
        if (eventTypes == null || eventTypes.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<EntityEvent> events = new ArrayList<>();
        for (String s : eventTypes.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                try {
                    events.add(EntityEvent.valueOf(trimmed));
                } catch (IllegalArgumentException e) {
                    logger.warn(String.format("unknown entity event type: %s, skipping", trimmed));
                }
            }
        }
        return events;
    }

    private Class<?> resolveEntityClass(String simpleTypeName) {
        Class<?> clz = entityClassByName.get(simpleTypeName);
        if (clz == null) {
            logger.warn(String.format("cannot resolve entity class for type: %s", simpleTypeName));
        }
        return clz;
    }

    private void buildEntityClassLookup() {
        for (Class<?> clz : BeanUtils.reflections.getTypesAnnotatedWith(Entity.class)) {
            entityClassByName.put(clz.getSimpleName(), clz);
        }
    }

    @Override
    public String getId() {
        return bus.makeLocalServiceId(ResNotifyConstants.SERVICE_ID);
    }

    @Override
    public boolean start() {
        buildEntityClassLookup();

        List<ResNotifySubscriptionVO> subs = Q.New(ResNotifySubscriptionVO.class)
                .eq(ResNotifySubscriptionVO_.state, ResNotifySubscriptionState.Enabled)
                .list();

        for (ResNotifySubscriptionVO sub : subs) {
            installCallbacksForSubscription(sub);
        }

        logger.info(String.format("ResNotifySubscriptionManager started, loaded %d active subscriptions", subs.size()));
        return true;
    }

    @Override
    public boolean stop() {
        for (String subUuid : new ArrayList<>(callbackRegistrations.keySet())) {
            uninstallCallbacksForSubscription(subUuid);
        }
        logger.info("ResNotifySubscriptionManager stopped, all callbacks uninstalled");
        return true;
    }
}
