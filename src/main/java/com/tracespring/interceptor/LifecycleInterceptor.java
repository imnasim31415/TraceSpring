package com.tracespring.interceptor;

import com.tracespring.filter.RequestTracingFilter;
import com.tracespring.model.LifecycleStage;
import com.tracespring.service.TraceStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Sits inside DispatcherServlet's handler chain.
 * Fires in order: preHandle → [controller] → postHandle → afterCompletion.
 * Reads requestId from request attribute set by RequestTracingFilter.
 */
@Slf4j
@RequiredArgsConstructor
public class LifecycleInterceptor implements HandlerInterceptor {

    private final TraceStore traceStore;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestId = (String) request.getAttribute(RequestTracingFilter.REQUEST_ID_ATTR);
        if (requestId == null) return true;

        traceStore.find(requestId).ifPresent(trace -> {
            trace.recordStage(LifecycleStage.INTERCEPTOR_PRE_HANDLE);

            if (handler instanceof HandlerMethod hm) {
                String signature = hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName();
                trace.setHandlerMethod(signature);
                log.info("[INTERCEPTOR PRE ] handler={} | requestId={}", signature, requestId);
            }
        });

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
        String requestId = (String) request.getAttribute(RequestTracingFilter.REQUEST_ID_ATTR);
        if (requestId == null) return;

        traceStore.find(requestId).ifPresent(trace -> {
            trace.recordStage(LifecycleStage.INTERCEPTOR_POST_HANDLE);
            log.info("[INTERCEPTOR POST] status={} | requestId={}", response.getStatus(), requestId);
        });
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        String requestId = (String) request.getAttribute(RequestTracingFilter.REQUEST_ID_ATTR);
        if (requestId == null) return;

        traceStore.find(requestId).ifPresent(trace -> {
            trace.recordStage(LifecycleStage.INTERCEPTOR_AFTER_COMPLETION);

            if (ex != null) {
                String info = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                trace.setExceptionInfo(info);
                log.warn("[INTERCEPTOR DONE] exception={} | requestId={}", info, requestId);
            } else {
                log.info("[INTERCEPTOR DONE] clean | requestId={}", requestId);
            }
        });
    }
}
