package io.myclaw.server.audit;

import io.myclaw.core.json.Json;
import io.myclaw.server.annotation.RepeatSubmit;
import io.myclaw.server.error.RepeatSubmitException;
import io.myclaw.server.utils.RedisLockUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RepeatSubmitAspectTest {
    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void buildsStableUserMethodUriAndParameterKey() {
        RedisLockUtil locks = mock(RedisLockUtil.class);
        RepeatSubmitAspect aspect = new RepeatSubmitAspect(locks, JsonMapper.builder().build());
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.getArgs()).thenReturn(new Object[]{Json.obj().put("message", "hello")});
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chat");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("alice", "n/a", java.util.List.of()));

        String first = aspect.buildLockKey(point, true);
        String second = aspect.buildLockKey(point, true);

        assertThat(first).isEqualTo(second).startsWith("myclaw:repeat-submit:alice:POST:/api/chat:");
        assertThat(first.substring(first.lastIndexOf(':') + 1)).hasSize(64);
    }

    @Test
    void rejectsDuplicateWithoutCallingController() throws Throwable {
        RedisLockUtil locks = mock(RedisLockUtil.class);
        when(locks.tryLock(anyString(), eq(1000L))).thenReturn(false);
        RepeatSubmitAspect aspect = new RepeatSubmitAspect(locks, JsonMapper.builder().build());
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("ChatController.chat(..)");
        when(point.getSignature()).thenReturn(signature);
        when(point.getArgs()).thenReturn(new Object[]{"hello"});
        RepeatSubmit annotation = mock(RepeatSubmit.class);
        when(annotation.interval()).thenReturn(1000L);
        when(annotation.checkParams()).thenReturn(true);
        when(annotation.message()).thenReturn("请勿重复提交");

        assertThatThrownBy(() -> aspect.around(point, annotation))
                .isInstanceOf(RepeatSubmitException.class).hasMessage("请勿重复提交");
        verify(point, never()).proceed();
    }
}