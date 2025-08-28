package com.msvcbilling.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@Aspect
public class LoggerAspect {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Around("com.msvcbilling.config.CommonPointcuts.greetingLoggerServices()")
    public Object loggerAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().getName();
        String args = Arrays.toString(joinPoint.getArgs());
        Object result = null;
        try {
            logger.info("El metodo {} con parametros {}", method, args);
            result = joinPoint.proceed();
            logger.info("El metodo : {}() retorna el resultado: {}", method, result);
            return result;
        } catch (
                Throwable e) {
            logger.error("Error en la llamada del metodo {}", method);
            throw e;
        }
    }
}
