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

import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.regex.Pattern;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.beans.factory.xml.XmlReaderContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AspectJTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Parser for the {@code <context:component-scan/>} element.
 * <p>解析 {@code <context:component-scan/>} 标签
 * @author Mark Fisher
 * @author Ramnivas Laddad
 * @author Juergen Hoeller
 * @since 2.5
 */
public class ComponentScanBeanDefinitionParser implements BeanDefinitionParser {

	private static final String BASE_PACKAGE_ATTRIBUTE = "base-package";

	private static final String RESOURCE_PATTERN_ATTRIBUTE = "resource-pattern";

	private static final String USE_DEFAULT_FILTERS_ATTRIBUTE = "use-default-filters";

	private static final String ANNOTATION_CONFIG_ATTRIBUTE = "annotation-config";

	private static final String NAME_GENERATOR_ATTRIBUTE = "name-generator";

	private static final String SCOPE_RESOLVER_ATTRIBUTE = "scope-resolver";

	private static final String SCOPED_PROXY_ATTRIBUTE = "scoped-proxy";

	private static final String EXCLUDE_FILTER_ELEMENT = "exclude-filter";

	private static final String INCLUDE_FILTER_ELEMENT = "include-filter";

	private static final String FILTER_TYPE_ATTRIBUTE = "type";

	private static final String FILTER_EXPRESSION_ATTRIBUTE = "expression";


	// 1.获取扫描路径
	// 2.根据扫描路径，获取所有的class文件资源
	// 3.读取所有的class文件资源，判断是否是符合扫描条件（默认 Component 注解），
	// 		符合条件的创建 ScannedGenericBeanDefinition，并设置对应的属性，如作用域
	// 4.将对应的 ScannedGenericBeanDefinition 注册到 beanFactory 中
	@Override
	@Nullable
	public BeanDefinition parse(Element element, ParserContext parserContext) {
		// element-解析的元素    parserContext-ParserContext对象，已经设置了属性 readerContext 和 delegate
		// 解析到基础要扫描的路径，这里获取 base-package 的属性值
		String basePackage = element.getAttribute(BASE_PACKAGE_ATTRIBUTE);
		// 对路径进行解析，比如用了一些表达式的，这里进行处理，比如 ${} 那些，通过 env 进行替换
		basePackage = parserContext.getReaderContext().getEnvironment().resolvePlaceholders(basePackage);
		// 对 basePackages 进行分割，支持 , ; \n \t 这四种字符分割
		String[] basePackages = StringUtils.tokenizeToStringArray(basePackage,
				ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS);

		// Actually scan for bean definitions and register them.
		// 真正进行扫描和注册bd
		// 创建一个classPath 的扫描器，并进行配置，设置属性
		// 这里会先根据 element（即 component-scan） 标签做了一部分设置，如
		//		use-default-filters、resource-pattern
		//		name-generator、scope-resolver
		//		子标签  context:exclude-filter 和 context:include-filter
		// 解析后都设置到 scanner 中
		ClassPathBeanDefinitionScanner scanner = configureScanner(parserContext, element);
		// 进行扫描，重点，basePackages 为需要扫描的包集合，通过标签设置进来的
		// 这个方法会扫描所有符合条件（Component注解）的类，创建成对应的 bd，并注册到 beanFactory 中
		Set<BeanDefinitionHolder> beanDefinitions = scanner.doScan(basePackages);
		// 注册组件，这里会注册进去
		// 		ConfigurationClassPostProcessor（重点）、
		// 		AutowiredAnnotationBeanPostProcessor、
		// 		EventListenerMethodProcessor、
		// 		DefaultEventListenerFactory
		registerComponents(parserContext.getReaderContext(), beanDefinitions, element);

		return null;
	}

	protected ClassPathBeanDefinitionScanner configureScanner(ParserContext parserContext, Element element) {
		// 解析 use-default-filters 属性，默认是 true，true 则采用默认的过滤器，即 Component 注解
		boolean useDefaultFilters = true;
		if (element.hasAttribute(USE_DEFAULT_FILTERS_ATTRIBUTE)) {
			useDefaultFilters = Boolean.parseBoolean(element.getAttribute(USE_DEFAULT_FILTERS_ATTRIBUTE));
		}

		// Delegate bean definition registration to scanner class.
		// 创建一个 ClassPathBeanDefinitionScanner
		ClassPathBeanDefinitionScanner scanner = createScanner(parserContext.getReaderContext(), useDefaultFilters);

		// 默认的 beanDefinitionDefaults，创建 delegate 的时候已经设置进去默认值
		scanner.setBeanDefinitionDefaults(parserContext.getDelegate().getBeanDefinitionDefaults());
		// 空
		scanner.setAutowireCandidatePatterns(parserContext.getDelegate().getAutowireCandidatePatterns());

		// 如果设置了属性 resource-pattern 则进行设置 scanner
		if (element.hasAttribute(RESOURCE_PATTERN_ATTRIBUTE)) {
			scanner.setResourcePattern(element.getAttribute(RESOURCE_PATTERN_ATTRIBUTE));
		}

		try {
			// 解析 name-generator，名称生成器，默认不配置则空
			parseBeanNameGenerator(element, scanner);
		}
		catch (Exception ex) {
			parserContext.getReaderContext().error(ex.getMessage(), parserContext.extractSource(element), ex.getCause());
		}

		try {
			// 解析 scope-resolver
			parseScope(element, scanner);
		}
		catch (Exception ex) {
			parserContext.getReaderContext().error(ex.getMessage(), parserContext.extractSource(element), ex.getCause());
		}

		// 处理子标签 context:exclude-filter 和 context:include-filter
		parseTypeFilters(element, scanner, parserContext);

		return scanner;
	}

	protected ClassPathBeanDefinitionScanner createScanner(XmlReaderContext readerContext, boolean useDefaultFilters) {
		return new ClassPathBeanDefinitionScanner(readerContext.getRegistry(), useDefaultFilters,
				readerContext.getEnvironment(), readerContext.getResourceLoader());
	}

	protected void registerComponents(
			XmlReaderContext readerContext, Set<BeanDefinitionHolder> beanDefinitions, Element element) {

		// null，没啥东西
		Object source = readerContext.extractSource(element);
		// 创建 CompositeComponentDefinition，就是对 element.getTagName 和 source 的一个包装
		// element.getTagName ==> context:component-scan
		CompositeComponentDefinition compositeDef = new CompositeComponentDefinition(element.getTagName(), source);

		// 遍历扫描到的 Component 注解对应的 beanDefinitionHolder 对象
		for (BeanDefinitionHolder beanDefHolder : beanDefinitions) {
			// BeanComponentDefinition 类继承了 BeanDefinitionHolder
			// new BeanComponentDefinition(beanDefHolder) 是对于 BeanDefinitionHolder 的一个包装加强
			compositeDef.addNestedComponent(new BeanComponentDefinition(beanDefHolder));
		}

		// Register annotation config processors, if necessary.
		boolean annotationConfig = true;

		// 是否含有 annotation-config 属性，默认 component-scan 这个标签含有这个属性，默认值是 true
		if (element.hasAttribute(ANNOTATION_CONFIG_ATTRIBUTE)) {
			// 默认是 true
			annotationConfig = Boolean.parseBoolean(element.getAttribute(ANNOTATION_CONFIG_ATTRIBUTE));
		}
		if (annotationConfig) {
			// 注册注解扫描需要的相关的 BFPP，注册到这个registry，也就是 beanFactory
			// 这里注册了4个
			// 		ConfigurationClassPostProcessor（重点）、
			// 		AutowiredAnnotationBeanPostProcessor、
			// 		EventListenerMethodProcessor、
			// 		DefaultEventListenerFactory
			Set<BeanDefinitionHolder> processorDefinitions =
					AnnotationConfigUtils.registerAnnotationConfigProcessors(readerContext.getRegistry(), source);

			// 把上一步注册的几个 bd，也都加入到内嵌 component
			for (BeanDefinitionHolder processorDefinition : processorDefinitions) {
				compositeDef.addNestedComponent(new BeanComponentDefinition(processorDefinition));
			}
		}
		// 发布组件注册事件
		readerContext.fireComponentRegistered(compositeDef);
	}

	// 如果有 name-generator 属性，则设置这个名称生成器
	// 默认没配置就没有
	protected void parseBeanNameGenerator(Element element, ClassPathBeanDefinitionScanner scanner) {
		if (element.hasAttribute(NAME_GENERATOR_ATTRIBUTE)) {
			BeanNameGenerator beanNameGenerator = (BeanNameGenerator) instantiateUserDefinedStrategy(
					element.getAttribute(NAME_GENERATOR_ATTRIBUTE), BeanNameGenerator.class,
					scanner.getResourceLoader().getClassLoader());
			scanner.setBeanNameGenerator(beanNameGenerator);
		}
	}

	protected void parseScope(Element element, ClassPathBeanDefinitionScanner scanner) {
		// Register ScopeMetadataResolver if class name provided.
		// 获取属性 scope-resolver 的值，默认不配置则不处理
		if (element.hasAttribute(SCOPE_RESOLVER_ATTRIBUTE)) {
			// 获取属性 scoped-proxy
			if (element.hasAttribute(SCOPED_PROXY_ATTRIBUTE)) {
				// 如果 scope-resolver 和 scoped-proxy 都设置了，抛出异常，不允许这两个同时设置
				throw new IllegalArgumentException(
						"Cannot define both 'scope-resolver' and 'scoped-proxy' on <component-scan> tag");
			}
			// 反射获取 scope-resolver 属性对应的示例对象
			ScopeMetadataResolver scopeMetadataResolver = (ScopeMetadataResolver) instantiateUserDefinedStrategy(
					element.getAttribute(SCOPE_RESOLVER_ATTRIBUTE), ScopeMetadataResolver.class,
					scanner.getResourceLoader().getClassLoader());
			// 将示例对象设置进来
			scanner.setScopeMetadataResolver(scopeMetadataResolver);
		}

		// 判断属性 scoped-proxy 是否有值
		if (element.hasAttribute(SCOPED_PROXY_ATTRIBUTE)) {
			// 如果走到这里，则说明 scope-resolver 是空的
			// 获取 scoped-proxy 属性
			String mode = element.getAttribute(SCOPED_PROXY_ATTRIBUTE);
			// 设置对应的模式
			if ("targetClass".equals(mode)) {
				scanner.setScopedProxyMode(ScopedProxyMode.TARGET_CLASS);
			}
			else if ("interfaces".equals(mode)) {
				scanner.setScopedProxyMode(ScopedProxyMode.INTERFACES);
			}
			else if ("no".equals(mode)) {
				scanner.setScopedProxyMode(ScopedProxyMode.NO);
			}
			else {
				throw new IllegalArgumentException("scoped-proxy only supports 'no', 'interfaces' and 'targetClass'");
			}
		}
	}

	/**
	 * 解析 component-scan 下的子标签  context:exclude-filter 和 context:include-filter
	 * @param element
	 * @param scanner
	 * @param parserContext
	 */
	protected void parseTypeFilters(Element element, ClassPathBeanDefinitionScanner scanner, ParserContext parserContext) {
		// Parse exclude and include filter elements.
		ClassLoader classLoader = scanner.getResourceLoader().getClassLoader();
		// 获取子节点
		NodeList nodeList = element.getChildNodes();
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node node = nodeList.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				// 获取对应的标签名
				String localName = parserContext.getDelegate().getLocalName(node);
				try {
					// include-filter 标签
					if (INCLUDE_FILTER_ELEMENT.equals(localName)) {
						// 创建 typeFilter 的实现类实例对象
						TypeFilter typeFilter = createTypeFilter((Element) node, classLoader, parserContext);
						// 添加到 scanner 的 includeFilters 中
						scanner.addIncludeFilter(typeFilter);
					}
					// exclude-filter 标签
					else if (EXCLUDE_FILTER_ELEMENT.equals(localName)) {
						// 和 includeFilters 的处理类型，只是这个创建后添加到 excludeFilters 中
						TypeFilter typeFilter = createTypeFilter((Element) node, classLoader, parserContext);
						scanner.addExcludeFilter(typeFilter);
					}
				}
				catch (ClassNotFoundException ex) {
					parserContext.getReaderContext().warning(
							"Ignoring non-present type filter class: " + ex, parserContext.extractSource(element));
				}
				catch (Exception ex) {
					parserContext.getReaderContext().error(
							ex.getMessage(), parserContext.extractSource(element), ex.getCause());
				}
			}
		}
	}

	/**
	 * 创建一个类型过滤器，后边需要把这个过滤器添加到 scanner 的 includeFilters 或者 excludeFilters 中
	 * @param element
	 * @param classLoader
	 * @param parserContext
	 * @return
	 * @throws ClassNotFoundException
	 */
	@SuppressWarnings("unchecked")
	protected TypeFilter createTypeFilter(Element element, @Nullable ClassLoader classLoader,
			ParserContext parserContext) throws ClassNotFoundException {

		// 获取 type 属性值
		String filterType = element.getAttribute(FILTER_TYPE_ATTRIBUTE);
		// 获取 expression 属性值
		String expression = element.getAttribute(FILTER_EXPRESSION_ATTRIBUTE);
		// 表达式的解析，同样是用了 env 此时的配置进行解析
		expression = parserContext.getReaderContext().getEnvironment().resolvePlaceholders(expression);
		// 根据各个值的情况，创建并返回不同的 TypeFilter 实现类对象
		if ("annotation".equals(filterType)) {
			// 注解类型的过滤器
			return new AnnotationTypeFilter((Class<Annotation>) ClassUtils.forName(expression, classLoader));
		}
		else if ("assignable".equals(filterType)) {
			return new AssignableTypeFilter(ClassUtils.forName(expression, classLoader));
		}
		else if ("aspectj".equals(filterType)) {
			return new AspectJTypeFilter(expression, classLoader);
		}
		else if ("regex".equals(filterType)) {
			return new RegexPatternTypeFilter(Pattern.compile(expression));
		}
		else if ("custom".equals(filterType)) {
			Class<?> filterClass = ClassUtils.forName(expression, classLoader);
			if (!TypeFilter.class.isAssignableFrom(filterClass)) {
				throw new IllegalArgumentException(
						"Class is not assignable to [" + TypeFilter.class.getName() + "]: " + expression);
			}
			return (TypeFilter) BeanUtils.instantiateClass(filterClass);
		}
		else {
			throw new IllegalArgumentException("Unsupported filter type: " + filterType);
		}
	}

	@SuppressWarnings("unchecked")
	private Object instantiateUserDefinedStrategy(
			String className, Class<?> strategyType, @Nullable ClassLoader classLoader) {

		Object result;
		try {
			result = ReflectionUtils.accessibleConstructor(ClassUtils.forName(className, classLoader)).newInstance();
		}
		catch (ClassNotFoundException ex) {
			throw new IllegalArgumentException("Class [" + className + "] for strategy [" +
					strategyType.getName() + "] not found", ex);
		}
		catch (Throwable ex) {
			throw new IllegalArgumentException("Unable to instantiate class [" + className + "] for strategy [" +
					strategyType.getName() + "]: a zero-argument constructor is required", ex);
		}

		if (!strategyType.isAssignableFrom(result.getClass())) {
			throw new IllegalArgumentException("Provided class name must be an implementation of " + strategyType);
		}
		return result;
	}

}
