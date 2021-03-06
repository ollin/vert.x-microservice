package org.jacpfx.vertx.registry;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;
import org.jacpfx.common.GlobalKeyHolder;
import org.jacpfx.common.Serializer;
import org.jacpfx.common.ServiceInfo;
import org.jacpfx.common.ServiceInfoHolder;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.function.Consumer;

/**
 * The Service registry knows all service verticles, a verticle registers here and will be traced. The registry also notify the router to add/remove routes to the services.
 * Created by amo on 22.10.14.
 */
public class ServiceRegistry extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(ServiceRegistry.class);

    private static final long DEFAULT_EXPIRATION_AGE = 5000;
    private static final long DEFAULT_TIMEOUT = 50000;
    private static final long DEFAULT_PING_TIME = 10000;
    private static final long DEFAULT_SWEEP_TIME = 0;


    private long expiration_age = DEFAULT_EXPIRATION_AGE;
    private long ping_time = DEFAULT_PING_TIME;
    private long sweep_time = DEFAULT_SWEEP_TIME;
    private long timeout_time = DEFAULT_TIMEOUT;
    private String serviceRegisterPath;
    private String serviceUnRegisterPath;


    @Override
    public void start(Future<Void> startFuture) {
        log.info("Service registry started.");

        initConfiguration(getConfig());
        vertx.eventBus().consumer(GlobalKeyHolder.SERVICE_REGISTRY_REGISTER, this::serviceRegister);
        vertx.eventBus().consumer(GlobalKeyHolder.SERVICE_REGISTRY_GET, this::getServicesInfo);
        pingService();
        startFuture.complete();
    }

    private void initConfiguration(JsonObject config) {
        expiration_age = config.getLong("expiration", DEFAULT_EXPIRATION_AGE);
        ping_time = config.getLong("ping", DEFAULT_PING_TIME);
        sweep_time = config.getLong("sweep", DEFAULT_SWEEP_TIME);
        timeout_time = config.getLong("timeout", DEFAULT_TIMEOUT);
        serviceRegisterPath = config.getString("serviceRegisterPath", GlobalKeyHolder.SERVICE_REGISTER_HANDLER);
        serviceUnRegisterPath = config.getString("serviceUnRegisterPath", GlobalKeyHolder.SERVICE_UNREGISTER_HANDLER);

    }

    private void getServicesInfo(Message<JsonObject> message) {
        log.info("service info: " + message.body());
        this.vertx.sharedData().<String, ServiceInfoHolder>getClusterWideMap(GlobalKeyHolder.REGISTRY_MAP_KEY, onSuccess(resultMap ->
                        getServiceHolderAndReplyToServiceInfoRequest(message, resultMap)
        ));
    }

    private void getServiceHolderAndReplyToServiceInfoRequest(Message<JsonObject> message, AsyncMap<String, ServiceInfoHolder> resultMap) {
        resultMap.get(GlobalKeyHolder.SERVICE_HOLDER, onSuccess(resultHolder -> {
            if (resultHolder != null) {
                message.reply(resultHolder.getServiceInfo());
            } else {
                message.reply(new JsonObject().put("services", new JsonArray()));
            }
        }));
    }

    protected <T> Handler<AsyncResult<T>> onSuccess(Consumer<T> consumer) {
        return result -> {
            if (result.failed()) {
                log.info("failed: " + result.cause());
                result.cause().printStackTrace();

            } else {
                log.info("ok : " + result.result());
                consumer.accept(result.result());
            }
        };
    }


    private void serviceRegister(final Message<byte[]> message) {
        final ServiceInfo info = deserializeServiceInfo(message);
        info.setLastConnection(getDateStamp());
        log.info("Register: " + message.body());
        this.vertx.sharedData().<String, ServiceInfoHolder>getClusterWideMap(GlobalKeyHolder.REGISTRY_MAP_KEY, onSuccess(resultMap -> {
                    getServiceHolderAndRegister(message, info, resultMap);
                }
        ));

    }

    private void getServiceHolderAndRegister(Message<byte[]> message, ServiceInfo info, AsyncMap<String, ServiceInfoHolder> resultMap) {
        log.info("got map");
        resultMap.get(GlobalKeyHolder.SERVICE_HOLDER, onSuccess(resultHolder -> {
            log.info("got result holder");
            if (resultHolder != null) {
                addServiceEntry(resultMap, info, resultHolder, message);
            } else {
                createNewEntry(resultMap, info, new ServiceInfoHolder(), message);
            }
        }));
    }

    private ServiceInfo deserializeServiceInfo(final Message<byte[]> message) {
        try {
            return (ServiceInfo) Serializer.deserialize(message.body());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void addServiceEntry(final AsyncMap resultMap, final ServiceInfo info, final ServiceInfoHolder holder, final Message<byte[]> message) {
        holder.add(info);
        log.info("update result holder");
        resultMap.replace(GlobalKeyHolder.SERVICE_HOLDER, holder, onSuccess(s -> {
            publishToEntryPoint(info);
            message.reply(true);
            log.info("Register REPLACE: " + info);
        }));
    }

    private void createNewEntry(final AsyncMap resultMap, final ServiceInfo info, final ServiceInfoHolder holder, final Message<byte[]> message) {
        holder.add(info);
        log.info("add result holder");
        resultMap.put(GlobalKeyHolder.SERVICE_HOLDER, holder, onSuccess(s -> {
            publishToEntryPoint(info);
            message.reply(true);
            log.info("Register ADD: " + info);
        }));
    }

    private void publishToEntryPoint(ServiceInfo info) {
        vertx.eventBus().publish(serviceRegisterPath, info);
    }


    private void pingService() {
        vertx.sharedData().getCounter("registry-timer-counter", onSuccess(counter -> counter.incrementAndGet(onSuccess(val -> {
            log.info("registry-timer-counter: " + val);
            if (val <= 1) {
                vertx.setPeriodic(ping_time, timerID -> {
                    log.info("ping_time: " + timerID);
                    getSharedRegistryAndPing();
                });
            }
        }))));

    }

    private void getSharedRegistryAndPing() {
        final SharedData sharedData = this.vertx.sharedData();
        sharedData.<String, ServiceInfoHolder>getClusterWideMap(GlobalKeyHolder.REGISTRY_MAP_KEY, onSuccess(resultMap -> {
                    log.info("resultMap " + resultMap);
                    getServiceHolderAndPingServices(resultMap);
                }
        ));
    }

    private void getServiceHolderAndPingServices(AsyncMap<String, ServiceInfoHolder> resultMap) {
        resultMap.get(GlobalKeyHolder.SERVICE_HOLDER, onSuccess(holder -> {
            log.info("get Holder " + holder + " this:" + this);
            if (holder != null) {
                final List<ServiceInfo> serviceHolders = holder.getAll();
                serviceHolders.forEach(this::pingService);

            }

        }));
    }

    private void pingService(final ServiceInfo info) {
        final String serviceName = info.getServiceName();
        vertx.
                eventBus().send(
                serviceName + "-info",
                "ping",
                new DeliveryOptions().setSendTimeout(timeout_time),
                (Handler<AsyncResult<Message<JsonObject>>>) event -> {
                    handlePingResult(info, serviceName, event);
                });
    }

    private void handlePingResult(ServiceInfo info, String serviceName, AsyncResult<Message<JsonObject>> event) {
        if (event.succeeded()) {
            info.setLastConnection(getDateStamp());
            updateServiceInfo(info);
            log.info("ping: " + serviceName);
        } else {
            log.info("ping error: " + serviceName);
            unregisterServiceAtRouter(info);
        }
    }

    private void updateServiceInfo(final ServiceInfo info) {
        this.vertx.sharedData().<String, ServiceInfoHolder>getClusterWideMap(GlobalKeyHolder.REGISTRY_MAP_KEY, onSuccess(resultMap ->
                        resultMap.get(GlobalKeyHolder.SERVICE_HOLDER, onSuccess(holder -> {
                            log.info("register service info " + info);
                            holder.replace(info);
                            resultMap.replace(GlobalKeyHolder.SERVICE_HOLDER, holder, onSuccess(s -> log.info("update services: ")));
                        }))
        ));


    }

    private void unregisterServiceAtRouter(final ServiceInfo info) {
        this.vertx.sharedData().<String, ServiceInfoHolder>getClusterWideMap(GlobalKeyHolder.REGISTRY_MAP_KEY, onSuccess(resultMap ->
                        resultMap.get(GlobalKeyHolder.SERVICE_HOLDER, onSuccess(holder ->
                                        removeAndUpdateServiceInfo(info, resultMap, holder)
                        ))
        ));


    }

    private void removeAndUpdateServiceInfo(ServiceInfo info, AsyncMap<String, ServiceInfoHolder> resultMap, ServiceInfoHolder holder) {
        holder.remove(info);
        resultMap.replace(GlobalKeyHolder.SERVICE_HOLDER, holder, t -> {
            if (t.succeeded()) {
                resetServiceCounterAndPublish(info);

            }
        });
    }

    private void resetServiceCounterAndPublish(ServiceInfo info) {
        vertx.sharedData().getCounter(info.getServiceName(), onSuccess(counter -> counter.get(onSuccess(c -> {
            counter.compareAndSet(c, 0, onSuccess(val ->
                            vertx.eventBus().publish(serviceUnRegisterPath, info)
            ));
        }))));
    }

    private JsonObject getConfig() {
        return context != null ? context.config() : new JsonObject();
    }


    private String getDateStamp() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        timeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return timeFormat.format(Calendar.getInstance().getTime());
    }
}
