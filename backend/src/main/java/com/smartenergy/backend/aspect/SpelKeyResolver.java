package com.smartenergy.backend.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

/**
 * 把切面上的 SpEL 表达式对方法入参求值，得到限流/加锁的动态 key。
 * 同时暴露形参名、p0/p1.. 与 a0/a1.. 三种变量写法，兼容编译期未带 -parameters 的情况。
 */
@Slf4j
final class SpelKeyResolver {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private SpelKeyResolver() {
    }

    static String resolve(ProceedingJoinPoint joinPoint, String expression) {
        if (expression == null || expression.isBlank()) {
            return "";
        }
        Signature rawSignature = joinPoint.getSignature();
        if (!(rawSignature instanceof MethodSignature signature)) {
            throw new IllegalArgumentException("Rate-limit/lock SpEL key requires a method signature");
        }
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] names = NAME_DISCOVERER.getParameterNames(method);
        for (int i = 0; i < args.length; i++) {
            if (names != null && i < names.length) {
                context.setVariable(names[i], args[i]);
            }
            context.setVariable("p" + i, args[i]);
            context.setVariable("a" + i, args[i]);
        }

        Expression parsed = PARSER.parseExpression(expression);
        Object value = parsed.getValue(context);
        String result = value == null ? "" : value.toString();

        if (result.isEmpty()) {
            log.warn("SpEL 表达式 '{}' 对方法 {}.{} 解析结果为空，可能导致锁/限流作用范围扩大",
                    expression, method.getDeclaringClass().getSimpleName(), method.getName());
        }
        return result;
    }
}
