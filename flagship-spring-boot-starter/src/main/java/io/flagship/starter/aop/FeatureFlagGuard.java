package io.flagship.starter.aop;

import java.lang.annotation.*;

/**
 * Method guard that executes the method only if the feature flag evaluates to enabled / matched.
 * If disabled:
 * - If fallbackMethod is defined, calls fallbackMethod.
 * - Else if throwOnDisabled is true, throws FeatureDisabledException.
 * - Else returns null / default primitive value.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FeatureFlagGuard {
    /**
     * Feature flag key to check.
     */
    String flag();

    /**
     * SpEL expression to extract the user key from method parameters.
     * Example: "#userId", "#order.customerId", "'admin'".
     */
    String userKeyExpression() default "'anonymous'";

    /**
     * Name of a fallback method in the same class to invoke if the flag is disabled.
     */
    String fallbackMethod() default "";

    /**
     * If true and the flag is disabled without a fallbackMethod, throw FeatureDisabledException.
     */
    boolean throwOnDisabled() default false;

    /**
     * Expected variation name. If empty (default), checks boolean isEnabled().
     */
    String expectedVariation() default "";
}
