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

package org.springframework.aop.interceptor;

import java.io.Serializable;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.Nullable;

/**
 * Interceptor that exposes the current {@link org.aopalliance.intercept.MethodInvocation}
 * as a thread-local object. We occasionally need to do this; for example, when a pointcut
 * (e.g. an AspectJ expression pointcut) needs to know the full invocation context.
 *
 * <p>Don't use this interceptor unless this is really necessary. Target objects should
 * not normally know about Spring AOP, as this creates a dependency on Spring API.
 * Target objects should be plain POJOs as far as possible.
 *
 * <p>If used, this interceptor will normally be the first in the interceptor chain.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 */
@SuppressWarnings("serial")
public final class ExposeInvocationInterceptor implements MethodInterceptor, PriorityOrdered, Serializable {

	/** Singleton instance of this class. */
	public static final ExposeInvocationInterceptor INSTANCE = new ExposeInvocationInterceptor();

	/**
	 * Singleton advisor for this class. Use in preference to INSTANCE when using
	 * Spring AOP, as it prevents the need to create a new Advisor to wrap the instance.
	 */
	public static final Advisor ADVISOR = new DefaultPointcutAdvisor(INSTANCE) {
		@Override
		public String toString() {
			return ExposeInvocationInterceptor.class.getName() +".ADVISOR";
		}
	};

	private static final ThreadLocal<MethodInvocation> invocation =
			new NamedThreadLocal<>("Current AOP method invocation");


	/**
	 * Return the AOP Alliance MethodInvocation object associated with the current invocation.
	 * @return the invocation object associated with the current invocation
	 * @throws IllegalStateException if there is no AOP invocation in progress,
	 * or if the ExposeInvocationInterceptor was not added to this interceptor chain
	 */
	public static MethodInvocation currentInvocation() throws IllegalStateException {
		MethodInvocation mi = invocation.get();
		if (mi == null) {
			throw new IllegalStateException(
					"No MethodInvocation found: Check that an AOP invocation is in progress and that the " +
					"ExposeInvocationInterceptor is upfront in the interceptor chain. Specifically, note that " +
					"advices with order HIGHEST_PRECEDENCE will execute before ExposeInvocationInterceptor! " +
					"In addition, ExposeInvocationInterceptor and ExposeInvocationInterceptor.currentInvocation() " +
					"must be invoked from the same thread.");
		}
		return mi;
	}


	/**
	 * Ensures that only the canonical instance can be created.
	 */
	private ExposeInvocationInterceptor() {
	}

	// aop 动态代理，第一个拦截器就是这个 ExposeInvocationInterceptor，所以这是首先要执行的
	@Override
	@Nullable
	public Object invoke(MethodInvocation mi) throws Throwable {
		// 先获取当前正在执行的 methodInterceptor 对象，从当前线程 threadLocal 获取
		// 拿到的就是其他动态代理执行的 mi 对象
		// 拿出来后先替换成自己，表示当前正在执行自己这个 aop，mi 是 CglibMethodInvocation
		MethodInvocation oldInvocation = invocation.get();
		invocation.set(mi);
		try {
			// 继续调用 mi.proceed，会走到 ReflectiveMethodInvocation.proceed 获取下一个链
			return mi.proceed();
		}
		finally {
			// 执行完，恢复线程，把其他的 aop 在执行的 mi 放回去
			invocation.set(oldInvocation);
		}
	}

	@Override
	public int getOrder() {
		return PriorityOrdered.HIGHEST_PRECEDENCE + 1;
	}

	/**
	 * Required to support serialization. Replaces with canonical instance
	 * on deserialization, protecting Singleton pattern.
	 * <p>Alternative to overriding the {@code equals} method.
	 */
	private Object readResolve() {
		return INSTANCE;
	}

}
