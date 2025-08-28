package com.msvcbilling.config;


import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class CommonPointcuts {
    @Pointcut("execution(* com.msvcbilling.services.*.*(..))")
    public void greetingLoggerServices(){};

    @Pointcut("execution(* com.msvcbilling.controllers.*.*(..))")
    public void greetingLoggerControllers(){};
}
