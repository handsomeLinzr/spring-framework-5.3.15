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

import java.lang.reflect.Method;
import java.util.Map;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.util.ConcurrentReferenceHashMap;

// 处理 Bean 注解方法的工具
/**
 * Utilities for processing {@link Bean}-annotated methods.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 */
abstract class BeanAnnotationHelper {

	// 记录 Bean 注解的方法 和 名称
	private static final Map<Method, String> beanNameCache = new ConcurrentReferenceHashMap<>();

	// 记录代理缓存
	private static final Map<Method, Boolean> scopedProxyCache = new ConcurrentReferenceHashMap<>();


	// 判断该方法是否是一个 Bean 注解的方法
	public static boolean isBeanAnnotated(Method method) {
		return AnnotatedElementUtils.hasAnnotation(method, Bean.class);
	}

	public static String determineBeanNameFor(Method beanMethod) {
		// 从缓存中获取，第一次是获取为空
		String beanName = beanNameCache.get(beanMethod);
		if (beanName == null) {
			// By default, the bean name is the name of the @Bean-annotated method
			// 获取方法的名称，默认方法名就是 bean 的名称
			beanName = beanMethod.getName();
			// Check to see if the user has explicitly set a custom bean name...
			// 检查是否设置了自定义的 bean 名称，在 Bean 注解上
			// 这里获取到了 Bean 注解的所以属性值
			AnnotationAttributes bean =
					AnnotatedElementUtils.findMergedAnnotationAttributes(beanMethod, Bean.class, false, false);
			if (bean != null) {
				// 获取 name 属性
				String[] names = bean.getStringArray("name");
				if (names.length > 0) {
					// 如果有自定义，则第一个就是 beanName
					beanName = names[0];
				}
			}
			// 放入缓存
			beanNameCache.put(beanMethod, beanName);
		}
		// 返回 beanName
		return beanName;
	}

	public static boolean isScopedProxy(Method beanMethod) {
		// 先从 scopedProxyCache 缓存中获取这个方法对应的结果
		Boolean scopedProxy = scopedProxyCache.get(beanMethod);
		// 第一次调用，这里是空的，所以第一次会返回 null
		if (scopedProxy == null) {
			// 获取 beanMethod 中注解 Scope 的属性值
			AnnotationAttributes scope =
					AnnotatedElementUtils.findMergedAnnotationAttributes(beanMethod, Scope.class, false, false);
			// 获取对应 Scope 注解是否有属性值 proxyMode，且不等于 NO
			scopedProxy = (scope != null && scope.getEnum("proxyMode") != ScopedProxyMode.NO);
			// 设置缓存，下次直接拿
			scopedProxyCache.put(beanMethod, scopedProxy);
		}
		return scopedProxy;
	}

}
