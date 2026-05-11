package com.tinyroute.service.ratelimit;

import com.tinyroute.infra.ratelimit.RateLimitPlan;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final ProxyManager<String> proxyManager;

    public Bucket resolveBucket(String key, RateLimitPlan plan) {
        return proxyManager.builder()
                .build(key, () -> BucketConfiguration.builder()
                        .addLimit(Bandwidth.classic(
                                plan.getCapacity(),
                                Refill.greedy(plan.getRefillTokens(), plan.getDuration())
                        ))
                        .build());
    }
}