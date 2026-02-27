package org.springframework.mytest.expand.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

/**
 * @author linzherong
 * @date 2026/2/1
 */
//@Aspect
//@Component
public class LoggingAspect {

//	@Pointcut("execution(* org.springframework.mytest.bean.components.aop..*(..))")
//	public void myPoint() {}

//	@Before("myPoint()")
	public void beforeMethod(JoinPoint joinPoint) {
		String methodName = joinPoint.getSignature().getName();
		System.out.println("before，执行方法 " + methodName);
	}

//	@After("myPoint()")
	public void afterMethod(JoinPoint joinPoint) {
		String methodName = joinPoint.getSignature().getName();
		System.out.println("after，执行方法 " + methodName);
	}

//	@Around("myPoint()")
	public Object aroundMethod(ProceedingJoinPoint joinPoint) throws Throwable {
		String methodName = joinPoint.getSignature().getName();
		System.out.println("around，执行方法 " + methodName);
		Object proceed = joinPoint.proceed();
		System.out.println("around，执行方法，结束");
		return proceed;
	}

//	@AfterThrowing("myPoint()")
	public void afterThrowingMethod(JoinPoint joinPoint) {
		String methodName = joinPoint.getSignature().getName();
		System.out.println("after-throwing，执行方法 " + methodName);
	}

//	@AfterReturning("myPoint()")
	public void afterReturningMethod(JoinPoint joinPoint) {
		String methodName = joinPoint.getSignature().getName();
		System.out.println("after-returning，执行方法 " + methodName);
	}
}
