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

import org.springframework.aop.AfterAdvice;
import org.springframework.lang.Nullable;

/**
 * Spring AOP advice wrapping an AspectJ after-throwing advice method.
 *
 * @author Rod Johnson
 * @since 2.0
 */
@SuppressWarnings("serial")
public class AspectJAfterThrowingAdvice extends AbstractAspectJAdvice
		implements MethodInterceptor, AfterAdvice, Serializable {

	public AspectJAfterThrowingAdvice(
			Method aspectJBeforeAdviceMethod, AspectJExpressionPointcut pointcut, AspectInstanceFactory aif) {

		super(aspectJBeforeAdviceMethod, pointcut, aif);
	}


	@Override
	public boolean isBeforeAdvice() {
		return false;
	}

	@Override
	public boolean isAfterAdvice() {
		return true;
	}

	@Override
	public void setThrowingName(String name) {
		setThrowingNameNoCheck(name);
	}

	// 责任链 5
	@Override
	@Nullable
	public Object invoke(MethodInvocation mi) throws Throwable {
		try {
			// 调用下个责任链
			return mi.proceed();
		}
		// 如果抛出异常
		catch (Throwable ex) {
			// 判断抛出的异常是否是和这个 afterThrowing 指定的异常类型匹配上
			if (shouldInvokeOnThrowing(ex)) {
				// 如果匹配上，调用增强逻辑
				invokeAdviceMethod(getJoinPointMatch(), null, ex);
			}
			// 最后再把异常抛出去，也就是先执行增强逻辑，再抛出异常
			throw ex;
		}
	}

	/**
	 * In AspectJ semantics, after throwing advice that specifies a throwing clause
	 * is only invoked if the thrown exception is a subtype of the given throwing type.
	 */
	private boolean shouldInvokeOnThrowing(Throwable ex) {
		return getDiscoveredThrowingType().isAssignableFrom(ex.getClass());
	}

}
