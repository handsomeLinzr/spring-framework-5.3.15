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

package org.springframework.core.io.support;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.UrlResource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * General purpose factory loading mechanism for internal use within the framework.
 *
 * <p>{@code SpringFactoriesLoader} {@linkplain #loadFactories loads} and instantiates
 * factories of a given type from {@value #FACTORIES_RESOURCE_LOCATION} files which
 * may be present in multiple JAR files in the classpath. The {@code spring.factories}
 * file must be in {@link Properties} format, where the key is the fully qualified
 * name of the interface or abstract class, and the value is a comma-separated list of
 * implementation class names. For example:
 *
 * <pre class="code">example.MyService=example.MyServiceImpl1,example.MyServiceImpl2</pre>
 *
 * where {@code example.MyService} is the name of the interface, and {@code MyServiceImpl1}
 * and {@code MyServiceImpl2} are two implementations.
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 3.2
 */
public final class SpringFactoriesLoader {

	/**
	 * The location to look for factories.
	 * <p>Can be present in multiple JAR files.
	 */
	public static final String FACTORIES_RESOURCE_LOCATION = "META-INF/spring.factories";


	private static final Log logger = LogFactory.getLog(SpringFactoriesLoader.class);

	// 已经通过类加载器从 spring.factories 文件加载来的数据
	// key 是类加载器，value 是对应的路径名称集合
	static final Map<ClassLoader, Map<String, List<String>>> cache = new ConcurrentReferenceHashMap<>();


	private SpringFactoriesLoader() {
	}


	// Spring 的 spi 机制，从 META-INF/spring.factories 文件中，获取到 factoryType 类型的所有类路径，并通过类加载器
	// 对加载到的类路径，获取对应的 factoryType 的所有类路径
	// 对得到的类路径都进行类加载和校验，通过后依次进行实例化对象
	// 最后返回对应的实例化对象集合
	/**
	 * Load and instantiate the factory implementations of the given type from
	 * {@value #FACTORIES_RESOURCE_LOCATION}, using the given class loader.
	 * <p>The returned factories are sorted through {@link AnnotationAwareOrderComparator}.
	 * <p>If a custom instantiation strategy is required, use {@link #loadFactoryNames}
	 * to obtain all registered factory names.
	 * <p>As of Spring Framework 5.3, if duplicate implementation class names are
	 * discovered for a given factory type, only one instance of the duplicated
	 * implementation type will be instantiated.
	 * @param factoryType the interface or abstract class representing the factory
	 * @param classLoader the ClassLoader to use for loading (can be {@code null} to use the default)
	 * @throws IllegalArgumentException if any factory implementation class cannot
	 * be loaded or if an error occurs while instantiating any factory
	 * @see #loadFactoryNames
	 */
	public static <T> List<T> loadFactories(Class<T> factoryType, @Nullable ClassLoader classLoader) {
		Assert.notNull(factoryType, "'factoryType' must not be null");
		// 获取类加载器
		ClassLoader classLoaderToUse = classLoader;
		if (classLoaderToUse == null) {
			classLoaderToUse = SpringFactoriesLoader.class.getClassLoader();
		}
		// 获取 factoryType 类型的所有 SPI 机制下需要加载的类的路径
		List<String> factoryImplementationNames = loadFactoryNames(factoryType, classLoaderToUse);
		if (logger.isTraceEnabled()) {
			logger.trace("Loaded [" + factoryType.getName() + "] names: " + factoryImplementationNames);
		}
		List<T> result = new ArrayList<>(factoryImplementationNames.size());
		for (String factoryImplementationName : factoryImplementationNames) {
			// instantiateFactory 加载校验并实例化对应的 factoryImplementationName 类对象
			// 将实例化后的对象添加到 result 中
			result.add(instantiateFactory(factoryImplementationName, factoryType, classLoaderToUse));
		}
		// 排序
		AnnotationAwareOrderComparator.sort(result);
		// 返回加载到的所有对象
		return result;
	}

	// 从 META-INF/spring.factories 文件中加载 factoryType 对应的所有类路径
	/**
	 * Load the fully qualified class names of factory implementations of the
	 * given type from {@value #FACTORIES_RESOURCE_LOCATION}, using the given
	 * class loader.
	 * <p>As of Spring Framework 5.3, if a particular implementation class name
	 * is discovered more than once for the given factory type, duplicates will
	 * be ignored.
	 * @param factoryType the interface or abstract class representing the factory
	 * @param classLoader the ClassLoader to use for loading resources; can be
	 * {@code null} to use the default
	 * @throws IllegalArgumentException if an error occurs while loading factory names
	 * @see #loadFactories
	 */
	public static List<String> loadFactoryNames(Class<?> factoryType, @Nullable ClassLoader classLoader) {
		ClassLoader classLoaderToUse = classLoader;
		if (classLoaderToUse == null) {
			classLoaderToUse = SpringFactoriesLoader.class.getClassLoader();
		}
		// 要要去的类全路径
		String factoryTypeName = factoryType.getName();
		// 加载 factoryTypeName 对应的所有类，如果没有则返回空 list
		// loadSpringFactories -> 得到当前类加载器对应加载的所有配置的类路径
		// getOrDefault -> 返回对应加载或者缓存到的类路径中，factoryTypeName 类型的集合
		return loadSpringFactories(classLoaderToUse).getOrDefault(factoryTypeName, Collections.emptyList());
	}

	// 获取通过类加载器 classLoader 从 spring.factories 中加载来的类
	// 返回这个 classLoader 加载到的或者缓存的，所有类的路径，字符串
	private static Map<String, List<String>> loadSpringFactories(ClassLoader classLoader) {
		// 先获取缓存
		Map<String, List<String>> result = cache.get(classLoader);
		// 有缓存，直接返回
		if (result != null) {
			return result;
		}

		// 没缓存
		result = new HashMap<>();
		try {
			// 获取所有的 META-INF/spring.factories 文件，加载成 URL
			Enumeration<URL> urls = classLoader.getResources(FACTORIES_RESOURCE_LOCATION);
			while (urls.hasMoreElements()) {
				// 遍历所有的 URL 文件
				URL url = urls.nextElement();
				UrlResource resource = new UrlResource(url);
				// 加载文件，加载成 properties
				Properties properties = PropertiesLoaderUtils.loadProperties(resource);
				// 遍历当前文件的配置
				for (Map.Entry<?, ?> entry : properties.entrySet()) {
					// 获取对应的 key，也就是对应的加载类的类型
					String factoryTypeName = ((String) entry.getKey()).trim();
					// 获取该类型下的所有类，封装成字符串数组
					String[] factoryImplementationNames =
							StringUtils.commaDelimitedListToStringArray((String) entry.getValue());
					// 遍历所有的类
					for (String factoryImplementationName : factoryImplementationNames) {
						// 添加到 result 封装的 map 中
						result.computeIfAbsent(factoryTypeName, key -> new ArrayList<>())
								.add(factoryImplementationName.trim());
					}
				}
			}

			// 替换
			// Replace all lists with unmodifiable lists containing unique elements
			result.replaceAll((factoryType, implementations) -> implementations.stream().distinct()
					.collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)));
			// 添加到缓存中
			cache.put(classLoader, result);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Unable to load factories from location [" +
					FACTORIES_RESOURCE_LOCATION + "]", ex);
		}
		// 返回得到的配置
		return result;
	}

	// 加载校验并实例化 factoryImplementationName 对象
	@SuppressWarnings("unchecked")
	private static <T> T instantiateFactory(String factoryImplementationName, Class<T> factoryType, ClassLoader classLoader) {
		try {
			// 加载 factoryImplementationName 对应的类
			Class<?> factoryImplementationClass = ClassUtils.forName(factoryImplementationName, classLoader);
			// 判断对应的这个加载到的类，是否属于给定的 factoryType 类
			// 如果不符合，则抛出异常
			if (!factoryType.isAssignableFrom(factoryImplementationClass)) {
				throw new IllegalArgumentException(
						"Class [" + factoryImplementationName + "] is not assignable to factory type [" + factoryType.getName() + "]");
			}
			// 通过反射对该类进行实例化对象，并返回
			return (T) ReflectionUtils.accessibleConstructor(factoryImplementationClass).newInstance();
		}
		catch (Throwable ex) {
			throw new IllegalArgumentException(
				"Unable to instantiate factory class [" + factoryImplementationName + "] for factory type [" + factoryType.getName() + "]",
				ex);
		}
	}

}
