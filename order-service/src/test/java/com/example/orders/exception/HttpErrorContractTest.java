package com.example.orders.exception;

import com.example.orders.controller.OrderController;
import com.example.orders.service.OrderService;
import com.example.orders.processor.OrderProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HttpErrorContractTest {
    @Test void malformedJsonReturnsSafe400() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new OrderController(mock(OrderService.class), mock(OrderProcessor.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{secret-invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("message").value("JSON ou parâmetro inválido"));
    }

    @Test void missingOrderReturns404WithoutExceptionDetails() throws Exception {
        var service = mock(OrderService.class);
        when(service.retry(eq(99L), any())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "private detail"));
        var mvc = MockMvcBuilders.standaloneSetup(new OrderController(service, mock(OrderProcessor.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/orders/99/retry").contentType(MediaType.APPLICATION_JSON).content("""
                {"order":{"externalId":"A","customerName":"Test","totalValue":10},"version":0,"confirmedNotIntegrated":true}
                """))
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("message").value("Pedido não encontrado"));
    }
}
