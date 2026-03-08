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

package org.springframework.context.event;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.SpringProperties;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

// 事件监听方法的处理，是一个 bfpp 对象
// 注册监听器
/**
 * Registers {@link EventListener} methods as individual {@link ApplicationListener} instances.
 * Implements {@link BeanFactoryPostProcessor} (as of 5.1) primarily for early retrieval,
 * avoiding AOP checks for this processor bean and its {@link EventListenerFactory} delegates.
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @author Sebastien Deleuze
 * @since 4.2
 * @see EventListenerFactory
 * @see DefaultEventListenerFactory
 */
public class EventListenerMethodProcessor
		implements SmartInitializingSingleton, ApplicationContextAware, BeanFactoryPostProcessor {

	// 没有设置 spring.spel.ignore 配置，所以默认得到的是 false
	/**
	 * Boolean flag controlled by a {@code spring.spel.ignore} system property that instructs Spring to
	 * ignore SpEL, i.e. to not initialize the SpEL infrastructure.
	 * <p>The default is "false".
	 */
	private static final boolean shouldIgnoreSpel = SpringProperties.getFlag("spring.spel.ignore");


	protected final Log logger = LogFactory.getLog(getClass());

	// ApplicationContextAware 对象，在实例化后会设置这个属性，获取到当前的 application 对象
	@Nullable
	private ConfigurableApplicationContext applicationContext;

	// 在调用 postProcessBeanFactory 的时候设置了 beanFactory
	@Nullable
	private ConfigurableListableBeanFactory beanFactory;

	// 在 postProcessBeanFactory 方法的额时候设置了这个属性
	// 存放的是当前 beanFactory 中所有的 EventListenerFactory 这个 bean 的 list
	// 默认在 AnnotationConfigUtils 的时候注册了 DefaultEventListenerFactory
	@Nullable
	private List<EventListenerFactory> eventListenerFactories;

	// 构造函数设置，得到的是 EventExpressionEvaluator
	@Nullable
	private final EventExpressionEvaluator evaluator;

	private final Set<Class<?>> nonAnnotatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(64));


	// 构造函数
	public EventListenerMethodProcessor() {
		// 默认是 false
		if (shouldIgnoreSpel) {
			this.evaluator = null;
		}
		else {
			this.evaluator = new EventExpressionEvaluator();
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		Assert.isTrue(applicationContext instanceof ConfigurableApplicationContext,
				"ApplicationContext does not implement ConfigurableApplicationContext");
		this.applicationContext = (ConfigurableApplicationContext) applicationContext;
	}

	// 处理 bfpp 的逻辑
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		// 设置当前对象的 beanFactory
		this.beanFactory = beanFactory;
		// 从 beanFactory 中获取到 EventListenerFactory 类型的所有 bean
        // 返回map，key 是 EventListenerFactory.class, value 是 DefaultEventListenerFactory
		Map<String, EventListenerFactory> beans = beanFactory.getBeansOfType(EventListenerFactory.class, false, false);
		// 创建一个 list 集合，存放所有的 EventListenerFactory 对象
		List<EventListenerFactory> factories = new ArrayList<>(beans.values());
		// 排序
		AnnotationAwareOrderComparator.sort(factories);
		// 赋值给 eventListenerFactories 对象
		this.eventListenerFactories = factories;
	}


	// 在所有当理 bean 都实例化后调用这个方法处理
	@Override
	public void afterSingletonsInstantiated() {
		// 当前的 beanFactory 对象
		ConfigurableListableBeanFactory beanFactory = this.beanFactory;
		Assert.state(this.beanFactory != null, "No ConfigurableListableBeanFactory set");
		// 获取到所有的 bean 名称
		String[] beanNames = beanFactory.getBeanNamesForType(Object.class);
		// 遍历所有的 bean 名称
		for (String beanName : beanNames) {
			// 判断是否是非作用域代理
			if (!ScopedProxyUtils.isScopedTarget(beanName)) {
				Class<?> type = null;
				try {
					// 获取对应的 bean 的类型
					type = AutoProxyUtils.determineTargetClass(beanFactory, beanName);
				}
				catch (Throwable ex) {
					// An unresolvable bean type, probably from a lazy bean - let's ignore it.
					if (logger.isDebugEnabled()) {
						logger.debug("Could not resolve target class for bean with name '" + beanName + "'", ex);
					}
				}
				if (type != null) {
					// 如果属于 ScopedObject
					if (ScopedObject.class.isAssignableFrom(type)) {
						try {
							// 获取目标的类型
							Class<?> targetClass = AutoProxyUtils.determineTargetClass(
									beanFactory, ScopedProxyUtils.getTargetBeanName(beanName));
							if (targetClass != null) {
								// 赋值给 type
								type = targetClass;
							}
						}
						catch (Throwable ex) {
							// An invalid scoped proxy arrangement - let's ignore it.
							if (logger.isDebugEnabled()) {
								logger.debug("Could not resolve target bean for scoped proxy '" + beanName + "'", ex);
							}
						}
					}
					try {
						// 调用 processBean 处理 bean
						// 传入的是 beanName 和对应的类型
						processBean(beanName, type);
					}
					catch (Throwable ex) {
						throw new BeanInitializationException("Failed to process @EventListener " +
								"annotation on bean with name '" + beanName + "'", ex);
					}
				}
			}
		}
	}

	// 核心处理逻辑，处理 bean 的过程
	private void processBean(final String beanName, final Class<?> targetType) {
		// 如果 nonAnnotatedClasses 缓存中没有这个 type 类型
		if (!this.nonAnnotatedClasses.contains(targetType) &&
				// 当前这个 bean 是否可能是 EventListener 注解修饰
				AnnotationUtils.isCandidateClass(targetType, EventListener.class) &&
				// 是否非 spring 内部的类，避免扫到 Spring 内部的事件类型，重复处理了
				!isSpringContainerClass(targetType)) {

			Map<Method, EventListener> annotatedMethods = null;
			try {
				// 最后得到的结果就是一个 map 集合
				// 存储的是 method 和对应的 EventListener 注解
				annotatedMethods = MethodIntrospector.selectMethods(targetType,
						(MethodIntrospector.MetadataLookup<EventListener>) method ->
								// 获取 method 方法上的 EventListener 注解信息，并返回
								AnnotatedElementUtils.findMergedAnnotation(method, EventListener.class));
			}
			catch (Throwable ex) {
				// An unresolvable type in a method signature, probably from a lazy bean - let's ignore it.
				if (logger.isDebugEnabled()) {
					logger.debug("Could not resolve methods for bean with name '" + beanName + "'", ex);
				}
			}

			// 如果处理后得到的方法是空的，也就是没有该注解的方法
			if (CollectionUtils.isEmpty(annotatedMethods)) {
				// 将这个类 targetType 放到 nonAnnotatedClasses 中缓存
				// 下次就直接跳过这个类
				this.nonAnnotatedClasses.add(targetType);
				if (logger.isTraceEnabled()) {
					logger.trace("No @EventListener annotations found on bean class: " + targetType.getName());
				}
			}
			else {
				// 对应 annotatedMethods 有数据，也就是处理后有得到该注解的方法
				// Non-empty set of methods
				// app 容器对象
				ConfigurableApplicationContext context = this.applicationContext;
				Assert.state(context != null, "No ApplicationContext set");
				// 所有的 EventListenerFactory 对象
				// 默认是 DefaultEventListenerFactory，有事务的话是加了 TransactionalEventListenerFactory
				List<EventListenerFactory> factories = this.eventListenerFactories;
				Assert.state(factories != null, "EventListenerFactory List not initialized");
				// 遍历处理后得到的所有注解方法
				for (Method method : annotatedMethods.keySet()) {
					// 遍历所有的 EventListenerFactory 方法
					for (EventListenerFactory factory : factories) {
						// 判断是否 EventListenerFactory 支持这个方法
						// 默认 DefaultEventListenerFactory 是 true
						// 事务 TransactionalEventListenerFactory 是判断当前方法上是否有 TransactionalEventListener
						if (factory.supportsMethod(method)) {
							// context.getType(beanName) ==> 获取对应的 beanName 的类型
							// 获得对应的方法
							Method methodToUse = AopUtils.selectInvocableMethod(method, context.getType(beanName));
							ApplicationListener<?> applicationListener =
									// 调用 factory 创建监听器
									// 传入 beanName、对应声明的类 targetType 和对应的方法 methodToUse
									factory.createApplicationListener(beanName, targetType, methodToUse);
							// 判断如果对应的生成的监听器属于 ApplicationListenerMethodAdapter
							if (applicationListener instanceof ApplicationListenerMethodAdapter) {
								// 调用 init 初始化
								((ApplicationListenerMethodAdapter) applicationListener).init(context, this.evaluator);
							}
							// 调用 context.addApplicationListener，添加这个监听器到容器中
							// 这时候这个创建的监听器就添加到多播器中了，后续事件通知则走正常流程
							context.addApplicationListener(applicationListener);
							break;
						}
					}
				}
				if (logger.isDebugEnabled()) {
					logger.debug(annotatedMethods.size() + " @EventListener methods processed on bean '" +
							beanName + "': " + annotatedMethods);
				}
			}
		}
	}

	// 判断是否是 org.springframework 包下的，且没有被标记为 Component 注解
	/**
	 * Determine whether the given class is an {@code org.springframework}
	 * bean class that is not annotated as a user or test {@link Component}...
	 * which indicates that there is no {@link EventListener} to be found there.
	 * @since 5.1
	 */
	private static boolean isSpringContainerClass(Class<?> clazz) {
		return (clazz.getName().startsWith("org.springframework.") &&
				!AnnotatedElementUtils.isAnnotated(ClassUtils.getUserClass(clazz), Component.class));
	}

}
