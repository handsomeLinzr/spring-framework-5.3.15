/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.aop.aspectj;

import java.io.Serializable;
import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.weaver.tools.JoinPointMatch;

import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.lang.Nullable;

/**
 * Spring AOP around advice (MethodInterceptor) that wraps
 * an AspectJ advice method. Exposes ProceedingJoinPoint.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 2.0
 */
@SuppressWarnings("serial")
public class AspectJAroundAdvice extends AbstractAspectJAdvice implements MethodInterceptor, Serializable {

	public AspectJAroundAdvice(
			Method aspectJAroundAdviceMethod, AspectJExpressionPointcut pointcut, AspectInstanceFactory aif) {

		super(aspectJAroundAdviceMethod, pointcut, aif);
	}


	@Override
	public boolean isBeforeAdvice() {
		return false;
	}

	@Override
	public boolean isAfterAdvice() {
		return false;
	}

	@Override
	protected boolean supportsProceedingJoinPoint() {
		return true;
	}

	// 责任链 3
	@Override
	@Nullable
	public Object invoke(MethodInvocation mi) throws Throwable {
		// 调用 aop 拦截器，around 会进入这个方法
		// 先验证 mi 必须是 ProxyMethodInvocation 类型，这里肯定是，mi 是 CglibMethodInvocation
		if (!(mi instanceof ProxyMethodInvocation)) {
			throw new IllegalStateException("MethodInvocation is not a Spring ProxyMethodInvocation: " + mi);
		}
		// 强转
		ProxyMethodInvocation pmi = (ProxyMethodInvocation) mi;
		// 获取 ProceedingJoinPoint，around 这个特殊的增强用到，实际的对象是 MethodInvocationProceedingJoinPoint
		ProceedingJoinPoint pjp = lazyGetProceedingJoinPoint(pmi);
		// 从 mi 中的缓存获取 jpm，一般默认得到 null
		JoinPointMatch jpm = getJoinPointMatch(pmi);
		// invoke 调用，根据给定的参数，调用增强的方法，这里其实一般就传过去 pjp 调用增强方法
		// 在增强方法中，需要调用 pjp.proceed() 方法，才会调到下个拦截器链，不然就不会往下走
		return invokeAdviceMethod(pjp, jpm, null, null);
	}

	/**
	 * Return the ProceedingJoinPoint for the current invocation,
	 * instantiating it lazily if it hasn't been bound to the thread already.
	 * @param rmi the current Spring AOP ReflectiveMethodInvocation,
	 * which we'll use for attribute binding
	 * @return the ProceedingJoinPoint to make available to advice methods
	 */
	protected ProceedingJoinPoint lazyGetProceedingJoinPoint(ProxyMethodInvocation rmi) {
		return new MethodInvocationProceedingJoinPoint(rmi);
	}

}
