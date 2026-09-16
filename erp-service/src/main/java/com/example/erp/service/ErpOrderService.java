package com.example.erp.service;

import com.example.erp.config.ErpSimulationProperties;
import com.example.erp.dto.ErpOrderRequest;
import com.example.erp.dto.ErpOrderResponse;
import com.example.erp.exception.ErpProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ErpOrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErpOrderService.class);

    private final ErpSimulationProperties properties;

    public ErpOrderService(ErpSimulationProperties properties) {
        this.properties = properties;
    }

    public ErpOrderResponse process(ErpOrderRequest request) {
        simulateDelay();

        if (mustFail(request.externalId())) {
            LOGGER.warn("ERP simulation failed for externalId={}", request.externalId());
            throw new ErpProcessingException("ERP could not process order " + request.externalId());
        }

        LOGGER.info("ERP simulation processed externalId={}", request.externalId());
        return new ErpOrderResponse(request.externalId(), "ACCEPTED", OffsetDateTime.now());
    }

    private void simulateDelay() {
        long delay = properties.minimumDelayMs() == properties.maximumDelayMs()
                ? properties.minimumDelayMs()
                : ThreadLocalRandom.current().nextLong(
                        properties.minimumDelayMs(), properties.maximumDelayMs() + 1);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ErpProcessingException("ERP processing was interrupted");
        }
    }

    private boolean mustFail(String externalId) {
        boolean deterministicFailure = properties.failurePrefix() != null
                && !properties.failurePrefix().isBlank()
                && externalId.startsWith(properties.failurePrefix());
        boolean randomFailure = properties.randomFailureRate() > 0
                && ThreadLocalRandom.current().nextDouble() < properties.randomFailureRate();
        return deterministicFailure || randomFailure;
    }
}
