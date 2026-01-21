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

package org.springframework.context.annotation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.context.annotation.DeferredImportSelector.Group;
import org.springframework.core.NestedIOException;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * Parses a {@link Configuration} class definition, populating a collection of
 * {@link ConfigurationClass} objects (parsing a single Configuration class may result in
 * any number of ConfigurationClass objects because one Configuration class may import
 * another using the {@link Import} annotation).
 *
 * <p>This class helps separate the concern of parsing the structure of a Configuration
 * class from the concern of registering BeanDefinition objects based on the content of
 * that model (with the exception of {@code @ComponentScan} annotations which need to be
 * registered immediately).
 *
 * <p>This ASM-based implementation avoids reflection and eager class loading in order to
 * interoperate effectively with lazy class loading in a Spring ApplicationContext.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Stephane Nicoll
 * @since 3.0
 * @see ConfigurationClassBeanDefinitionReader
 */
class ConfigurationClassParser {

	private static final PropertySourceFactory DEFAULT_PROPERTY_SOURCE_FACTORY = new DefaultPropertySourceFactory();

	// 默认的排除方式，这里是通过判断 className 全类名是用 DescriptiveResource 开头 或者 org.springframework.stereotype. 开头
	// 这两个路径开头的，基本是 注解
	private static final Predicate<String> DEFAULT_EXCLUSION_FILTER = className ->
			(className.startsWith("java.lang.annotation.") || className.startsWith("org.springframework.stereotype."));

	private static final Comparator<DeferredImportSelectorHolder> DEFERRED_IMPORT_COMPARATOR =
			(o1, o2) -> AnnotationAwareOrderComparator.INSTANCE.compare(o1.getImportSelector(), o2.getImportSelector());


	private final Log logger = LogFactory.getLog(getClass());

	// 构造函数设置进来了 CachingMetadataReaderFactory
	private final MetadataReaderFactory metadataReaderFactory;

	private final ProblemReporter problemReporter;

	private final Environment environment;

	private final ResourceLoader resourceLoader;

	private final BeanDefinitionRegistry registry;

	// 构造函数的时候，创建了 ComponentScanAnnotationParser
	private final ComponentScanAnnotationParser componentScanParser;

	private final ConditionEvaluator conditionEvaluator;

	// 存放解析出来的自己类信息
	private final Map<ConfigurationClass, ConfigurationClass> configurationClasses = new LinkedHashMap<>();
    // 父类指向子类的bd
	private final Map<String, ConfigurationClass> knownSuperclasses = new HashMap<>();

	private final List<String> propertySourceNames = new ArrayList<>();

	// 这是一个双端队列，用户保存过程中导入的类
	private final ImportStack importStack = new ImportStack();

	// 延迟导入假期器的处理器
	private final DeferredImportSelectorHandler deferredImportSelectorHandler = new DeferredImportSelectorHandler();

	private final SourceClass objectSourceClass = new SourceClass(Object.class);


	/**
	 * Create a new {@link ConfigurationClassParser} instance that will be used
	 * to populate the set of configuration classes.
	 */
	public ConfigurationClassParser(MetadataReaderFactory metadataReaderFactory,
			ProblemReporter problemReporter, Environment environment, ResourceLoader resourceLoader,
			BeanNameGenerator componentScanBeanNameGenerator, BeanDefinitionRegistry registry) {

		// CachingMetadataReaderFactory
		this.metadataReaderFactory = metadataReaderFactory;
		// 问题日志
		this.problemReporter = problemReporter;
		// 环境
		this.environment = environment;
		// DefaultResourceLoader
		this.resourceLoader = resourceLoader;
		// beanFactory 工厂
		this.registry = registry;
		// Component 注解扫描解析器，解析 Component 组件扫描
		this.componentScanParser = new ComponentScanAnnotationParser(
				environment, resourceLoader, componentScanBeanNameGenerator, registry);
		// condition 表达处理器
		this.conditionEvaluator = new ConditionEvaluator(registry, environment, resourceLoader);
	}


	/**
	 * 解析传进来的 bd 配置文件
	 * @param configCandidates
	 */
	public void parse(Set<BeanDefinitionHolder> configCandidates) {
		// 遍历所有传进来的 bdHolder
		for (BeanDefinitionHolder holder : configCandidates) {
			// 获取对应的 bd
			BeanDefinition bd = holder.getBeanDefinition();
			try {
				// 通过注解扫描进来的 bd
				if (bd instanceof AnnotatedBeanDefinition) {
					// 注册的解析方式
					parse(((AnnotatedBeanDefinition) bd).getMetadata(), holder.getBeanName());
				}
				else if (bd instanceof AbstractBeanDefinition && ((AbstractBeanDefinition) bd).hasBeanClass()) {
					// 非 注解方式扫描进来的，即普通的 bd，直接扫描进来的
					parse(((AbstractBeanDefinition) bd).getBeanClass(), holder.getBeanName());
				}
				else {
					// 其余的 bd 解析方式
					parse(bd.getBeanClassName(), holder.getBeanName());
				}
			}
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to parse configuration class [" + bd.getBeanClassName() + "]", ex);
			}
		}
        // 处理 deferredImportSelectorHandler 的数据
		// 在解析 Import 注解的时候，如果 Import 进来的配置类，是属于 DeferredImportSelector 类型，则会放到这里边
		// 等现在再执行
		this.deferredImportSelectorHandler.process();
	}

	// 兜底的 bd 解析  3
    // 从当前类开始，遍历到父类，都进行 配置的解析，Configuration
	// 以下3个方法是重载方法
	protected final void parse(@Nullable String className, String beanName) throws IOException {
		Assert.notNull(className, "No bean class name for configuration class bean definition");
		// 根据class文件名，转为 resource 对象，最后返回 SimpleMetadataReader
		// 这里的 metadataReaderFactory 是构造函数传进来的 CachingMetadataReaderFactory
		// 所以会通过 resource 先去缓存中获取，有则返回，没有则先生成，然后返回
		MetadataReader reader = this.metadataReaderFactory.getMetadataReader(className);
		// 解析处理对应的 bd，这里传进去的是一个包装了 元数据 和 beanName 的类
		processConfigurationClass(new ConfigurationClass(reader, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	// 普通 bd 解析  2
	protected final void parse(Class<?> clazz, String beanName) throws IOException {
		processConfigurationClass(new ConfigurationClass(clazz, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	// 注解 bd 解析  1
	// metadata - 元数据；beanName - bd 对应的名称
	protected final void parse(AnnotationMetadata metadata, String beanName) throws IOException {
		processConfigurationClass(new ConfigurationClass(metadata, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	/**
	 * Validate each {@link ConfigurationClass} object.
	 * @see ConfigurationClass#validate
	 */
	public void validate() {
		for (ConfigurationClass configClass : this.configurationClasses.keySet()) {
			configClass.validate(this.problemReporter);
		}
	}

	public Set<ConfigurationClass> getConfigurationClasses() {
		return this.configurationClasses.keySet();
	}


	/**
	 * 解析配置的 bd 类
	 * 这个 ConfigurationClass 是一个组合类，包装了要解析的 bd 的元数据信息和 beanName
	 * @param configClass
	 * @param filter
	 * @throws IOException
	 */
	protected void processConfigurationClass(ConfigurationClass configClass, Predicate<String> filter) throws IOException {
		// 当前阶段为配置解析阶段，判断是否需要跳过解析这个bd
		if (this.conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.PARSE_CONFIGURATION)) {
			// 跳过则直接返回
			return;
		}

		// 先从 configurationClasses 缓存中获取是否存在这个 configClass
		// 缓存，第一次的话是空的
		ConfigurationClass existingClass = this.configurationClasses.get(configClass);
		// existingClass 不为空
		if (existingClass != null) {
			// 当前这个配置类是否有 importedBy 数据
			if (configClass.isImported()) {
				// 如果之前解析的这个配置，已经有了 importedBy 数据
				if (existingClass.isImported()) {
					// configClass 已经解析过了，而且 configClass 和 之前解析的那个都是 import 的
					// 则进行合并，将 configClass 的 importedBy 加到 existingClass 的 importedBy
					existingClass.mergeImportedBy(configClass);
				}
				// Otherwise ignore new imported config class; existing non-imported class overrides it.
				// 否则，忽略新的导入配置类
				return;
			}
			else {
				// Explicit bean definition found, probably replacing an import.
				// Let's remove the old one and go with the new one.
				this.configurationClasses.remove(configClass);
				this.knownSuperclasses.values().removeIf(configClass::equals);
			}
		}

		// Recursively process the configuration class and its superclass hierarchy.
		// 递归调用配置类的过程和他的父类
		// 这里 asSourceClass(configClass, filter) 方法，其实就是返回一个 SourceClass，其中包含了这个 configClass 的元数据
		// 而如果 配置类是空的，获取命中 filter，则返回 SourceClass(Object.class)
		SourceClass sourceClass = asSourceClass(configClass, filter);
		do {
			// Component  PropertySource  ComponentScans  Import  ImportResource  Bean注解的方法
			// 返回 class 的父类，父类也需要被解析
			sourceClass = doProcessConfigurationClass(configClass, sourceClass, filter);
		}
		// 循环处理，只要父类不是空的，则继续处理父类
		while (sourceClass != null);

		// 放入缓存，key 和 value 都是自己，表示此时解析了的配置类
		this.configurationClasses.put(configClass, configClass);
	}

	// 通过元数据类的注解、方法和属性，解析和创建一个完整的 ConfigurationClass
	/**
	 * Apply processing and build a complete {@link ConfigurationClass} by reading the
	 * annotations, members and methods from the source class. This method can be called
	 * multiple times as relevant sources are discovered.
	 * @param configClass the configuration class being build
	 * @param sourceClass a source class
	 * @return the superclass, or {@code null} if none found or previously processed
	 */
	@Nullable
	protected final SourceClass doProcessConfigurationClass(
			ConfigurationClass configClass, SourceClass sourceClass, Predicate<String> filter)
			throws IOException {

		// 判断 当前解析的类是否有包含了 Component 注解，先解析内部类
		if (configClass.getMetadata().isAnnotated(Component.class.getName())) {
			// Recursively process any member (nested) classes first
			// 对当前类的内部类
			// 		1.解析出当前这个类的所有内部类
			//		2.判断所有内部类是否是配置类或者需要被解析的类≤
			//		3.对所有的内部类进行排序
			//		4.递归重新调用 processConfigurationClass，处理配置类的解析
			processMemberClasses(configClass, sourceClass, filter);
		}

		// Process any @PropertySource annotations
		// 处理所有 PropertySource 注解，这里就包含了  PropertySources 和 PropertySource
		for (AnnotationAttributes propertySource : AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), PropertySources.class,
				org.springframework.context.annotation.PropertySource.class)) {
			// environment 是一个 StandardEnvironment，所以这里为 true
			if (this.environment instanceof ConfigurableEnvironment) {
				// 处理 propertySource，把对应的配置信息，添加到 env 中，如果 env 中已经有了，则进行覆盖
				processPropertySource(propertySource);
			}
			else {
				logger.info("Ignoring @PropertySource annotation on [" + sourceClass.getMetadata().getClassName() +
						"]. Reason: Environment must implement ConfigurableEnvironment");
			}
		}

		// Process any @ComponentScan annotations
		// 处理所有的 ComponentScan 相关注解，这里包括 ComponentScans 和 ComponentScan
		Set<AnnotationAttributes> componentScans = AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), ComponentScans.class, ComponentScan.class);

		// 判断是否被空，或者是否被跳过
		if (!componentScans.isEmpty() &&
				!this.conditionEvaluator.shouldSkip(sourceClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN)) {
			for (AnnotationAttributes componentScan : componentScans) {
				// The config class is annotated with @ComponentScan -> perform the scan immediately
				// 扫描对应的路径，并将符合条件的类进行注册成 bd
				Set<BeanDefinitionHolder> scannedBeanDefinitions =
						this.componentScanParser.parse(componentScan, sourceClass.getMetadata().getClassName());
				// Check the set of scanned definitions for any further config classes and parse recursively if needed
				for (BeanDefinitionHolder holder : scannedBeanDefinitions) {
					BeanDefinition bdCand = holder.getBeanDefinition().getOriginatingBeanDefinition();
					if (bdCand == null) {
						bdCand = holder.getBeanDefinition();
					}
					// 检查是否是配置类的候选，是则进行解析，递归
					if (ConfigurationClassUtils.checkConfigurationClassCandidate(bdCand, this.metadataReaderFactory)) {
						parse(bdCand.getBeanClassName(), holder.getBeanName());
					}
				}
			}
		}

		// Process any @Import annotations
		// 处理 Import 注解，Springboot 自动装配的重点需要看这里
		// getImports(sourceClass) 得到当前 sourceClass 的所有 Import 注解的 value 值
		// 具体其实就是三个逻辑
		//		1.如果是 ImportSelector 的类型，则获取需要导入的类，Springboot 自动装配的原理
		//		2.如果是 ImportBeanDefinitionRegistrar 的类型，则实例化对象后添加到 importBeanDefinitionRegistrars
		//		3.如果以上两种都不是，则直接当做是一个普通的 Configuration 类进行处理
		processImports(configClass, sourceClass, getImports(sourceClass), filter, true);

		// Process any @ImportResource annotations
		// 解析所有的 ImportResource 注解，扫描到 ImportResource 指定的路径，添加到 importedResources
		AnnotationAttributes importResource =
				AnnotationConfigUtils.attributesFor(sourceClass.getMetadata(), ImportResource.class);
		if (importResource != null) {
			// 获取 ImportResource 注解的 locations 值
			String[] resources = importResource.getStringArray("locations");
			// 获取 ImportResource 注解的 reader 值，是一个指定了下限是 BeanDefinitionReader 的类
			// 默认不指定的话是 BeanDefinitionReader.class
			Class<? extends BeanDefinitionReader> readerClass = importResource.getClass("reader");
			// 遍历 locations
			for (String resource : resources) {
				// 进行表达式的替换，得到结果
				String resolvedResource = this.environment.resolveRequiredPlaceholders(resource);
				// 添加到 importedResources
				configClass.addImportedResource(resolvedResource, readerClass);
			}
		}

		// Process individual @Bean methods
		// 解析配置类中的 Bean 注解修饰的方法
		// 获取到所有的 Bean 方法，解析成 MethodMetadata，添加到 beanMethods 中
		Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(sourceClass);
		for (MethodMetadata methodMetadata : beanMethods) {
			// 将所有扫描到的 Bean 注解的方法都加到 beanMethods 中
			configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
		}

		// Process default methods on interfaces
		// 处理接口的类型，从 jdk1.8 开始，接口中允许定义 default 方法实现，这个方法同样有可能是 @Bean 修饰
		// 获取所有的 bean 方法，添加到 beanMethods 中
		processInterfaces(configClass, sourceClass);

		// Process superclass, if any
		// 处理父类
		// 判断如果这个配置有父类
		if (sourceClass.getMetadata().hasSuperClass()) {
			// 获取到父类的类名
			String superclass = sourceClass.getMetadata().getSuperClassName();
			// 如果有以下三种情况都符合的情况
			// 		父类不为空，
			// 		父类不是 java 自带的类（java 开头）
			//		父类没有处理过
			if (superclass != null && !superclass.startsWith("java") &&
					!this.knownSuperclasses.containsKey(superclass)) {
				// 将父类放到 knownSuperclasses 中缓存，避免下次重复处理了
				this.knownSuperclasses.put(superclass, configClass);
				// Superclass found, return its annotation metadata and recurse
				// 如果有父类，则跳出返回父类，外部会继续把父类传进来继续进行解析过程
				return sourceClass.getSuperClass();
			}
		}

		// No superclass -> processing is complete
		// 当父类也都处理完了，则处理流程就完成了
		return null;
	}

	// 解析 Component
	/**
	 * Register member (nested) classes that happen to be configuration classes themselves.
	 */
	private void processMemberClasses(ConfigurationClass configClass, SourceClass sourceClass,
			Predicate<String> filter) throws IOException {

		// 先根据类元数据，获得所有的内部类信息
		Collection<SourceClass> memberClasses = sourceClass.getMemberClasses();
		if (!memberClasses.isEmpty()) {
			// 先定义一个集合，用来缓存符合条件（检测可能是一个配置，需要被解析）的内部类
			List<SourceClass> candidates = new ArrayList<>(memberClasses.size());
			// 遍历
			for (SourceClass memberClass : memberClasses) {
				// 判断内部类是否是一个配置或者有组件注解，或者有 bean 方法
				if (ConfigurationClassUtils.isConfigurationCandidate(memberClass.getMetadata()) &&
						// 内部类不是当前类
						!memberClass.getMetadata().getClassName().equals(configClass.getMetadata().getClassName())) {
					// 将该内部类添加到 candidates 中
					candidates.add(memberClass);
				}
			}
			// 排序
			OrderComparator.sort(candidates);
			// 遍历扫描出来的所有内部类
			for (SourceClass candidate : candidates) {
				// 先判断是否 importStack 中已经存在，如果存在了，则说明重复导入了，添加异常日志
				if (this.importStack.contains(configClass)) {
					this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
				}
				else {
					// 先压栈
					this.importStack.push(configClass);
					try {
						// 处理这个配置类
						// 将 ConfigurationClass 封装成 ConfigurationClass，然后递归调用到外部进来的方法 processConfigurationClass
						// 可以理解为，就是循环遍历所有的内部类，然后继续走外边解析配置类的过程，递归调用
						processConfigurationClass(candidate.asConfigClass(configClass), filter);
					}
					finally {
						// 弹栈
						this.importStack.pop();
					}
				}
			}
		}
	}

	// 注册父接口中所有 default 方法且是 Bean 注解修饰的非抽象的 bean 方法，加到 beanMethods 中
	/**
	 * Register default methods on interfaces implemented by the configuration class.
	 */
	private void processInterfaces(ConfigurationClass configClass, SourceClass sourceClass) throws IOException {
		// 遍历配置类的所有实现的接口
		for (SourceClass ifc : sourceClass.getInterfaces()) {
			// 获取到这个接口的所有 Bean 方法，接口中 default 方法是可以写具体实现和家 Bean 注解的
			Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(ifc);
			for (MethodMetadata methodMetadata : beanMethods) {
				// 判断方法不是抽象方法
				if (!methodMetadata.isAbstract()) {
					// A default method or other concrete method on a Java 8+ interface...
					// 加到 beanMethods 中
					configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
				}
			}
			// 递归调用 processInterfaces，处理父接口的父接口
			processInterfaces(configClass, ifc);
		}
	}

	// 获取到当前这个类的所有 Bean 注解的方法
	/**
	 * Retrieve the metadata for all <code>@Bean</code> methods.
	 */
	private Set<MethodMetadata> retrieveBeanMethodMetadata(SourceClass sourceClass) {
		// 获取配置类的元数据信息
		AnnotationMetadata original = sourceClass.getMetadata();
		// 获取到配置类的所有有 Bean 注解的属性和方法，获取 bean 方法的，前边已经用asm扫描了
		Set<MethodMetadata> beanMethods = original.getAnnotatedMethods(Bean.class.getName());
		// 遍历处理 bean 方法，如果原日志的元数据属于 StandardAnnotationMetadata
		if (beanMethods.size() > 1 && original instanceof StandardAnnotationMetadata) {
			// Try reading the class file via ASM for deterministic declaration order...
			// Unfortunately, the JVM's standard reflection returns methods in arbitrary
			// order, even between different runs of the same application on the same JVM.
			try {
				AnnotationMetadata asm =
						this.metadataReaderFactory.getMetadataReader(original.getClassName()).getAnnotationMetadata();
				Set<MethodMetadata> asmMethods = asm.getAnnotatedMethods(Bean.class.getName());
				if (asmMethods.size() >= beanMethods.size()) {
					Set<MethodMetadata> selectedMethods = new LinkedHashSet<>(asmMethods.size());
					for (MethodMetadata asmMethod : asmMethods) {
						for (MethodMetadata beanMethod : beanMethods) {
							if (beanMethod.getMethodName().equals(asmMethod.getMethodName())) {
								selectedMethods.add(beanMethod);
								break;
							}
						}
					}
					if (selectedMethods.size() == beanMethods.size()) {
						// All reflection-detected methods found in ASM method set -> proceed
						beanMethods = selectedMethods;
					}
				}
			}
			catch (IOException ex) {
				logger.debug("Failed to read class file via ASM for determining @Bean method order", ex);
				// No worries, let's continue with the reflection metadata we started with...
			}
		}
		// 返回所有的 bean 方法
		return beanMethods;
	}


	/**
	 * Process the given <code>@PropertySource</code> annotation metadata.
	 * @param propertySource metadata for the <code>@PropertySource</code> annotation found
	 * @throws IOException if loading a property source failed
	 */
	private void processPropertySource(AnnotationAttributes propertySource) throws IOException {
		// 获取 name 属性
		String name = propertySource.getString("name");
		if (!StringUtils.hasLength(name)) {
			name = null;
		}
		// 获取 encoding 属性
		String encoding = propertySource.getString("encoding");
		if (!StringUtils.hasLength(encoding)) {
			encoding = null;
		}
		// 获取 value 属性
		String[] locations = propertySource.getStringArray("value");
		// value 属性不能空
		Assert.isTrue(locations.length > 0, "At least one @PropertySource(value) location is required");
		// 获取属性 ignoreResourceNotFound
		boolean ignoreResourceNotFound = propertySource.getBoolean("ignoreResourceNotFound");

		// 获取属性 factory
		Class<? extends PropertySourceFactory> factoryClass = propertySource.getClass("factory");
		// 如果factory 是 PropertySourceFactory，则 factory = DefaultPropertySourceFactory
		// 如果不是，则反射获取实例对象
		PropertySourceFactory factory = (factoryClass == PropertySourceFactory.class ?
				DEFAULT_PROPERTY_SOURCE_FACTORY : BeanUtils.instantiateClass(factoryClass));

		// 遍历所有的对应文件
		for (String location : locations) {
			try {
				// 先进行变量解析替换
				String resolvedLocation = this.environment.resolveRequiredPlaceholders(location);
				// 转换为 Resource 对象
				Resource resource = this.resourceLoader.getResource(resolvedLocation);
				// factory.createPropertySource 解析对应 resource，得到 ResourcePropertySource
				// ResourcePropertySource 中的属性 name 和 source 中的 source 则是解析得到的值
				addPropertySource(factory.createPropertySource(name, new EncodedResource(resource, encoding)));
			}
			catch (IllegalArgumentException | FileNotFoundException | UnknownHostException | SocketException ex) {
				// Placeholders not resolvable or resource not found when trying to open it
				if (ignoreResourceNotFound) {
					if (logger.isInfoEnabled()) {
						logger.info("Properties location [" + location + "] not resolvable: " + ex.getMessage());
					}
				}
				else {
					throw ex;
				}
			}
		}
	}

	private void addPropertySource(PropertySource<?> propertySource) {
		// 获取到 name
		String name = propertySource.getName();
		// 获取到当前环境
		MutablePropertySources propertySources = ((ConfigurableEnvironment) this.environment).getPropertySources();

		// 记录已经解析过的 PropertySource 类
		// 第一次进来 propertySourceNames 是空的，所以第一次不会进入这个分支
		// 如果能进来，说明之前已经有了，现在需要增加值
		if (this.propertySourceNames.contains(name)) {
			// We've already added a version, we need to extend it
			// 从环境中获取到对应的 propertySource 对象
			PropertySource<?> existing = propertySources.get(name);
			if (existing != null) {
				PropertySource<?> newSource = (propertySource instanceof ResourcePropertySource ?
						((ResourcePropertySource) propertySource).withResourceName() : propertySource);
				if (existing instanceof CompositePropertySource) {
					// 在原来的 propertySource 前再加上当前解析的 newSource
					((CompositePropertySource) existing).addFirstPropertySource(newSource);
				}
				else {
					if (existing instanceof ResourcePropertySource) {
						existing = ((ResourcePropertySource) existing).withResourceName();
					}
					// 定义一个合并的 PropertySource
					CompositePropertySource composite = new CompositePropertySource(name);
					// 将原来的和现在的合并到 PropertySource
					composite.addPropertySource(newSource);
					composite.addPropertySource(existing);
					// 从合并的，去替换之前的
					propertySources.replace(name, composite);
				}
				return;
			}
		}

		if (this.propertySourceNames.isEmpty()) {
			// 直接在当前环境添加上 propertySource
			propertySources.addLast(propertySource);
		}
		else {
			// 获取当前 propertySourceNames 的最后一个
			String firstProcessed = this.propertySourceNames.get(this.propertySourceNames.size() - 1);
			// 添加到环境中，插入到 propertySourceNames 里存在的最前边，这是为了后边的配置能排前边，实现了后边加载的配置覆盖前边加载的配置的现象
			propertySources.addBefore(firstProcessed, propertySource);
		}
		// 将这个 source 缓存到 propertySourceNames 中
		this.propertySourceNames.add(name);
	}


	// 获取 Import 注解的类，收集到 Set 中返回
	// 这里的实现中，会递归遍历 sourceClass 的所有注解和注解的父类，找到所有的 Import 注解，收集 value
	/**
	 * Returns {@code @Import} class, considering all meta-annotations.
	 */
	private Set<SourceClass> getImports(SourceClass sourceClass) throws IOException {
		Set<SourceClass> imports = new LinkedHashSet<>();
		Set<SourceClass> visited = new LinkedHashSet<>();
		collectImports(sourceClass, imports, visited);
		return imports;
	}

	// 递归收集所有 Import 注解的值，通过 Configuration 注解也会声明一个 Import 注解
	// imports 已经收集到的类
	// visited 已经解析过的类，避免无限循环
	// 这里执行完后的结果是，当前 sourceClass 的所有注解，或者注解下的注解，都会扫描 Import 注解，如果有的话，将对应的 value 属性收集到 imports
	/**
	 * Recursively collect all declared {@code @Import} values. Unlike most
	 * meta-annotations it is valid to have several {@code @Import}s declared with
	 * different values; the usual process of returning values from the first
	 * meta-annotation on a class is not sufficient.
	 * <p>For example, it is common for a {@code @Configuration} class to declare direct
	 * {@code @Import}s in addition to meta-imports originating from an {@code @Enable}
	 * annotation.
	 * @param sourceClass the class to search
	 * @param imports the imports collected so far
	 * @param visited used to track visited classes to prevent infinite recursion
	 * @throws IOException if there is any problem reading metadata from the named class
	 */
	private void collectImports(SourceClass sourceClass, Set<SourceClass> imports, Set<SourceClass> visited)
			throws IOException {

		// 将当前需要解析的 sourceClass 先添加到 visited
		// 如果成功了，则说明之前没有收集过，进入收集
		// 如果失败了，说明之前已经收集过了，跳过
		if (visited.add(sourceClass)) {
			// 获取当前这个类的所有注解，进行遍历
			for (SourceClass annotation : sourceClass.getAnnotations()) {
				// 获取注解的类名
				String annName = annotation.getMetadata().getClassName();
				// 找到不是 Import 的注解
				if (!annName.equals(Import.class.getName())) {
					// 递归调用自己，继续处理
					collectImports(annotation, imports, visited);
				}
			}
			// imports 中添加当前 sourceClass 对应注解 Import 的 value 值
			// 然后 imports 继续往下传递
			imports.addAll(sourceClass.getAnnotationAttributes(Import.class.getName(), "value"));
		}
	}

	// 处理 Import 注解的过程
	// 遍历 importCandidates，
	// 		如果是 DeferredImportSelector，添加到 deferredImportSelectorHandler
	// 		如果是 其他 ImportSelector 选择器，调用 selectImports 得到引入的其他配置，递归调用当前方法 processImports
	//		如果是 ImportBeanDefinitionRegistrar 注册器，添加到当前 configClass 的属性 importBeanDefinitionRegistrars 中
	//		剩余其他情况，直接当做 Configuration 进行处理
	private void processImports(ConfigurationClass configClass, SourceClass currentSourceClass,
			Collection<SourceClass> importCandidates, Predicate<String> exclusionFilter,
			boolean checkForCircularImports) {

		// 没有 import 的 value，不需要进行解析，直接返回
		if (importCandidates.isEmpty()) {
			return;
		}

		if (checkForCircularImports && isChainedImportOnStack(configClass)) {
			// 如果 isChainedImportOnStack 也返回 true，说明这个配置类已经是在处理中
			// 此时已经是循环解析了，添加日志报告
			this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
		}
		else {
			// 将当前配置添加到 importStack
			this.importStack.push(configClass);
			try {
				// 遍历所有扫描到的，需要处理的配置类
				for (SourceClass candidate : importCandidates) {
					// 判断是否是 ImportSelector 这个类的，springboot 自动装配就是
					if (candidate.isAssignable(ImportSelector.class)) {
						// Candidate class is an ImportSelector -> delegate to it to determine imports
						// 如果 import 的类的是一个 ImportSelector，则委派这个选择器进行确认导入处理
						Class<?> candidateClass = candidate.loadClass();
						// 实例化对象，如果是 Aware 接口，并调用了对应的 aware 方法
						ImportSelector selector = ParserStrategyUtils.instantiateClass(candidateClass, ImportSelector.class,
								this.environment, this.resourceLoader, this.registry);
						// 获取这个 ImportSelector 的排除过滤规则，默认实现是 null
						Predicate<String> selectorFilter = selector.getExclusionFilter();
						if (selectorFilter != null) {
							// 不为 null，则加到 exclusionFilter 这个排除规则中
							exclusionFilter = exclusionFilter.or(selectorFilter);
						}
						// 如果是默认的 DeferredImportSelector，延迟加载选择器
						if (selector instanceof DeferredImportSelector) {
							// 调用 DeferredImportSelectorHandler.handle 方法
							// 即如果 import 的类是 DeferredImportSelector 类型，则会放入到 deferredImportSelectorHandler 中，最后再处理的
							this.deferredImportSelectorHandler.handle(configClass, (DeferredImportSelector) selector);
						}
						else {
							// 如果不是 DeferredImportSelector
							// 通过 selector.selectImports 扫描获取到要加载进来的配置类的名称
							String[] importClassNames = selector.selectImports(currentSourceClass.getMetadata());
							// 先过滤掉 exclusionFilter 后，全都转成 SourceClass，收集到 importSourceClasses 中，
							Collection<SourceClass> importSourceClasses = asSourceClasses(importClassNames, exclusionFilter);
							// 递归调用自己，继续处理 select 的配置类
							processImports(configClass, currentSourceClass, importSourceClasses, exclusionFilter, false);
						}
					}
					// 如果是属于 ImportBeanDefinitionRegistrar 类型
					else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
						// Candidate class is an ImportBeanDefinitionRegistrar ->
						// delegate to it to register additional bean definitions
						// 如果得到的 import 进来的类是一个 ImportBeanDefinitionRegistrar，其实就是当做是一个注册器
						// 委派注册器去注册需要增加的 bd
						Class<?> candidateClass = candidate.loadClass();
						// 实例化对象
						ImportBeanDefinitionRegistrar registrar =
								ParserStrategyUtils.instantiateClass(candidateClass, ImportBeanDefinitionRegistrar.class,
										this.environment, this.resourceLoader, this.registry);
						// 和上边不同的地方
						configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata());
					}
					else {
						// Candidate class not an ImportSelector or ImportBeanDefinitionRegistrar ->
						// process it as an @Configuration class
						// 其他情况，这个 import 的类既不是 ImportSelector 又不是 ImportBeanDefinitionRegistrar
						// 将这个类当做一个 Configuration 注解的类进行处理
						this.importStack.registerImport(
								currentSourceClass.getMetadata(), candidate.getMetadata().getClassName());
						// 就是这里，调用 processConfigurationClass 递归进行处理这个 import 进来的对象
						// 这时候，asConfigClass 方法会将这个 configClass 添加到 importedBy 中
						processConfigurationClass(candidate.asConfigClass(configClass), exclusionFilter);
					}
				}
			}
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to process import candidates for configuration class [" +
						configClass.getMetadata().getClassName() + "]", ex);
			}
			finally {
				// 弹出
				this.importStack.pop();
			}
		}
	}

	// 判断是否当前要处理的配置类已经在 importStack 中了
	private boolean isChainedImportOnStack(ConfigurationClass configClass) {
		if (this.importStack.contains(configClass)) {
			String configClassName = configClass.getMetadata().getClassName();
			AnnotationMetadata importingClass = this.importStack.getImportingClassFor(configClassName);
			while (importingClass != null) {
				if (configClassName.equals(importingClass.getClassName())) {
					return true;
				}
				importingClass = this.importStack.getImportingClassFor(importingClass.getClassName());
			}
		}
		return false;
	}

	ImportRegistry getImportRegistry() {
		return this.importStack;
	}


	// 将 Configuration 配置转为 ConfigurationClassParser.SourceClas 类型
	/**
	 * Factory method to obtain a {@link SourceClass} from a {@link ConfigurationClass}.
	 */
	private SourceClass asSourceClass(ConfigurationClass configurationClass, Predicate<String> filter) throws IOException {
		// 获取对应要处理的配置的元数据
		AnnotationMetadata metadata = configurationClass.getMetadata();
		if (metadata instanceof StandardAnnotationMetadata) {
			// StandardAnnotationMetadata 的话走这个分支
			// getIntrospectedClass() 得到类对象， 最后返回 new SourceClass(classType)，然后调用了重载的方法
			return asSourceClass(((StandardAnnotationMetadata) metadata).getIntrospectedClass(), filter);
		}
		// SimpleAnnotationMetadata 的话走这个分支，传入了当前要解析的配置类的类名，调用重载方法
		return asSourceClass(metadata.getClassName(), filter);
	}

	// 从一个 Class 获取一个 ConfigurationClassParser.SourceClass 的工厂方法
	/**
	 * Factory method to obtain a {@link SourceClass} from a {@link Class}.
	 */
	SourceClass asSourceClass(@Nullable Class<?> classType, Predicate<String> filter) throws IOException {
		if (classType == null || filter.test(classType.getName())) {
			return this.objectSourceClass;
		}
		try {
			// Sanity test that we can reflectively read annotations,
			// including Class attributes; if not -> fall back to ASM
			// 遍历所有注解
			for (Annotation ann : classType.getDeclaredAnnotations()) {
				// 注解验证
				AnnotationUtils.validateAnnotation(ann);
			}
			// 返回 SourceClass
			return new SourceClass(classType);
		}
		catch (Throwable ex) {
			// Enforce ASM via class name resolution
			return asSourceClass(classType.getName(), filter);
		}
	}

	/**
	 * Factory method to obtain a {@link SourceClass} collection from class names.
	 */
	private Collection<SourceClass> asSourceClasses(String[] classNames, Predicate<String> filter) throws IOException {
		List<SourceClass> annotatedClasses = new ArrayList<>(classNames.length);
		for (String className : classNames) {
			annotatedClasses.add(asSourceClass(className, filter));
		}
		return annotatedClasses;
	}

	/**
	 * Factory method to obtain a {@link SourceClass} from a class name.
	 */
	SourceClass asSourceClass(@Nullable String className, Predicate<String> filter) throws IOException {
		if (className == null || filter.test(className)) {
			// 如果 className 是空的，或者符合 filter，则直接返回 SourceClass(Object.class)
			// 说明不需要再继续处理
			return this.objectSourceClass;
		}
		// 判断是否 java 自带的类
		if (className.startsWith("java")) {
			// Never use ASM for core java types
			try {
				// 调用 ClassUtils 反射得到类对象，返回对应 SourceClass，包装了 类对象
				// 返回的标准反射的元数据 StandardAnnotationMetadata
				return new SourceClass(ClassUtils.forName(className, this.resourceLoader.getClassLoader()));
			}
			catch (ClassNotFoundException ex) {
				throw new NestedIOException("Failed to load class [" + className + "]", ex);
			}
		}
		// 这个返回的是 this.metadataReaderFactory.getMetadataReader(className)
		// 先去 CachingMetadataReaderFactory 缓存找，有则返回，没有则创建 SimpleMetadataReader，放入缓存并返回
		return new SourceClass(this.metadataReaderFactory.getMetadataReader(className));
	}


	@SuppressWarnings("serial")
	private static class ImportStack extends ArrayDeque<ConfigurationClass> implements ImportRegistry {

		private final MultiValueMap<String, AnnotationMetadata> imports = new LinkedMultiValueMap<>();

		public void registerImport(AnnotationMetadata importingClass, String importedClass) {
			this.imports.add(importedClass, importingClass);
		}

		@Override
		@Nullable
		public AnnotationMetadata getImportingClassFor(String importedClass) {
			return CollectionUtils.lastElement(this.imports.get(importedClass));
		}

		@Override
		public void removeImportingClass(String importingClass) {
			for (List<AnnotationMetadata> list : this.imports.values()) {
				for (Iterator<AnnotationMetadata> iterator = list.iterator(); iterator.hasNext();) {
					if (iterator.next().getClassName().equals(importingClass)) {
						iterator.remove();
						break;
					}
				}
			}
		}

		/**
		 * Given a stack containing (in order)
		 * <ul>
		 * <li>com.acme.Foo</li>
		 * <li>com.acme.Bar</li>
		 * <li>com.acme.Baz</li>
		 * </ul>
		 * return "[Foo->Bar->Baz]".
		 */
		@Override
		public String toString() {
			StringJoiner joiner = new StringJoiner("->", "[", "]");
			for (ConfigurationClass configurationClass : this) {
				joiner.add(configurationClass.getSimpleName());
			}
			return joiner.toString();
		}
	}


	private class DeferredImportSelectorHandler {

		// 在调用 handle 方法的时候，添加 DeferredImportSelectorHolder
		// 这里添加的 DeferredImportSelectorHolder，configClass 是当前的配置类，importSelector 是 DeferredImportSelector
		@Nullable
		private List<DeferredImportSelectorHolder> deferredImportSelectors = new ArrayList<>();

		/**
		 * Handle the specified {@link DeferredImportSelector}. If deferred import
		 * selectors are being collected, this registers this instance to the list. If
		 * they are being processed, the {@link DeferredImportSelector} is also processed
		 * immediately according to its {@link DeferredImportSelector.Group}.
		 * @param configClass the source configuration class
		 * @param importSelector the selector to handle
		 */
		public void handle(ConfigurationClass configClass, DeferredImportSelector importSelector) {
			// 包装类
			DeferredImportSelectorHolder holder = new DeferredImportSelectorHolder(configClass, importSelector);
			if (this.deferredImportSelectors == null) {
				DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();
				handler.register(holder);
				handler.processGroupImports();
			}
			else {
				// 将 holder 添加到 deferredImportSelectors 中
				this.deferredImportSelectors.add(holder);
			}
		}

		public void process() {

			// 这里是通过 @Import 注解添加进来的，属于 DeferredImportSelector 类的配置
			List<DeferredImportSelectorHolder> deferredImports = this.deferredImportSelectors;
			// 处理一次清空一次
			this.deferredImportSelectors = null;
			try {
				// 有数据
				if (deferredImports != null) {
					// 构建一个处理器 handler
					DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();
					// 对 deferredImportSelectors 的配置进行排序
					deferredImports.sort(DEFERRED_IMPORT_COMPARATOR);
					// 遍历 deferredImportSelectors 的配置，调用 register 处理
					// 其实就是进行一些前置工作，实例化 Group，创建 DeferredImportSelectorGrouping，放到 configurationClasses 中 和 deferredImports 中
					deferredImports.forEach(handler::register);
					// 收集后，进行处理整个组
					handler.processGroupImports();
				}
			}
			finally {
				// 最后将 deferredImportSelectors 进行清空，表示已经处理过了
				this.deferredImportSelectors = new ArrayList<>();
			}
		}
	}


	private class DeferredImportSelectorGroupingHandler {

		// 也是延迟处理的 DeferredImportSelectorGrouping
		private final Map<Object, DeferredImportSelectorGrouping> groupings = new LinkedHashMap<>();

		// 对已经处理的 selector 进行处理，也就是放到这里
		private final Map<AnnotationMetadata, ConfigurationClass> configurationClasses = new HashMap<>();

		// 对延迟处理的 selector 进行注册和处理
		public void register(DeferredImportSelectorHolder deferredImport) {
			// 获得对应的 group 类，如果没有默认则返回 null
			Class<? extends Group> group = deferredImport.getImportSelector().getImportGroup();
			// 存放 groupings 数据，放入 group 和 DeferredImportSelectorGrouping，创建一个新的 DeferredImportSelectorGrouping 给进去 groupings
			// 所以这里放入集合，不涉及真的处理过程
			DeferredImportSelectorGrouping grouping = this.groupings.computeIfAbsent(
					(group != null ? group : deferredImport),
					// createGroup(group) 实例化一个 Group 类型的对象
					// 如果 group 是空的，则创建 DefaultDeferredImportSelectorGroup
					key -> new DeferredImportSelectorGrouping(createGroup(group)));
			// 这里个的 grouping 就是一个 DeferredImportSelectorGrouping
			grouping.add(deferredImport);
			// 添加到 configurationClasses 中，后边在处理的时候才能拿到
			this.configurationClasses.put(deferredImport.getConfigurationClass().getMetadata(),
					deferredImport.getConfigurationClass());
		}

		// 处理上边方法缓存起来的延迟处理的 selector
		public void processGroupImports() {
			// 遍历 groupings
			for (DeferredImportSelectorGrouping grouping : this.groupings.values()) {
				// 获取 grouping 里所有 selector 需要排除的过滤条件
				Predicate<String> exclusionFilter = grouping.getCandidateFilter();
				// 开始遍历所有的 imports
				// Springboot 自动装配，这里的 grouping.getImports() 会通过 Spring.factories 获取到所有的 EnableAutoConfiguration 对应的配置类进行过滤后处理
				grouping.getImports().forEach(entry -> {
					// 从 configurationClasses 缓存中获取对应的配置
					ConfigurationClass configurationClass = this.configurationClasses.get(entry.getMetadata());
					try {
						// 调用 processImports 处理 import 过程，将排除过滤的条件也放进去
						processImports(configurationClass, asSourceClass(configurationClass, exclusionFilter),
								Collections.singleton(asSourceClass(entry.getImportClassName(), exclusionFilter)),
								exclusionFilter, false);
					}
					catch (BeanDefinitionStoreException ex) {
						throw ex;
					}
					catch (Throwable ex) {
						throw new BeanDefinitionStoreException(
								"Failed to process import candidates for configuration class [" +
										configurationClass.getMetadata().getClassName() + "]", ex);
					}
				});
			}
		}

		// 反射创建一个 Group 对象
		private Group createGroup(@Nullable Class<? extends Group> type) {
			Class<? extends Group> effectiveType = (type != null ? type : DefaultDeferredImportSelectorGroup.class);
			// 实例化
			return ParserStrategyUtils.instantiateClass(effectiveType, Group.class,
					ConfigurationClassParser.this.environment,
					ConfigurationClassParser.this.resourceLoader,
					ConfigurationClassParser.this.registry);
		}
	}


	private static class DeferredImportSelectorHolder {

		private final ConfigurationClass configurationClass;

		private final DeferredImportSelector importSelector;

		// 构造函数，设置两个属性 configurationClass 和 importSelector
		public DeferredImportSelectorHolder(ConfigurationClass configClass, DeferredImportSelector selector) {
			this.configurationClass = configClass;
			this.importSelector = selector;
		}

		public ConfigurationClass getConfigurationClass() {
			return this.configurationClass;
		}

		public DeferredImportSelector getImportSelector() {
			return this.importSelector;
		}
	}


	private static class DeferredImportSelectorGrouping {

		// 构造函数传进来的 group 对象
		private final DeferredImportSelector.Group group;

		// 延迟处理的 selector
		private final List<DeferredImportSelectorHolder> deferredImports = new ArrayList<>();

		DeferredImportSelectorGrouping(Group group) {
			this.group = group;
		}

		public void add(DeferredImportSelectorHolder deferredImport) {
			this.deferredImports.add(deferredImport);
		}

		/**
		 * Return the imports defined by the group.
		 * @return each import with its associated configuration class
		 */
		public Iterable<Group.Entry> getImports() {
			// 遍历 deferredImports 对象
			for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
				//处理
				this.group.process(deferredImport.getConfigurationClass().getMetadata(),
						deferredImport.getImportSelector());
			}
			return this.group.selectImports();
		}

		// 合并需要排除的条件
		public Predicate<String> getCandidateFilter() {
			// 过滤条件
			Predicate<String> mergedFilter = DEFAULT_EXCLUSION_FILTER;
			// 遍历 deferredImports 中的所有 selector
			for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
				// 获取 selector 自己设置的排除条件
				Predicate<String> selectorFilter = deferredImport.getImportSelector().getExclusionFilter();
				if (selectorFilter != null) {
					// 如果 selector 自己也有排除的条件，则直接和上边的统一条件合并
					mergedFilter = mergedFilter.or(selectorFilter);
				}
			}
			// 返回总的排除条件
			return mergedFilter;
		}
	}


	private static class DefaultDeferredImportSelectorGroup implements Group {

		private final List<Entry> imports = new ArrayList<>();

		@Override
		public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {
			for (String importClassName : selector.selectImports(metadata)) {
				this.imports.add(new Entry(metadata, importClassName));
			}
		}

		@Override
		public Iterable<Entry> selectImports() {
			return this.imports;
		}
	}


	/**
	 * Simple wrapper that allows annotated source classes to be dealt with
	 * in a uniform manner, regardless of how they are loaded.
	 */
	private class SourceClass implements Ordered {

		private final Object source;  // Class or MetadataReader

		// 元数据
		private final AnnotationMetadata metadata;

		public SourceClass(Object source) {
			this.source = source;
			if (source instanceof Class) {
				// 如果传进来的是 Class 类对象，则 metadata = StandardAnnotationMetadata
				this.metadata = AnnotationMetadata.introspect((Class<?>) source);
			}
			else {
				// 其他情况，传进来的不是类对象
				this.metadata = ((MetadataReader) source).getAnnotationMetadata();
			}
		}

		public final AnnotationMetadata getMetadata() {
			return this.metadata;
		}

		@Override
		public int getOrder() {
			Integer order = ConfigurationClassUtils.getOrder(this.metadata);
			return (order != null ? order : Ordered.LOWEST_PRECEDENCE);
		}

		public Class<?> loadClass() throws ClassNotFoundException {
			if (this.source instanceof Class) {
				return (Class<?>) this.source;
			}
			String className = ((MetadataReader) this.source).getClassMetadata().getClassName();
			return ClassUtils.forName(className, resourceLoader.getClassLoader());
		}

		public boolean isAssignable(Class<?> clazz) throws IOException {
			if (this.source instanceof Class) {
				return clazz.isAssignableFrom((Class<?>) this.source);
			}
			return new AssignableTypeFilter(clazz).match((MetadataReader) this.source, metadataReaderFactory);
		}

		// 创建 ConfigurationClass，并将 importedBy 保存在这个对象中
		public ConfigurationClass asConfigClass(ConfigurationClass importedBy) {
			if (this.source instanceof Class) {
				return new ConfigurationClass((Class<?>) this.source, importedBy);
			}
			return new ConfigurationClass((MetadataReader) this.source, importedBy);
		}

		// 获取内部类
		public Collection<SourceClass> getMemberClasses() throws IOException {
			// 获得元数据
			Object sourceToProcess = this.source;
			if (sourceToProcess instanceof Class) {
				// 如果元数据是Class 类型，强转
				Class<?> sourceClass = (Class<?>) sourceToProcess;
				try {
					// 获取所有内部类
					Class<?>[] declaredClasses = sourceClass.getDeclaredClasses();
					List<SourceClass> members = new ArrayList<>(declaredClasses.length);
					for (Class<?> declaredClass : declaredClasses) {
						// 将内部类转成 SourceClass，添加到 members 中
						members.add(asSourceClass(declaredClass, DEFAULT_EXCLUSION_FILTER));
					}
					// 直接返回
					return members;
				}
				catch (NoClassDefFoundError err) {
					// getDeclaredClasses() failed because of non-resolvable dependencies
					// -> fall back to ASM below
					sourceToProcess = metadataReaderFactory.getMetadataReader(sourceClass.getName());
				}
			}

			// ASM-based resolution - safe for non-resolvable classes as well
			MetadataReader sourceReader = (MetadataReader) sourceToProcess;
			String[] memberClassNames = sourceReader.getClassMetadata().getMemberClassNames();
			List<SourceClass> members = new ArrayList<>(memberClassNames.length);
			for (String memberClassName : memberClassNames) {
				try {
					members.add(asSourceClass(memberClassName, DEFAULT_EXCLUSION_FILTER));
				}
				catch (IOException ex) {
					// Let's skip it if it's not resolvable - we're just looking for candidates
					if (logger.isDebugEnabled()) {
						logger.debug("Failed to resolve member class [" + memberClassName +
								"] - not considering it as a configuration class candidate");
					}
				}
			}
			return members;
		}

		public SourceClass getSuperClass() throws IOException {
			if (this.source instanceof Class) {
				return asSourceClass(((Class<?>) this.source).getSuperclass(), DEFAULT_EXCLUSION_FILTER);
			}
			return asSourceClass(
					((MetadataReader) this.source).getClassMetadata().getSuperClassName(), DEFAULT_EXCLUSION_FILTER);
		}

		public Set<SourceClass> getInterfaces() throws IOException {
			Set<SourceClass> result = new LinkedHashSet<>();
			if (this.source instanceof Class) {
				Class<?> sourceClass = (Class<?>) this.source;
				for (Class<?> ifcClass : sourceClass.getInterfaces()) {
					result.add(asSourceClass(ifcClass, DEFAULT_EXCLUSION_FILTER));
				}
			}
			else {
				for (String className : this.metadata.getInterfaceNames()) {
					result.add(asSourceClass(className, DEFAULT_EXCLUSION_FILTER));
				}
			}
			return result;
		}

		public Set<SourceClass> getAnnotations() {
			Set<SourceClass> result = new LinkedHashSet<>();
			// 如果当前的 source 属于 Class 类型
			if (this.source instanceof Class) {
				Class<?> sourceClass = (Class<?>) this.source;
				// 获取类的所有注解，进行遍历
				for (Annotation ann : sourceClass.getDeclaredAnnotations()) {
					Class<?> annType = ann.annotationType();
					// 如果注解不是 java 开头的，即这个注解不是 Java 自带的
					if (!annType.getName().startsWith("java")) {
						try {
							// 添加
							result.add(asSourceClass(annType, DEFAULT_EXCLUSION_FILTER));
						}
						catch (Throwable ex) {
							// An annotation not present on the classpath is being ignored
							// by the JVM's class loading -> ignore here as well.
						}
					}
				}
			}
			// 如果是属于 metadata 类型
			else {
				// 也是获取到所有的注解
				for (String className : this.metadata.getAnnotationTypes()) {
					if (!className.startsWith("java")) {
						try {
							// 添加
							result.add(getRelated(className));
						}
						catch (Throwable ex) {
							// An annotation not present on the classpath is being ignored
							// by the JVM's class loading -> ignore here as well.
						}
					}
				}
			}
			return result;
		}

		public Collection<SourceClass> getAnnotationAttributes(String annType, String attribute) throws IOException {
			Map<String, Object> annotationAttributes = this.metadata.getAnnotationAttributes(annType, true);
			if (annotationAttributes == null || !annotationAttributes.containsKey(attribute)) {
				return Collections.emptySet();
			}
			String[] classNames = (String[]) annotationAttributes.get(attribute);
			Set<SourceClass> result = new LinkedHashSet<>();
			for (String className : classNames) {
				result.add(getRelated(className));
			}
			return result;
		}

		private SourceClass getRelated(String className) throws IOException {
			if (this.source instanceof Class) {
				try {
					Class<?> clazz = ClassUtils.forName(className, ((Class<?>) this.source).getClassLoader());
					return asSourceClass(clazz, DEFAULT_EXCLUSION_FILTER);
				}
				catch (ClassNotFoundException ex) {
					// Ignore -> fall back to ASM next, except for core java types.
					if (className.startsWith("java")) {
						throw new NestedIOException("Failed to load class [" + className + "]", ex);
					}
					return new SourceClass(metadataReaderFactory.getMetadataReader(className));
				}
			}
			return asSourceClass(className, DEFAULT_EXCLUSION_FILTER);
		}

		@Override
		public boolean equals(@Nullable Object other) {
			return (this == other || (other instanceof SourceClass &&
					this.metadata.getClassName().equals(((SourceClass) other).metadata.getClassName())));
		}

		@Override
		public int hashCode() {
			return this.metadata.getClassName().hashCode();
		}

		@Override
		public String toString() {
			return this.metadata.getClassName();
		}
	}


	/**
	 * {@link Problem} registered upon detection of a circular {@link Import}.
	 */
	private static class CircularImportProblem extends Problem {

		public CircularImportProblem(ConfigurationClass attemptedImport, Deque<ConfigurationClass> importStack) {
			super(String.format("A circular @Import has been detected: " +
					"Illegal attempt by @Configuration class '%s' to import class '%s' as '%s' is " +
					"already present in the current import stack %s", importStack.element().getSimpleName(),
					attemptedImport.getSimpleName(), attemptedImport.getSimpleName(), importStack),
					new Location(importStack.element().getResource(), attemptedImport.getMetadata()));
		}
	}

}
