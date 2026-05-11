package com.tinyroute;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TinyRouteApplicationTests {

	@MockitoBean
	private RedisClient redisClient;

	@MockitoBean
	private StatefulRedisConnection<String, byte[]> redisConnection;

	@MockitoBean
	private ProxyManager<String> proxyManager;

	@Test
	void contextLoads() {
	}

}
