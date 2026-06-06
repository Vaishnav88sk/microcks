/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.microcks.web;

import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.event.MockInvocationEvent;
import io.github.microcks.util.delay.DelaySpec;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * This is a test case for the MockControllerCommons class.
 * @author laurent
 */
class MockControllerCommonsTest {

   @Test
   void shouldUseDelayAndStrategyFromHeaders() {
      HttpHeaders headers = new HttpHeaders();
      headers.add(MockControllerCommons.X_MICROCKS_DELAY_HEADER, "500");
      headers.add(MockControllerCommons.X_MICROCKS_DELAY_STRATEGY_HEADER, "random-10");

      DelaySpec spec = MockControllerCommons.getDelay(headers, null, "fixed");
      assertNotNull(spec);
      assertEquals(500L, spec.baseValue());
      assertEquals("random-10", spec.strategyName());
   }

   @Test
   void shouldBeCaseInsensitiveForStrategyHeader() {
      HttpHeaders headers = new HttpHeaders();
      headers.add(MockControllerCommons.X_MICROCKS_DELAY_HEADER, "250");
      headers.add(MockControllerCommons.X_MICROCKS_DELAY_STRATEGY_HEADER, "RaNdOm-20");

      DelaySpec spec = MockControllerCommons.getDelay(headers, null, "fixed");
      assertNotNull(spec);
      assertEquals(250L, spec.baseValue());
      assertEquals("RaNdOm-20", spec.strategyName()); // keep header value as provided
   }

   @Test
   void shouldFallBackToParameterWhenHeaderValueInvalid() {
      HttpHeaders headers = new HttpHeaders();
      headers.add(MockControllerCommons.X_MICROCKS_DELAY_HEADER, "not-a-number");

      DelaySpec spec = MockControllerCommons.getDelay(headers, 100L, "random");
      assertNotNull(spec);
      assertEquals(100L, spec.baseValue());
      assertEquals("random", spec.strategyName());
   }

   @Test
   void publishMockInvocationShouldEmitNonNegativeDuration() {
      // Regression test: before the fix, duration was computed as
      // 'startTime - currentTimeMillis()' which is always a large negative number.
      // Real-world impact: the Microcks dashboard would show negative response times
      // for every mocked API invocation (REST, SOAP, gRPC, GraphQL).

      Service service = new Service();
      service.setName("PetstoreService");
      service.setVersion("1.0");

      Response response = new Response();
      response.setName("200 OK");

      AtomicReference<MockInvocationEvent> captured = new AtomicReference<>();
      ApplicationContext ctx = mock(ApplicationContext.class);
      doAnswer(inv -> {
         captured.set(inv.getArgument(0));
         return null;
      }).when(ctx).publishEvent(any(MockInvocationEvent.class));

      // Simulate a 10ms invocation that happened in the past.
      long startTime = System.currentTimeMillis() - 10;
      MockControllerCommons.publishMockInvocation(ctx, this, service, response, startTime);

      assertNotNull(captured.get(), "Event must have been published");
      assertTrue(captured.get().getDuration() >= 0,
            "Invocation duration must be non-negative but was: " + captured.get().getDuration());
   }
}
