package runafter.mqtt.stress.test.client;

import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.responses.EtcdAuthenticationException;
import mousio.etcd4j.responses.EtcdException;
import mousio.etcd4j.responses.EtcdVersionResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * Created by runaf on 2017-07-09.
 */
public class SimpleEctdTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleEctdTest.class);
    private static final String KEY = "key1";
    private static final String VALUE = "value1";
    private static final long TIMEOUT = 3000L;
    private EtcdClient ec;

    @Before
    public void setUp() {
        ec = new EtcdClient(URI.create("http://127.0.0.1:2379"));
    }
    @After
    public void tearDown() throws EtcdAuthenticationException, TimeoutException, EtcdException {
        try {
            if (ec != null) {
                ec.delete(KEY).send().get();
                ec.close();
            }
        } catch (IOException e) {
            LOGGER.error("ec.close", e);
        }
    }
    @Test
    public void shouldConnect() throws IOException {
        EtcdVersionResponse version = ec.version();
        LOGGER.info("version.server: {}", version.server);
        LOGGER.info("version.cluster: {}", version.cluster);
        assertThat(version, notNullValue());
    }

    @Test
    public void shouldPutGetSync() throws Exception {
        ec.put(KEY, VALUE).send().get();
        assertThat(ec.get(KEY).send().get().node.value, is(VALUE));
    }

    @Test
    public void shouldWatchBeforePut() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        ec.get(KEY).waitForChange().send().addListener(response -> latch.countDown());
        ec.put(KEY, VALUE).send().get();
        await(latch);

        assertThat(latch.getCount(), is(0L));
    }

    @Test
    public void shouldWatchAfterPut() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        ec.put(KEY, VALUE).send().get();
        ec.get(KEY).waitForChange().send().addListener(response -> latch.countDown());

        await(latch);
        assertThat(latch.getCount(), is(1L));

        ec.put(KEY, VALUE).send().get();
        await(latch);
        assertThat(latch.getCount(), is(0L));
    }

    private void await(CountDownLatch latch) throws InterruptedException {
        latch.await(TIMEOUT, TimeUnit.MILLISECONDS);
    }
}
