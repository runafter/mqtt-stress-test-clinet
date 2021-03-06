package runafter.mqtt.stress.test.client;

import org.fusesource.mqtt.client.*;
import org.fusesource.mqtt.client.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Created by runaf on 2017-07-01.
 */
public class SimpleMqttClientTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleMqttClientTest.class);
    private static final long TIMEOUT = 10000L;
    private static final String CLIENT_ID = "CLIENT";
    private static final Topic[] TOPICS = new Topic[] {new Topic("public", QoS.EXACTLY_ONCE)};
    private Collection<MQTTConnection> connections;
    private FutureConnection connection;

    @Before
    public void setUp() throws URISyntaxException {
        connection = null;
        connections = null;
    }

    @After
    public void tearDown() throws Exception {
        disconnectAll(connections);
        disconnect(connection);
    }

    @Test
    public void shouldConnectToServerUsingFutureConnection() throws Exception {
        MQTT mqtt = new MQTT();
        mqtt.setClientId(CLIENT_ID);
        mqtt.setHost("tcp://localhost:1883");
        mqtt.setKeepAlive((short) 60);
        connection = mqtt.futureConnection();
        Future<Void> future = connection.connect();
        awaitUntilConnected(connection, future, TIMEOUT);
        assertThat(connection.isConnected(), is(true));
    }
    @Test
    public void shouldConnectToServerUsingFutureConnectionMassiveClients() throws Exception {
        int threadPoolCount = 16;
        MQTT.setBlockingThreadPool(fixedThreadPoolOf(threadPoolCount));
        sleep(30000L);
        connections = connect(50);
        awaitConnectedAll(connections);
        assertConnected(connections);
    }

    private void sleep(long time) throws InterruptedException {
        LOGGER.info("sleep {} ms", time);
        Thread.sleep(time);
    }

    private ThreadPoolExecutor fixedThreadPoolOf(int threadPoolCount) {
        final AtomicInteger threadNumber = new AtomicInteger(0);
        final ThreadGroup group = Thread.currentThread().getThreadGroup();
        return new ThreadPoolExecutor(threadPoolCount, threadPoolCount, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(group, r, "mqtt-client-thread-" + threadNumber.getAndIncrement());
                if (t.isDaemon())
                    t.setDaemon(true);
                if (t.getPriority() != Thread.NORM_PRIORITY)
                    t.setPriority(Thread.NORM_PRIORITY);
                return t;
            }
        });
    }

    private void disconnectAll(Collection<MQTTConnection> connections) throws Exception {
        if (connections == null)
            return;
        for (MQTTConnection connection : connections)
            connection.connection.disconnect().await();
    }
    private void disconnect(FutureConnection connection) throws Exception {
        if (connection == null)
            return;
        connection.disconnect().await();
    }

    private void assertConnected(Collection<MQTTConnection> connections) {
        for (MQTTConnection connection : connections)
            assertThat(connection.mqtt.getClientId() + " is not connected", connection.connection.isConnected(), is(true));
    }

    private void awaitConnectedAll(Collection<MQTTConnection> connections) throws Exception {
        long timeout = TIMEOUT * connections.size();
         for (MQTTConnection connection : connections) {
             awaitUntilConnected(connection.connection, connection.future, timeout);
         }
    }

    private void awaitUntilConnected(FutureConnection connection, Future<Void> future, long timeout) throws Exception {
        final int retryLimit = 3;
        int retry = 0;
        while (!connection.isConnected() && retry++ < retryLimit) {
            await(future, timeout);
            if (!connection.isConnected())
                sleep(100L);
        }
    }

    private Collection<MQTTConnection> connect(int count) throws URISyntaxException {
        List<MQTTConnection> connections = new ArrayList<>();

        Future<Void> future;
        AtomicInteger connectedCount = new AtomicInteger(0);
        for (int i = 0 ; i < count ; i++) {
            MQTT mqtt = new MQTT();
            String clientId = clientIdOf(i);
            mqtt.setClientId(clientId);
            mqtt.setHost("tcp://localhost:1883");
            mqtt.setKeepAlive((short) 60);

            long start = System.currentTimeMillis();
            LOGGER.info("{} is connecting....", clientId);
            final FutureConnection connection = mqtt.futureConnection();
            future = connection.connect();
            future.then(new Callback<Void>() {
                @Override
                public void onSuccess(Void value) {
                    int c = connectedCount.incrementAndGet();
                    LOGGER.info("{} is connected. It takes {} ms. ( {} / {} )", clientId, System.currentTimeMillis() - start, c, count);
                    connection.subscribe(TOPICS);
                }

                @Override
                public void onFailure(Throwable value) {
                    LOGGER.info("{} is failed to connect.", clientId);
                }
            });
            connections.add(new MQTTConnection(mqtt, connection, future));
        }

        return connections;
    }

    private String clientIdOf(int seq) {
        return String.format("%s%05d", CLIENT_ID, seq);
    }

    private void await(Future<?> future, long timeout) throws Exception {
        future.await(timeout, TimeUnit.MILLISECONDS);
    }

    private void await(Future<?> future) throws Exception {
        await(future, TIMEOUT);
    }
    private static class MQTTConnection {
        private final MQTT mqtt;
        private final FutureConnection connection;
        private final Future<Void> future;
        public MQTTConnection(MQTT mqtt, FutureConnection connection, Future<Void> future) {
            this.mqtt = mqtt;
            this.connection = connection;
            this.future = future;
        }
    }
}
