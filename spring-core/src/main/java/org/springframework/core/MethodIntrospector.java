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

package org.springframework.core;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Defines the algorithm for searching for metadata-associated methods exhaustively
 * including interfaces and parent classes while also dealing with parameterized methods
 * as well as common scenarios encountered with interface and class-based proxies.
 *
 * <p>Typically, but not necessarily, used for finding annotated handler methods.
 *
 * @author Juergen Hoeller
 * @author Rossen Stoyanchev
 * @since 4.2.3
 */
public final class MethodIntrospector {

	private MethodIntrospector() {
	}


	// 根据相关目标类元数据的的查找，选择给定目标类型上的方法
	/**
	 * Select methods on the given target type based on the lookup of associated metadata.
	 * <p>Callers define methods of interest through the {@link MetadataLookup} parameter,
	 * allowing to collect the associated metadata into the result map.
	 * @param targetType the target type to search methods on
	 * @param metadataLookup a {@link MetadataLookup} callback to inspect methods of interest,
	 * returning non-null metadata to be associated with a given method if there is a match,
	 * or {@code null} for no match
	 * @return the selected methods associated with their metadata (in the order of retrieval),
	 * or an empty map in case of no match
	 */
	public static <T> Map<Method, T> selectMethods(Class<?> targetType, final MetadataLookup<T> metadataLookup) {
		// 创建集合对象
		final Map<Method, T> methodMap = new LinkedHashMap<>();
		Set<Class<?>> handlerTypes = new LinkedHashSet<>();
		Class<?> specificHandlerType = null;

		// 判断是否 targetType 不是 Proxy 类
		if (!Proxy.isProxyClass(targetType)) {
			// 获取对应的原始类
			specificHandlerType = ClassUtils.getUserClass(targetType);
			// 添加到 handlerTypes 中
			handlerTypes.add(specificHandlerType);
		}
		// 添加 targetType 的所有接口到 handlerTypes
		handlerTypes.addAll(ClassUtils.getAllInterfacesForClassAsSet(targetType));

		// 遍历 handlerTypes 里的所有类进行处理
		for (Class<?> currentHandlerType : handlerTypes) {
			// 当前处理的类型，specificHandlerType 不为空则是这个，否则就是当前遍历到的类型
			final Class<?> targetClass = (specificHandlerType != null ? specificHandlerType : currentHandlerType);

			// 通过类型 currentHandlerType 获取所有的方法
			// 对所有方法进行匹配，匹配上的方法调用回调方法
			// 参数 1 对应类，参数 2 回调方法，参数 3 进行方法匹配
			ReflectionUtils.doWithMethods(currentHandlerType, method -> {
				// 方法回调的逻辑
				// 先获取 targetClass 中对应的 method 方法
				// 这里不直接通过 method，而是需要再处理一层，是因为有可能当前的 method 是来自接口的方法，并没有真正的实现
				// 通过 ClassUtils.getMostSpecificMethod，能拿到对应的 targetClass 这个类中的对应 method
				Method specificMethod = ClassUtils.getMostSpecificMethod(method, targetClass);
				// 调用 metadataLookup 的	逻辑，返回 result
				T result = metadataLookup.inspect(specificMethod);
				// 判断如果 result 不为空
				if (result != null) {
					// 获取原始方法
					Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(specificMethod);
					// 判断如果原始方法就是自己这个方法，或者 metadataLookup 调用对应的原始方法返回 nul
					if (bridgedMethod == specificMethod || metadataLookup.inspect(bridgedMethod) == null) {
						// 添加对应的方法和 result 到methodMap 中
						methodMap.put(specificMethod, result);
					}
				}
			}, ReflectionUtils.USER_DECLARED_METHODS);
		}

		// 最后放回这个收集到结果的 map
		return methodMap;
	}

	/**
	 * Select methods on the given target type based on a filter.
	 * <p>Callers define methods of interest through the {@code MethodFilter} parameter.
	 * @param targetType the target type to search methods on
	 * @param methodFilter a {@code MethodFilter} to help
	 * recognize handler methods of interest
	 * @return the selected methods, or an empty set in case of no match
	 */
	public static Set<Method> selectMethods(Class<?> targetType, final ReflectionUtils.MethodFilter methodFilter) {
		return selectMethods(targetType,
				(MetadataLookup<Boolean>) method -> (methodFilter.matches(method) ? Boolean.TRUE : null)).keySet();
	}

	// 在目标类上选择一个可调用的方法
	/**
	 * Select an invocable method on the target type: either the given method itself
	 * if actually exposed on the target type, or otherwise a corresponding method
	 * on one of the target type's interfaces or on the target type itself.
	 * <p>Matches on user-declared interfaces will be preferred since they are likely
	 * to contain relevant metadata that corresponds to the method on the target class.
	 * @param method the method to check
	 * @param targetType the target type to search methods on
	 * (typically an interface-based JDK proxy)
	 * @return a corresponding invocable method on the target type
	 * @throws IllegalStateException if the given method is not invocable on the given
	 * target type (typically due to a proxy mismatch)
	 */
	public static Method selectInvocableMethod(Method method, Class<?> targetType) {
		// 判断如果声明该方法所属的类，就是 targetType
		if (method.getDeclaringClass().isAssignableFrom(targetType)) {
			// 一般都走这里，因为一般这个方法都是声明在 targetType 这个累上，所以返回这个方法
			return method;
		}
		try {
			// 获取方法名
			String methodName = method.getName();
			// 获取参数类型
			Class<?>[] parameterTypes = method.getParameterTypes();
			// 获取类的接口
			for (Class<?> ifc : targetType.getInterfaces()) {
				try {
					// 尝试获取该方法
					// 如果获取不到，则继续下一个接口
					return ifc.getMethod(methodName, parameterTypes);
				}
				catch (NoSuchMethodException ex) {
					// Alright, not on this interface then...
				}
			}
			// 最后进行自身类进行尝试获取
			// A final desperate attempt on the proxy class itself...
			return targetType.getMethod(methodName, parameterTypes);
		}
		catch (NoSuchMethodException ex) {
			throw new IllegalStateException(String.format(
					"Need to invoke method '%s' declared on target class '%s', " +
					"but not found in any interface(s) of the exposed proxy type. " +
					"Either pull the method up to an interface or switch to CGLIB " +
					"proxies by enforcing proxy-target-class mode in your configuration.",
					method.getName(), method.getDeclaringClass().getSimpleName()));
		}
	}


	/**
	 * A callback interface for metadata lookup on a given method.
	 * @param <T> the type of metadata returned
	 */
	@FunctionalInterface
	public interface MetadataLookup<T> {

		/**
		 * Perform a lookup on the given method and return associated metadata, if any.
		 * @param method the method to inspect
		 * @return non-null metadata to be associated with a method if there is a match,
		 * or {@code null} for no match
		 */
		@Nullable
		T inspect(Method method);
	}

}
