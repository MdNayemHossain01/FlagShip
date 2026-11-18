package io.flagship.starter.aop;

import io.flagship.core.context.EvaluationContext;
import io.flagship.starter.client.FlagShipClient;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

@Aspect
public class FeatureFlagGuardAspect {
    private final FlagShipClient flagClient;
    private final ExpressionParser parser = new SpelExpressionParser();

    public FeatureFlagGuardAspect(FlagShipClient flagClient) {
        this.flagClient = flagClient;
    }

    @Around("@annotation(guard)")
    public Object guardWithFlag(ProceedingJoinPoint pjp, FeatureFlagGuard guard) throws Throwable {
        String userKey = resolveUserKey(pjp, guard.userKeyExpression());
        EvaluationContext ctx = EvaluationContext.builder(userKey).build();

        boolean allowed;
        if (!guard.expectedVariation().isBlank()) {
            var detail = flagClient.evaluateDetail(guard.flag(), ctx, "");
            allowed = guard.expectedVariation().equalsIgnoreCase(detail.variationName());
        } else {
            allowed = flagClient.isEnabled(guard.flag(), ctx, false);
        }

        if (allowed) {
            return pjp.proceed();
        }

        if (!guard.fallbackMethod().isBlank()) {
            return invokeFallbackMethod(pjp, guard.fallbackMethod());
        }

        if (guard.throwOnDisabled()) {
            throw new FeatureDisabledException(guard.flag(), userKey);
        }

        return getDefaultReturnValue(pjp);
    }

    @Around("@annotation(gate)")
    public Object guardWithGate(ProceedingJoinPoint pjp, FeatureGate gate) throws Throwable {
        String userKey = resolveUserKey(pjp, gate.userKeyExpression());
        boolean enabled = flagClient.isEnabled(gate.flag(), EvaluationContext.builder(userKey).build(), false);
        if (enabled) {
            return pjp.proceed();
        }
        if (!gate.fallbackMethod().isBlank()) {
            return invokeFallbackMethod(pjp, gate.fallbackMethod());
        }
        return getDefaultReturnValue(pjp);
    }

    private Object invokeFallbackMethod(ProceedingJoinPoint pjp, String methodName) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method fallback = pjp.getTarget().getClass().getDeclaredMethod(methodName, sig.getParameterTypes());
        fallback.setAccessible(true);
        return fallback.invoke(pjp.getTarget(), pjp.getArgs());
    }

    private Object getDefaultReturnValue(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Class<?> returnType = sig.getReturnType();
        if (returnType.equals(boolean.class)) return false;
        if (returnType.equals(int.class)) return 0;
        if (returnType.equals(long.class)) return 0L;
        if (returnType.equals(double.class)) return 0.0;
        return null;
    }

    private String resolveUserKey(ProceedingJoinPoint pjp, String spel) {
        try {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            String[] paramNames = sig.getParameterNames();
            Object[] args = pjp.getArgs();
            StandardEvaluationContext ctx = new StandardEvaluationContext();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    ctx.setVariable(paramNames[i], args[i]);
                }
            }
            Object val = parser.parseExpression(spel).getValue(ctx);
            return val != null ? val.toString() : "anonymous";
        } catch (Exception e) {
            return "anonymous";
        }
    }
}
