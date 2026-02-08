/*
 * Copyright 2002-2018 the original author or authors.
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

import org.aopalliance.aop.Advice;

import org.springframework.aop.Pointcut;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * AspectJPointcutAdvisor that adapts an {@link AbstractAspectJAdvice}
 * to the {@link org.springframework.aop.PointcutAdvisor} interface.
 *
 * @author Adrian Colyer
 * @author Juergen Hoeller
 * @since 2.0
 */
public class AspectJPointcutAdvisor implements PointcutAdvisor, Ordered {

	// 当前管理器对应的切面通知
	private final AbstractAspectJAdvice advice;

	// ComposablePointcut，通过调用 advice.buildSafePointcut() 获取
	private final Pointcut pointcut;

	@Nullable
	private Integer order;


	// 构造函数，实例化的时候会调用到这里
	/**
	 * Create a new AspectJPointcutAdvisor for the given advice.
	 * @param advice the AbstractAspectJAdvice to wrap
	 */
	public AspectJPointcutAdvisor(AbstractAspectJAdvice advice) {
		Assert.notNull(advice, "Advice must not be null");
		// 设置 advice 属性，就是当前设置进来的具体切面通知 advice
		this.advice = advice;
		// 设置 pointcut 切点，调用切面的 buildSafePointcut 返回一个 ComposablePointcut
		this.pointcut = advice.buildSafePointcut();
	}


	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		if (this.order != null) {
			return this.order;
		}
		else {
			return this.advice.getOrder();
		}
	}

	@Override
	public boolean isPerInstance() {
		return true;
	}

	@Override
	public Advice getAdvice() {
		return this.advice;
	}

	@Override
	public Pointcut getPointcut() {
		return this.pointcut;
	}

	/**
	 * Return the name of the aspect (bean) in which the advice was declared.
	 * @since 4.3.15
	 * @see AbstractAspectJAdvice#getAspectName()
	 */
	public String getAspectName() {
		return this.advice.getAspectName();
	}


	@Override
	public boolean equals(@Nullable Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof AspectJPointcutAdvisor)) {
			return false;
		}
		AspectJPointcutAdvisor otherAdvisor = (AspectJPointcutAdvisor) other;
		return this.advice.equals(otherAdvisor.advice);
	}

	@Override
	public int hashCode() {
		return AspectJPointcutAdvisor.class.hashCode() * 29 + this.advice.hashCode();
	}

}
