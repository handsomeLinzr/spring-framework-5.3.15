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

package org.springframework.util.function;

import java.util.function.Supplier;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

// 传入3个属性对象
// 	单例对象   单例对象的获取    默认单例对象的获取
//  当单例对象是空的，则调用单例队形的获取来尝试得到单例对象，如果还没有则调用默认的来获取
/**
 * A {@link java.util.function.Supplier} decorator that caches a singleton result and
 * makes it available from {@link #get()} (nullable) and {@link #obtain()} (null-safe).
 *
 * <p>A {@code SingletonSupplier} can be constructed via {@code of} factory methods
 * or via constructors that provide a default supplier as a fallback. This is
 * particularly useful for method reference suppliers, falling back to a default
 * supplier for a method that returned {@code null} and caching the result.
 *
 * @author Juergen Hoeller
 * @since 5.1
 * @param <T> the type of results supplied by this supplier
 */
public class SingletonSupplier<T> implements Supplier<T> {

	@Nullable
	private final Supplier<? extends T> instanceSupplier;

	@Nullable
	private final Supplier<? extends T> defaultSupplier;

	@Nullable
	private volatile T singletonInstance;


	/**
	 * Build a {@code SingletonSupplier} with the given singleton instance
	 * and a default supplier for the case when the instance is {@code null}.
	 * @param instance the singleton instance (potentially {@code null})
	 * @param defaultSupplier the default supplier as a fallback
	 */
	public SingletonSupplier(@Nullable T instance, Supplier<? extends T> defaultSupplier) {
		this.instanceSupplier = null;
		this.defaultSupplier = defaultSupplier;
		this.singletonInstance = instance;
	}

	/**
	 * Build a {@code SingletonSupplier} with the given instance supplier
	 * and a default supplier for the case when the instance is {@code null}.
	 * @param instanceSupplier the immediate instance supplier
	 * @param defaultSupplier the default supplier as a fallback
	 */
	public SingletonSupplier(@Nullable Supplier<? extends T> instanceSupplier, Supplier<? extends T> defaultSupplier) {
		this.instanceSupplier = instanceSupplier;
		this.defaultSupplier = defaultSupplier;
	}

	private SingletonSupplier(Supplier<? extends T> supplier) {
		this.instanceSupplier = supplier;
		this.defaultSupplier = null;
	}

	private SingletonSupplier(T singletonInstance) {
		this.instanceSupplier = null;
		this.defaultSupplier = null;
		this.singletonInstance = singletonInstance;
	}


	// 获取共享单例对象的逻辑
	/**
	 * Get the shared singleton instance for this supplier.
	 * @return the singleton instance (or {@code null} if none)
	 */
	@Override
	@Nullable
	public T get() {
		// 先获取传入对象 singletonInstance
		T instance = this.singletonInstance;
		// 如果传入的单例对象是空的
		if (instance == null) {
			synchronized (this) {
				// 双重校验，单例模式
				instance = this.singletonInstance;
				if (instance == null) {
					if (this.instanceSupplier != null) {
						// 调用用一个对象，调用其中的获取方法，获取结果
						instance = this.instanceSupplier.get();
					}
					if (instance == null && this.defaultSupplier != null) {
						// 如果当前还没有对象，且有设置 defaultSupplier
						// 则调用 defaultSupplier 得到单例对象
						instance = this.defaultSupplier.get();
					}
					// 将得到的单例对象赋值给 singletonInstance
					this.singletonInstance = instance;
				}
			}
		}
		// 返回单例对象
		return instance;
	}

	/**
	 * Obtain the shared singleton instance for this supplier.
	 * @return the singleton instance (never {@code null})
	 * @throws IllegalStateException in case of no instance
	 */
	public T obtain() {
		T instance = get();
		Assert.state(instance != null, "No instance from Supplier");
		return instance;
	}


	/**
	 * Build a {@code SingletonSupplier} with the given singleton instance.
	 * @param instance the singleton instance (never {@code null})
	 * @return the singleton supplier (never {@code null})
	 */
	public static <T> SingletonSupplier<T> of(T instance) {
		return new SingletonSupplier<>(instance);
	}

	/**
	 * Build a {@code SingletonSupplier} with the given singleton instance.
	 * @param instance the singleton instance (potentially {@code null})
	 * @return the singleton supplier, or {@code null} if the instance was {@code null}
	 */
	@Nullable
	public static <T> SingletonSupplier<T> ofNullable(@Nullable T instance) {
		return (instance != null ? new SingletonSupplier<>(instance) : null);
	}

	/**
	 * Build a {@code SingletonSupplier} with the given supplier.
	 * @param supplier the instance supplier (never {@code null})
	 * @return the singleton supplier (never {@code null})
	 */
	public static <T> SingletonSupplier<T> of(Supplier<T> supplier) {
		return new SingletonSupplier<>(supplier);
	}

	/**
	 * Build a {@code SingletonSupplier} with the given supplier.
	 * @param supplier the instance supplier (potentially {@code null})
	 * @return the singleton supplier, or {@code null} if the instance supplier was {@code null}
	 */
	@Nullable
	public static <T> SingletonSupplier<T> ofNullable(@Nullable Supplier<T> supplier) {
		return (supplier != null ? new SingletonSupplier<>(supplier) : null);
	}

}
