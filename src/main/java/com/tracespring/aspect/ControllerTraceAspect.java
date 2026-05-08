package com.tracespring.aspect;

import com.tracespring.filter.RequestTracingFilter;
import com.tracespring.model.LifecycleStage;
import com.tracespring.service.TraceStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * AOP advice that fires exactly when a @RestController method body executes.
 * This is the only reliable hook for CONTROLLER_EXECUTION — interceptor.preHandle
 * fires before the controller, and postHandle fires after, so neither captures
 * the controller entry precisely. This aspect does.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ControllerTraceAspect {

    private final TraceStore traceStore;

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object traceControllerExecution(ProceedingJoinPoint pjp) throws Throwable {
        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attrs == null) return pjp.proceed();

        String requestId = (String) attrs.getRequest()
                                         .getAttribute(RequestTracingFilter.REQUEST_ID_ATTR);
        if (requestId == null) return pjp.proceed();

        String methodLabel = pjp.getTarget().getClass().getSimpleName()
                             + "#" + pjp.getSignature().getName();

        log.info("[CONTROLLER     ] {} | requestId={}", methodLabel, requestId);
        traceStore.find(requestId)
                  .ifPresent(trace -> trace.recordStage(LifecycleStage.CONTROLLER_EXECUTION));

        return pjp.proceed();
    }
}
