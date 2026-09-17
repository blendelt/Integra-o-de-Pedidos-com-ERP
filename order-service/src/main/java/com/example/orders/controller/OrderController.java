package com.example.orders.controller;

import org.springframework.web.bind.annotation.PathVariable;

import com.example.orders.dto.RetryOrderRequest;

import com.example.orders.dto.CreateOrderRequest;
import com.example.orders.dto.OrderResponse;
import com.example.orders.dto.ProcessingResult;
import com.example.orders.processor.OrderProcessor;
import com.example.orders.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderProcessor orderProcessor;

    public OrderController(OrderService orderService, OrderProcessor orderProcessor) {
        this.orderService = orderService;
        this.orderProcessor = orderProcessor;
    }

    @PostMapping("/process")
    public ProcessingResult process() {
        return orderProcessor.processPending();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(request);
    }

    @GetMapping
    public List<OrderResponse> findAll() {
        return orderService.findAll();
    }

    @PostMapping("/{id}/retry")
    public OrderResponse retry(@PathVariable Long id,
            @Valid @RequestBody RetryOrderRequest request) {
        return orderService.retry(id, request);
    }
}
