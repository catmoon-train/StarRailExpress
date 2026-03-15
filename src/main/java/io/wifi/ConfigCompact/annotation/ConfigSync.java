package io.wifi.ConfigCompact.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 指定注解可应用于字段和方法
@Target({ElementType.FIELD})
// 指定注解在运行时保留，以便通过反射读取
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigSync {
    // 可以定义一些属性，例如一个默认值
    boolean shouldSync() default true;
}