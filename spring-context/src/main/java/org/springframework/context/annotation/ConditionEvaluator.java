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

package org.springframework.context.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.MultiValueMap;

/**
 * Internal class used to evaluate {@link Conditional} annotations.
 *
 * @author Phillip Webb
 * @author Juergen Hoeller
 * @since 4.0
 */
class ConditionEvaluator {

	// ConditionContextImpl
	private final ConditionContextImpl context;


	/**
	 * Create a new {@link ConditionEvaluator} instance.
	 */
	public ConditionEvaluator(@Nullable BeanDefinitionRegistry registry,
			@Nullable Environment environment, @Nullable ResourceLoader resourceLoader) {

		this.context = new ConditionContextImpl(registry, environment, resourceLoader);
	}


	/**
	 * Determine if an item should be skipped based on {@code @Conditional} annotations.
	 * The {@link ConfigurationPhase} will be deduced from the type of item (i.e. a
	 * {@code @Configuration} class will be {@link ConfigurationPhase#PARSE_CONFIGURATION})
	 * @param metadata the meta data
	 * @return if the item should be skipped
	 */
	public boolean shouldSkip(AnnotatedTypeMetadata metadata) {
		return shouldSkip(metadata, null);
	}

	// 检测 Conditional 注解，检测当前是否应该被跳过
	/**
	 * Determine if an item should be skipped based on {@code @Conditional} annotations.
	 * @param metadata the meta data
	 * @param phase the phase of the call
	 * @return if the item should be skipped
	 */
	public boolean shouldSkip(@Nullable AnnotatedTypeMetadata metadata, @Nullable ConfigurationPhase phase) {
		if (metadata == null || !metadata.isAnnotated(Conditional.class.getName())) {
			// 没有元数据或者当前bd没有注解Conditional，则直接返回false，表示不应该跳过
			return false;
		}

		// 如果传进来的当前阶段是空的
		// 从 ConfigurationClassParser 调用过来的时候，这里传的是 PARSE_CONFIGURATION，解析配置阶段
		if (phase == null) {
			// 如果当前bd是一个注解解析来的，且是一个候选的配置，后续还需要继续处理
			if (metadata instanceof AnnotationMetadata &&
					ConfigurationClassUtils.isConfigurationCandidate((AnnotationMetadata) metadata)) {
				// 递归调用自己，当前阶段是解析配置阶段
				return shouldSkip(metadata, ConfigurationPhase.PARSE_CONFIGURATION);
			}
			// 如果当前bd不是一个注解解析来的，或者已经没有后续需要继续处理了，则递归调用自己，当前阶段是注册bean阶段
			return shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN);
		}

		// 创建一个list，存的是当前这个 Conditional 注解的 value 上，所有类的实例化对象
		List<Condition> conditions = new ArrayList<>();

		// 获取Condition的value，value 设置的是一个List集合，存的是下限是 Condition 的类
		for (String[] conditionClasses : getConditionClasses(metadata)) {
			// 遍历这些条件设置的类
			for (String conditionClass : conditionClasses) {
				// 1.获取到 conditionClass 对应的 类对象
				// 2.根据 Class 类对象，进行实例化，返回实例化后的对象
				Condition condition = getCondition(conditionClass, this.context.getClassLoader());
				// 将对应的实例化后的对象放到conditions中
				conditions.add(condition);
			}
		}

		// 排序
		AnnotationAwareOrderComparator.sort(conditions);

		// 遍历条件类
		// 所以这里说明，Conditional 注解中的所有类的条件都符合，才能匹配上，只要存在不匹配的，则跳过
		for (Condition condition : conditions) {
			ConfigurationPhase requiredPhase = null;
			if (condition instanceof ConfigurationCondition) {
				// 如果条件类属于 ConfigurationCondition 类型，则通过这个类拿到 requiredPhase，这个表示当前 condition 的阶段
				requiredPhase = ((ConfigurationCondition) condition).getConfigurationPhase();
			}
			// 1.如果 requiredPhase 是空的 或者 requiredPhase 和当前传进来的阶段是同一个
			// 2.调用 condition.matches 方法返回的结果取反（即没命中 condition.matches 方法）
			//		如果上边两点都为 true，则跳过，否则 不跳过
			if ((requiredPhase == null || requiredPhase == phase) && !condition.matches(this.context, metadata)) {
				return true;
			}
		}

		return false;
	}

	@SuppressWarnings("unchecked")
	private List<String[]> getConditionClasses(AnnotatedTypeMetadata metadata) {
		// 获取到注解 Conditional 上的所有属性值
		MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(Conditional.class.getName(), true);
		// 获取到 value 属性
		Object values = (attributes != null ? attributes.get("value") : null);
		// 请转成 List，返回，因为 value 是这样定义的：Class<? extends Condition>，是一个集合，存的是下限是 Condition 的类
		return (List<String[]>) (values != null ? values : Collections.emptyList());
	}

	private Condition getCondition(String conditionClassName, @Nullable ClassLoader classloader) {
		// 获取到对应的类对象
		Class<?> conditionClass = ClassUtils.resolveClassName(conditionClassName, classloader);
		// 根据类对象进行实例化，返回实例化后的对象
		return (Condition) BeanUtils.instantiateClass(conditionClass);
	}


	/**
	 * Implementation of a {@link ConditionContext}.
	 */
	private static class ConditionContextImpl implements ConditionContext {

		@Nullable
		private final BeanDefinitionRegistry registry;  // 对应的容器，AnnotatilnConfigApplicationContext

		@Nullable
		private final ConfigurableListableBeanFactory beanFactory;  // DefaultListableBeanFactory

		private final Environment environment;  // StandardEnvironment

		private final ResourceLoader resourceLoader;  // 容器，AnnotationConfigApplicationContext

		@Nullable
		private final ClassLoader classLoader;  // 初始化时当前线程的类加载器，调用的是 Thread.currentThread.getContextClassLoader()

		public ConditionContextImpl(@Nullable BeanDefinitionRegistry registry,
				@Nullable Environment environment, @Nullable ResourceLoader resourceLoader) {

			// bean工厂
			this.registry = registry;  // AnnotationConfigApplicationContext
			this.beanFactory = deduceBeanFactory(registry); // defaultListableBeanFactory
			this.environment = (environment != null ? environment : deduceEnvironment(registry));   // StandardEnvironment
			// DefaultResourceLoader
			this.resourceLoader = (resourceLoader != null ? resourceLoader : deduceResourceLoader(registry));  // AnnotationConfigApplicatinContext
			// AppClassLocader，获取到当前线程的类加载器
			this.classLoader = deduceClassLoader(resourceLoader, this.beanFactory);
		}

		@Nullable
		private ConfigurableListableBeanFactory deduceBeanFactory(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof ConfigurableListableBeanFactory) {
				return (ConfigurableListableBeanFactory) source;
			}
			if (source instanceof ConfigurableApplicationContext) {
				return (((ConfigurableApplicationContext) source).getBeanFactory());
			}
			return null;
		}

		private Environment deduceEnvironment(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof EnvironmentCapable) {
				return ((EnvironmentCapable) source).getEnvironment();
			}
			return new StandardEnvironment();
		}

		private ResourceLoader deduceResourceLoader(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof ResourceLoader) {
				return (ResourceLoader) source;
			}
			return new DefaultResourceLoader();
		}

		@Nullable
		private ClassLoader deduceClassLoader(@Nullable ResourceLoader resourceLoader,
				@Nullable ConfigurableListableBeanFactory beanFactory) {

			if (resourceLoader != null) {
				ClassLoader classLoader = resourceLoader.getClassLoader();
				if (classLoader != null) {
					return classLoader;
				}
			}
			if (beanFactory != null) {
				return beanFactory.getBeanClassLoader();
			}
			return ClassUtils.getDefaultClassLoader();
		}

		@Override
		public BeanDefinitionRegistry getRegistry() {
			Assert.state(this.registry != null, "No BeanDefinitionRegistry available");
			return this.registry;
		}

		@Override
		@Nullable
		public ConfigurableListableBeanFactory getBeanFactory() {
			return this.beanFactory;
		}

		@Override
		public Environment getEnvironment() {
			return this.environment;
		}

		@Override
		public ResourceLoader getResourceLoader() {
			return this.resourceLoader;
		}

		@Override
		@Nullable
		public ClassLoader getClassLoader() {
			return this.classLoader;
		}
	}

}
