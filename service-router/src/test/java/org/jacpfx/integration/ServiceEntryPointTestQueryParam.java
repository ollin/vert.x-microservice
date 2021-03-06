package org.jacpfx.integration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.vertx.test.core.VertxTestBase;

/**
 * Created by amo on 13.11.14.
 */
public class ServiceEntryPointTestQueryParam extends VertxTestBase {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
/*
    @Override
    public void start() {
        // Make sure we call initialize() - this sets up the assert stuff so assert functionality works correctly
        initialize();
        // Deploy the module - the System property `vertx.modulename` will contain the name of the module so you
        // don't have to hardecode it in your tests
        container.deployVerticle("org.jacpfx.vertx.entrypoint.ServiceEntryPoint", asyncResult -> {
            // Deployment is asynchronous and this this handler will be called when it's complete (or failed)
            assertTrue(asyncResult.succeeded());
            assertNotNull("deploymentID should not be null", asyncResult.result());
            // If deployed correctly then start the tests!
            startTests();

        });
    }

    private HttpClient getClient() {

        Vertx vertx = VertxFactory.newVertx();
        HttpClient client = vertx.
                createHttpClient().
                setHost("localhost").
                setPort(8080);

        return client;
    }


    @Test
    public void testSimpleRESTGetQueryParamRoute() throws InterruptedException {
        final ConcurrentSharedMap<Object, Object> map = vertx.sharedData().getMap(ServiceRegistry.SERVICE_REGISTRY);
        int size = map.size();
        vertx.eventBus().send(ServiceRegistry.SERVICE_REGISTRY_REGISTER, getServiceInfoDesc("/testservice1"), (Handler<Message<Boolean>>) reply -> {

            assertEquals(true, reply.body());
            assertTrue(vertx.sharedData().getMap(ServiceRegistry.SERVICE_REGISTRY).size() == size + 1);
            vertx.eventBus().registerHandler("/testservice1/operation2", m -> {
                Logger logger = container.logger();
                final Parameter<String> params = gson.fromJson(m.body().toString(), Parameter.class);
                m.reply(params.getValue("name"));
                logger.info("reply to: " + m.body());
            });
            HttpClientRequest request = getClient().get("/testservice1/operation2?name=hallo1", new Handler<HttpClientResponse>() {
                public void handle(HttpClientResponse resp) {
                    resp.bodyHandler(body -> {
                        System.out.println("Got a response: " + body.toString());
                        Assert.assertEquals(body.toString(), "hallo1");
                    });

                    testComplete();
                }
            });
            request.end();

        });

    }




    private JsonObject getServiceInfoDesc(String serviceName) {
        JsonObject info = new JsonObject();
        final JsonArray operationsArray = new JsonArray();
        getDummyOperations(serviceName).forEach(op -> operationsArray.addObject(op));
        info.putString("serviceName", serviceName);
        info.putArray("operations", operationsArray);

        return info;
    }

    private List<JsonObject> getDummyOperations(String serviceName) {
        List<JsonObject> result = new ArrayList<>();

        result.add(org.jacpfx.common.JSONTool.createOperationObject(serviceName + "/operation1", Type.REST_GET.name(), new String[]{"text"}));
        result.add(org.jacpfx.common.JSONTool.createOperationObject(serviceName + "/operation2", Type.REST_GET.name(), new String[]{"text"},"name"));
        result.add(org.jacpfx.common.JSONTool.createOperationObject(serviceName + "/operation3:value", Type.REST_GET.name(), new String[]{"text"},"value"));
        result.add(org.jacpfx.common.JSONTool.createOperationObject(serviceName + "/operation4", Type.REST_GET.name(), new String[]{"text"}));
        return result;
    }*/
}
