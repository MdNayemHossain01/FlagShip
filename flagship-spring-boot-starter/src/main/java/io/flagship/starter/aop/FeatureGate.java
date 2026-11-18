package io.flagship.starter.aop;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FeatureGate {
    String flag();
    String userKeyExpression() default "'anonymous'";
    String fallbackMethod() default "";
}
