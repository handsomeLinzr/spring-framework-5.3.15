/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.aop.config;

import java.lang.reflect.Method;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

// aop配置中的记录 method 用
// 这是一个 factoryBean 对象，getObject 方法返回对应的 method 通知方法
// 且实现了 beanFactoryAware，实例化后有 setBeanFactory 的方法回调
/**
 * {@link FactoryBean} implementation that locates a {@link Method} on a specified bean.
 *
 * @author Rob Harrop
 * @since 2.0
 */
public class MethodLocatingFactoryBean implements FactoryBean<Method>, BeanFactoryAware {

	// 解析的时候会设置进来对应的通知 bean 的名称，也就是通知的方法 method 所在的 bean 对象名称
	@Nullable
	private String targetBeanName;

	// 在解析的时候会设置进来通知方法名称
	@Nullable
	private String methodName;

	// 在 setBeanFactory 这个回调方法中，会根据 beanFactory、targetBeanName、methodName 来找到对应的 method 对象
	@Nullable
	private Method method;


	/**
	 * Set the name of the bean to locate the {@link Method} on.
	 * <p>This property is required.
	 * @param targetBeanName the name of the bean to locate the {@link Method} on
	 */
	public void setTargetBeanName(String targetBeanName) {
		this.targetBeanName = targetBeanName;
	}

	/**
	 * Set the name of the {@link Method} to locate.
	 * <p>This property is required.
	 * @param methodName the name of the {@link Method} to locate
	 */
	public void setMethodName(String methodName) {
		this.methodName = methodName;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		if (!StringUtils.hasText(this.targetBeanName)) {
			throw new IllegalArgumentException("Property 'targetBeanName' is required");
		}
		if (!StringUtils.hasText(this.methodName)) {
			throw new IllegalArgumentException("Property 'methodName' is required");
		}

		// 从当前的 beanFactory 中获取 targetBeanName 的类型
		Class<?> beanClass = beanFactory.getType(this.targetBeanName);
		if (beanClass == null) {
			throw new IllegalArgumentException("Can't determine type of bean with name '" + this.targetBeanName + "'");
		}
		//
		this.method = BeanUtils.resolveSignature(this.methodName, beanClass);

		if (this.method == null) {
			throw new IllegalArgumentException("Unable to locate method [" + this.methodName +
					"] on bean [" + this.targetBeanName + "]");
		}
	}


	@Override
	@Nullable
	public Method getObject() throws Exception {
		return this.method;
	}

	@Override
	public Class<Method> getObjectType() {
		return Method.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

}
