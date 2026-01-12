/*
 * Copyright 2002-2021 the original author or authors.
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.scope.ScopedProxyFactoryBean;
import org.springframework.asm.Type;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.SimpleInstantiationStrategy;
import org.springframework.cglib.core.ClassGenerator;
import org.springframework.cglib.core.ClassLoaderAwareGeneratorStrategy;
import org.springframework.cglib.core.Constants;
import org.springframework.cglib.core.SpringNamingPolicy;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.CallbackFilter;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.Factory;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.cglib.proxy.NoOp;
import org.springframework.cglib.transform.ClassEmitterTransformer;
import org.springframework.cglib.transform.TransformingClassGenerator;
import org.springframework.lang.Nullable;
import org.springframework.objenesis.ObjenesisException;
import org.springframework.objenesis.SpringObjenesis;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

// 在进行 BFPP 处理的时候，在 ConfigurationClassPostProcessor 处理 postProcessBeanFactory 的时候，
// 会将所有的 Configuration 注解的 bd，和有  Bean 注解方法发的 bd 都进行 beanClass 的增强代理，而代理
// 后的逻辑就是用的这个类
/**
 * Enhances {@link Configuration} classes by generating a CGLIB subclass which
 * interacts with the Spring container to respect bean scoping semantics for
 * {@code @Bean} methods. Each such {@code @Bean} method will be overridden in
 * the generated subclass, only delegating to the actual {@code @Bean} method
 * implementation if the container actually requests the construction of a new
 * instance. Otherwise, a call to such an {@code @Bean} method serves as a
 * reference back to the container, obtaining the corresponding bean by name.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.0
 * @see #enhance
 * @see ConfigurationClassPostProcessor
 */
class ConfigurationClassEnhancer {

	// 代理类的增强逻辑的类
	// The callbacks to use. Note that these callbacks must be stateless.
	private static final Callback[] CALLBACKS = new Callback[] {
			new BeanMethodInterceptor(),  // bean 方法用
			new BeanFactoryAwareMethodInterceptor(),  // 用于设置 beanFactory 用
			NoOp.INSTANCE
	};

	private static final ConditionalCallbackFilter CALLBACK_FILTER = new ConditionalCallbackFilter(CALLBACKS);

	// 配置类代理增强类的 beanFactory 属性
	private static final String BEAN_FACTORY_FIELD = "$$beanFactory";


	private static final Log logger = LogFactory.getLog(ConfigurationClassEnhancer.class);

	private static final SpringObjenesis objenesis = new SpringObjenesis();


	/**
	 * Loads the specified class and generates a CGLIB subclass of it equipped with
	 * container-aware callbacks capable of respecting scoping and other bean semantics.
	 * @return the enhanced subclass
	 */
	public Class<?> enhance(Class<?> configClass, @Nullable ClassLoader classLoader) {
		if (EnhancedConfiguration.class.isAssignableFrom(configClass)) {
			// 已经代理了，直接返回
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Ignoring request to enhance %s as it has " +
						"already been enhanced. This usually indicates that more than one " +
						"ConfigurationClassPostProcessor has been registered (e.g. via " +
						"<context:annotation-config>). This is harmless, but you may " +
						"want check your configuration and remove one CCPP if possible",
						configClass.getName()));
			}
			return configClass;
		}
		// 创建代理类
		Class<?> enhancedClass = createClass(newEnhancer(configClass, classLoader));
		if (logger.isTraceEnabled()) {
			logger.trace(String.format("Successfully enhanced %s; enhanced class name is: %s",
					configClass.getName(), enhancedClass.getName()));
		}
		// 返回代理类
		return enhancedClass;
	}

	// 创建一个 cglib 代理对象
	/**
	 * Creates a new CGLIB {@link Enhancer} instance.
	 */
	private Enhancer newEnhancer(Class<?> configSuperClass, @Nullable ClassLoader classLoader) {
		// 这里就是非常常规的 cglib 代理类创建过程，主要是看 callback 设置了什么
		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(configSuperClass);
		// 这里额外给代理类加了实现的接口 EnhancedConfiguration，
		// 这个同时也是一个 BeanFactoryAware 接口，最后会设置进去 beanFactory
		enhancer.setInterfaces(new Class<?>[] {EnhancedConfiguration.class});
		enhancer.setUseFactory(false);
		enhancer.setNamingPolicy(SpringNamingPolicy.INSTANCE);
		// 设置策略 BeanFactoryAwareGeneratorStrategy
		enhancer.setStrategy(new BeanFactoryAwareGeneratorStrategy(classLoader));
		// callback
		// 这里其实就是设置了三个：BeanMethodInterceptor, BeanFactoryAwareMethodInterceptor, NoOp.INSTANCE
		enhancer.setCallbackFilter(CALLBACK_FILTER);
		// callback 类型，也是 BeanMethodInterceptor, BeanFactoryAwareMethodInterceptor, NoOp.INSTANCE 三种 Class 类型
		enhancer.setCallbackTypes(CALLBACK_FILTER.getCallbackTypes());
		return enhancer;
	}

	// 创建类，用增强器生成一个对应的字类，确保能调用 callback 回来
	/**
	 * Uses enhancer to generate a subclass of superclass,
	 * ensuring that callbacks are registered for the new subclass.
	 */
	private Class<?> createClass(Enhancer enhancer) {
		// 获取代理类的 Class 对象
		Class<?> subclass = enhancer.createClass();
		// Registering callbacks statically (as opposed to thread-local)
		// is critical for usage in an OSGi environment (SPR-5932)...
		// 注册
		Enhancer.registerStaticCallbacks(subclass, CALLBACKS);
		return subclass;
	}


	// 让 Configuration 通过 cglib 代理的字类对象都去实现这个类，促使增强代理的幂等行为
	// 同时继承了 BeanFactoryAware，让代理类都成拿到 BeanFactory 对象
	// 这个类设计只能 Spring 内部使用，用 public 修饰是为了让其他包能够生成子类
	/**
	 * Marker interface to be implemented by all @Configuration CGLIB subclasses.
	 * Facilitates idempotent behavior for {@link ConfigurationClassEnhancer#enhance}
	 * through checking to see if candidate classes are already assignable to it, e.g.
	 * have already been enhanced.
	 * <p>Also extends {@link BeanFactoryAware}, as all enhanced {@code @Configuration}
	 * classes require access to the {@link BeanFactory} that created them.
	 * <p>Note that this interface is intended for framework-internal use only, however
	 * must remain public in order to allow access to subclasses generated from other
	 * packages (i.e. user code).
	 */
	public interface EnhancedConfiguration extends BeanFactoryAware {
	}


	/**
	 * Conditional {@link Callback}.
	 * @see ConditionalCallbackFilter
	 */
	private interface ConditionalCallback extends Callback {

		boolean isMatch(Method candidateMethod);
	}


	/**
	 * A {@link CallbackFilter} that works by interrogating {@link Callback Callbacks} in the order
	 * that they are defined via {@link ConditionalCallback}.
	 */
	private static class ConditionalCallbackFilter implements CallbackFilter {

		private final Callback[] callbacks;

		private final Class<?>[] callbackTypes;

		public ConditionalCallbackFilter(Callback[] callbacks) {
			this.callbacks = callbacks;
			this.callbackTypes = new Class<?>[callbacks.length];
			for (int i = 0; i < callbacks.length; i++) {
				this.callbackTypes[i] = callbacks[i].getClass();
			}
		}

		@Override
		public int accept(Method method) {
			for (int i = 0; i < this.callbacks.length; i++) {
				Callback callback = this.callbacks[i];
				if (!(callback instanceof ConditionalCallback) || ((ConditionalCallback) callback).isMatch(method)) {
					return i;
				}
			}
			throw new IllegalStateException("No callback available for method " + method.getName());
		}

		public Class<?>[] getCallbackTypes() {
			return this.callbackTypes;
		}
	}


	/**
	 * Custom extension of CGLIB's DefaultGeneratorStrategy, introducing a {@link BeanFactory} field.
	 * Also exposes the application ClassLoader as thread context ClassLoader for the time of
	 * class generation (in order for ASM to pick it up when doing common superclass resolution).
	 */
	private static class BeanFactoryAwareGeneratorStrategy extends
			ClassLoaderAwareGeneratorStrategy {

		public BeanFactoryAwareGeneratorStrategy(@Nullable ClassLoader classLoader) {
			super(classLoader);
		}

		@Override
		protected ClassGenerator transform(ClassGenerator cg) throws Exception {
			ClassEmitterTransformer transformer = new ClassEmitterTransformer() {
				@Override
				public void end_class() {
					declare_field(Constants.ACC_PUBLIC, BEAN_FACTORY_FIELD, Type.getType(BeanFactory.class), null);
					super.end_class();
				}
			};
			return new TransformingClassGenerator(cg, transformer);
		}

	}


	// 拦截设置 beanFactory 的方法
	/**
	 * Intercepts the invocation of any {@link BeanFactoryAware#setBeanFactory(BeanFactory)} on
	 * {@code @Configuration} class instances for the purpose of recording the {@link BeanFactory}.
	 * @see EnhancedConfiguration
	 */
	private static class BeanFactoryAwareMethodInterceptor implements MethodInterceptor, ConditionalCallback {

		// 调用 BeanFactoryAware.setBeanFactory 的时候，会进入这个拦截，
		// 将 beanFactory 设置到属性 $$beanFactory
		// 如果代理前就是一个 BeanFactoryAware 实现类，则需要继续调用 setBeanFactory
		@Override
		@Nullable
		public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
			// 获取到代理类的属性 $$beanFactory
			Field field = ReflectionUtils.findField(obj.getClass(), BEAN_FACTORY_FIELD);
			Assert.state(field != null, "Unable to find generated BeanFactory field");
			// 设置值，这里的 args[0] 就是第一个参数，就是 beanFactory
			field.set(obj, args[0]);

			// Does the actual (non-CGLIB) superclass implement BeanFactoryAware?
			// If so, call its setBeanFactory() method. If not, just exit.
			// 如果这个配置类真的是实现了 BeanFactoryAware，则还需要继续调用一个 setBeanFactory
			if (BeanFactoryAware.class.isAssignableFrom(ClassUtils.getUserClass(obj.getClass().getSuperclass()))) {
				// 调用方法 setBeanFactory，这个是配置类本身就实现了 BeanFactoryAware 才会执行
				return proxy.invokeSuper(obj, args);
			}
			return null;
		}

		@Override
		public boolean isMatch(Method candidateMethod) {
			// 是否是 setBeanFactory 方法
			return isSetBeanFactory(candidateMethod);
		}

		// 判断是否是一个 setBeanFactory 方法
		public static boolean isSetBeanFactory(Method candidateMethod) {
			// 方法名称 = setBeanFactory
			return (candidateMethod.getName().equals("setBeanFactory") &&
					// 参数个数 = 1
					candidateMethod.getParameterCount() == 1 &&
					// 参数类型是 BeanFactory.class
					BeanFactory.class == candidateMethod.getParameterTypes()[0] &&
					// 当前类是一个 BeanFactoryAware
					BeanFactoryAware.class.isAssignableFrom(candidateMethod.getDeclaringClass()));
		}
	}


	/**
	 * Intercepts the invocation of any {@link Bean}-annotated methods in order to ensure proper
	 * handling of bean semantics such as scoping and AOP proxying.
	 * @see Bean
	 * @see ConfigurationClassEnhancer
	 */
	private static class BeanMethodInterceptor implements MethodInterceptor, ConditionalCallback {

		// 增强 Bean 方法
		// 在调用方法前先检查有没有在 beanFactory 中已存在
		/**
		 * Enhance a {@link Bean @Bean} method to check the supplied BeanFactory for the
		 * existence of this bean object.
		 * @throws Throwable as a catch-all for any exception that may be thrown when invoking the
		 * super implementation of the proxied method i.e., the actual {@code @Bean} method
		 */
		@Override
		@Nullable
		public Object intercept(Object enhancedConfigInstance, Method beanMethod, Object[] beanMethodArgs,
					MethodProxy cglibMethodProxy) throws Throwable {

			// 获取到 beanFactory 对象
			ConfigurableBeanFactory beanFactory = getBeanFactory(enhancedConfigInstance);
			// 获取 bean 的名称，一般默认是用方法名，除非 Bean 注解指定了 name 属性
			String beanName = BeanAnnotationHelper.determineBeanNameFor(beanMethod);

			// Determine whether this bean is a scoped-proxy
			// 检查判断该 bean 方法是不是有注解 Scope，且属性 proxyMode 不是 NO 选项
			if (BeanAnnotationHelper.isScopedProxy(beanMethod)) {
				// 创建对应的代理后的 beanName 名称
				String scopedBeanName = ScopedProxyCreator.getTargetBeanName(beanName);
				// 如果这个 bean 正常创建
				if (beanFactory.isCurrentlyInCreation(scopedBeanName)) {
					// 替换 beanName
					beanName = scopedBeanName;
				}
			}

			// To handle the case of an inter-bean method reference, we must explicitly check the
			// container for already cached instances.
			// 检查当前容器中是否已经缓存了实例

			// First, check to see if the requested bean is a FactoryBean. If so, create a subclass
			// proxy that intercepts calls to getObject() and returns any cached bean instance.
			// This ensures that the semantics of calling a FactoryBean from within @Bean methods
			// is the same as that of referring to a FactoryBean within XML. See SPR-6602.
			// 首先，检查是否这个 bean 是一个 FactoryBean，如果是则创建代理的字类，然后拦截器去调用 getObject() 方法并且返回缓存的 bean 实例
			// 这样确保从 Bean 注解的方法中调用 和 从 xml 中配置 FactoryBean 语义相同
			if (factoryContainsBean(beanFactory, BeanFactory.FACTORY_BEAN_PREFIX + beanName) &&
					// 1.beanFactory 中是否有这个 beanName 对应的 factoryBean，且当前不在创建
					// 2.beanFactory 中是否有这个 beanName，其当前不在创建：这个一般是 false，因为这个 bean 方法正在创建
					factoryContainsBean(beanFactory, beanName)) {
				// 如果都是 true，则获取对应的 factoryBean 对象
				Object factoryBean = beanFactory.getBean(BeanFactory.FACTORY_BEAN_PREFIX + beanName);
				if (factoryBean instanceof ScopedProxyFactoryBean) {
					// Scoped proxy factory beans are a special case and should not be further proxied
					// ScopedProxyFactoryBean 类型的 factoryBean 特殊，不处理
				}
				else {
					// It is a candidate FactoryBean - go ahead with enhancement
					// 普通的 factoryBean，则对 factoryBean 进行代理，通过拦截 getObject 方法，改成调用 beanFactory.getBean 的方法
					// 返回这个 factoryBean 的代理对象
					return enhanceFactoryBean(factoryBean, beanMethod.getReturnType(), beanFactory, beanName);
				}
			}

			// 普通情况，判断当前是不是真正调创建执行这个 bean 方法
			// 如果是，则不需要继续代理了，直接调用 bean 方法就好了
			if (isCurrentlyInvokedFactoryMethod(beanMethod)) {
				// 一般都是返回 true，走这个分支逻辑
				// The factory is calling the bean method in order to instantiate and register the bean
				// (i.e. via a getBean() call) -> invoke the super implementation of the method to actually
				// create the bean instance.
				if (logger.isInfoEnabled() &&
						BeanFactoryPostProcessor.class.isAssignableFrom(beanMethod.getReturnType())) {
					logger.info(String.format("@Bean method %s.%s is non-static and returns an object " +
									"assignable to Spring's BeanFactoryPostProcessor interface. This will " +
									"result in a failure to process annotations such as @Autowired, " +
									"@Resource and @PostConstruct within the method's declaring " +
									"@Configuration class. Add the 'static' modifier to this method to avoid " +
									"these container lifecycle issues; see @Bean javadoc for complete details.",
							beanMethod.getDeclaringClass().getSimpleName(), beanMethod.getName()));
				}
				// 调用原始的方法，这里就是直接去执行 bean 方法了，不需要再去拦截
				// 因为走到这里说明当前这个 bean 正在执行当前，不能再通过拦截的方式改成 beanFactory.getBean，因为还没创建成功，即使拿到也是不完整的对象
				// 这时候就直接调用原生 bean 方法就好了
				return cglibMethodProxy.invokeSuper(enhancedConfigInstance, beanMethodArgs);
			}

			// 处理 bean 引用
			return resolveBeanReference(beanMethod, beanMethodArgs, beanFactory, beanName);
		}

		private Object resolveBeanReference(Method beanMethod, Object[] beanMethodArgs,
				ConfigurableBeanFactory beanFactory, String beanName) {

			// The user (i.e. not the factory) is requesting this bean through a call to
			// the bean method, direct or indirect. The bean may have already been marked
			// as 'in creation' in certain autowiring scenarios; if so, temporarily set
			// the in-creation status to false in order to avoid an exception.
			// 判断当前这个 beanName 是不是正在创建
			boolean alreadyInCreation = beanFactory.isCurrentlyInCreation(beanName);
			try {
				if (alreadyInCreation) {
					// 如果这个 beanName 当前正在创建中，则先设置属性，以免异常
					beanFactory.setCurrentlyInCreation(beanName, false);
				}
				// 是否有参数
				boolean useArgs = !ObjectUtils.isEmpty(beanMethodArgs);
				// 如果 bean 方法有用参数，其实单例的类型
				if (useArgs && beanFactory.isSingleton(beanName)) {
					// Stubbed null arguments just for reference purposes,
					// expecting them to be autowired for regular singleton references?
					// A safe assumption since @Bean singleton arguments cannot be optional...
					for (Object arg : beanMethodArgs) {
						if (arg == null) {
							useArgs = false;
							break;
						}
					}
				}
				// 获取对应的实例对象，通过 beanFactory.getBean 走了 Spring 逻辑，确保 bean 的唯一性
				Object beanInstance = (useArgs ? beanFactory.getBean(beanName, beanMethodArgs) :
						beanFactory.getBean(beanName));
				if (!ClassUtils.isAssignableValue(beanMethod.getReturnType(), beanInstance)) {
					// Detect package-protected NullBean instance through equals(null) check
					if (beanInstance.equals(null)) {
						if (logger.isDebugEnabled()) {
							logger.debug(String.format("@Bean method %s.%s called as bean reference " +
									"for type [%s] returned null bean; resolving to null value.",
									beanMethod.getDeclaringClass().getSimpleName(), beanMethod.getName(),
									beanMethod.getReturnType().getName()));
						}
						beanInstance = null;
					}
					else {
						String msg = String.format("@Bean method %s.%s called as bean reference " +
								"for type [%s] but overridden by non-compatible bean instance of type [%s].",
								beanMethod.getDeclaringClass().getSimpleName(), beanMethod.getName(),
								beanMethod.getReturnType().getName(), beanInstance.getClass().getName());
						try {
							BeanDefinition beanDefinition = beanFactory.getMergedBeanDefinition(beanName);
							msg += " Overriding bean of same name declared in: " + beanDefinition.getResourceDescription();
						}
						catch (NoSuchBeanDefinitionException ex) {
							// Ignore - simply no detailed message then.
						}
						throw new IllegalStateException(msg);
					}
				}
				// 获取当前正在处理的 bean 方法
				Method currentlyInvoked = SimpleInstantiationStrategy.getCurrentlyInvokedFactoryMethod();
				if (currentlyInvoked != null) {
					// 得到 bean 名称
					String outerBeanName = BeanAnnotationHelper.determineBeanNameFor(currentlyInvoked);
					// 设置依赖 beanName  outerBeanName
					beanFactory.registerDependentBean(beanName, outerBeanName);
				}
				// 返回实例
				return beanInstance;
			}
			finally {
				if (alreadyInCreation) {
					beanFactory.setCurrentlyInCreation(beanName, true);
				}
			}
		}

		// 这个回调函数的匹配规则
		@Override
		public boolean isMatch(Method candidateMethod) {
			// 不是 Object.class
			return (candidateMethod.getDeclaringClass() != Object.class &&
					// 不是 setBeanFactory 方法
					!BeanFactoryAwareMethodInterceptor.isSetBeanFactory(candidateMethod) &&
					// 是 Bean 注解的方法
					BeanAnnotationHelper.isBeanAnnotated(candidateMethod));
		}

		// 获取 beanFactory 这个属性对象，因为该代理类已经实现了 BeanFactoryAware 接口，所以会设置进来
		private ConfigurableBeanFactory getBeanFactory(Object enhancedConfigInstance) {
			// 获取当前 beanFactory 字段
			Field field = ReflectionUtils.findField(enhancedConfigInstance.getClass(), BEAN_FACTORY_FIELD);
			Assert.state(field != null, "Unable to find generated bean factory field");
			// 反射获取到这个 beanFactory 对象
			Object beanFactory = ReflectionUtils.getField(field, enhancedConfigInstance);
			Assert.state(beanFactory != null, "BeanFactory has not been injected into @Configuration class");
			Assert.state(beanFactory instanceof ConfigurableBeanFactory,
					"Injected BeanFactory is not a ConfigurableBeanFactory");
			// 返回
			return (ConfigurableBeanFactory) beanFactory;
		}

		/**
		 * Check the BeanFactory to see whether the bean named <var>beanName</var> already
		 * exists. Accounts for the fact that the requested bean may be "in creation", i.e.:
		 * we're in the middle of servicing the initial request for this bean. From an enhanced
		 * factory method's perspective, this means that the bean does not actually yet exist,
		 * and that it is now our job to create it for the first time by executing the logic
		 * in the corresponding factory method.
		 * <p>Said another way, this check repurposes
		 * {@link ConfigurableBeanFactory#isCurrentlyInCreation(String)} to determine whether
		 * the container is calling this method or the user is calling this method.
		 * @param beanName name of bean to check for
		 * @return whether <var>beanName</var> already exists in the factory
		 */
		private boolean factoryContainsBean(ConfigurableBeanFactory beanFactory, String beanName) {
			// 在 beanFactory 中存在 bd
			// 当前不是在创建阶段
			// 这里需要注意的是，必须保证 beanName 不是正在创建中，是为了避免陷入无限循环
			// 当如果当前这个 bean 正在创建中，则不能再被通过 beanFactory.getBean 获取了，
			// 不然假如 A 引用了 B，B 引用了 A，然后又来到 A 的时候，继续 getBean 又要去拿 B，形成循环
			// 此时应该到 A 了，则是直接去调用 bean 方法通过 new 得到对应的实例对象
			return (beanFactory.containsBean(beanName) && !beanFactory.isCurrentlyInCreation(beanName));
		}


		// 检查给定的方法是否对应于容器当前调用的工厂方法。
		/**
		 * Check whether the given method corresponds to the container's currently invoked
		 * factory method. Compares method name and parameter types only in order to work
		 * around a potential problem with covariant return types (currently only known
		 * to happen on Groovy classes).
		 */
		private boolean isCurrentlyInvokedFactoryMethod(Method method) {
			// 当前正在调用的 Bean 方法
			Method currentlyInvoked = SimpleInstantiationStrategy.getCurrentlyInvokedFactoryMethod();
			// 当前调用的 bean 方法不为空
			// 当前调用的 bean 方法名称和传进来的方法一样
			// 当前调用的 bean 方法参数和传进来的方法的参数一样
			// 其实这里就是判断这个 method 是不是就是当前正在执行的方法
			return (currentlyInvoked != null && method.getName().equals(currentlyInvoked.getName()) &&
					Arrays.equals(method.getParameterTypes(), currentlyInvoked.getParameterTypes()));
		}

		// 处理 factoryBean 的代理增强逻辑
		// 创建一个个代理字类，拦截 factoryBean 的 getObject 方法
		// 委派给当前的 beanFactory，而不是直接创建一个实例对象
		/**
		 * Create a subclass proxy that intercepts calls to getObject(), delegating to the current BeanFactory
		 * instead of creating a new instance. These proxies are created only when calling a FactoryBean from
		 * within a Bean method, allowing for proper scoping semantics even when working against the FactoryBean
		 * instance directly. If a FactoryBean instance is fetched through the container via &-dereferencing,
		 * it will not be proxied. This too is aligned with the way XML configuration works.
		 */
		private Object enhanceFactoryBean(Object factoryBean, Class<?> exposedType,
				ConfigurableBeanFactory beanFactory, String beanName) {

			try {
				// 先获取 factoryBean 的 Class 对象
				Class<?> clazz = factoryBean.getClass();
				// 判断是否是 final 类型，final 类型则 cglib 代理不了，因为 cglib 是通过继承字类实现代理
				boolean finalClass = Modifier.isFinal(clazz.getModifiers());
				// 判断 getObject 方法是否是 final 修饰，同样 final 修饰的方法没法重写
				boolean finalMethod = Modifier.isFinal(clazz.getMethod("getObject").getModifiers());
				// 如果是 final 类或者 final 方法
				if (finalClass || finalMethod) {
					// 如果返回来行是一个接口，则直接用 jdk 原生动态代理的方式
					if (exposedType.isInterface()) {
						if (logger.isTraceEnabled()) {
							logger.trace("Creating interface proxy for FactoryBean '" + beanName + "' of type [" +
									clazz.getName() + "] for use within another @Bean method because its " +
									(finalClass ? "implementation class" : "getObject() method") +
									" is final: Otherwise a getObject() call would not be routed to the factory.");
						}
						// 返回 jdk 动态代理对象
						return createInterfaceProxyForFactoryBean(factoryBean, exposedType, beanFactory, beanName);
					}
					else {
						// 返回类型是一个具体类，并非一个接口，则无法进行代理了，直接返回这个 factoryBean 对象了
						if (logger.isDebugEnabled()) {
							logger.debug("Unable to proxy FactoryBean '" + beanName + "' of type [" +
									clazz.getName() + "] for use within another @Bean method because its " +
									(finalClass ? "implementation class" : "getObject() method") +
									" is final: A getObject() call will NOT be routed to the factory. " +
									"Consider declaring the return type as a FactoryBean interface.");
						}
						return factoryBean;
					}
				}
			}
			catch (NoSuchMethodException ex) {
				// No getObject() method -> shouldn't happen, but as long as nobody is trying to call it...
			}

			// 默认只要是非 final 类的配置，都用 cglib 动态代理
			return createCglibProxyForFactoryBean(factoryBean, beanFactory, beanName);
		}

		/**
		 * 代理的配置类是 final 类型或者 getObject 方法是 final 类型的情况，
		 * 且这个 beanName 对应的返回类型是一个接口，则说明返回的对象起码是实现了一个接口
		 * 就用这个创建代理的方式，采用 jdk 动态代理
		 * @param factoryBean
		 * @param interfaceType
		 * @param beanFactory
		 * @param beanName
		 * @return
		 */
		private Object createInterfaceProxyForFactoryBean(Object factoryBean, Class<?> interfaceType,
				ConfigurableBeanFactory beanFactory, String beanName) {

			return Proxy.newProxyInstance(
					factoryBean.getClass().getClassLoader(), new Class<?>[] {interfaceType},
					(proxy, method, args) -> {
						// 代理 getObject 方法，改成调用 beanFactory.getBean
						if (method.getName().equals("getObject") && args == null) {
							return beanFactory.getBean(beanName);
						}
						return ReflectionUtils.invokeMethod(method, factoryBean, args);
					});
		}

		/**
		 * 默认只要代理的配置类不是 final 类型且方法 getObject 方法不是 final 类型，
		 * 则采用 cglib 动态代理的方式，创建代理对象
		 * @param factoryBean
		 * @param beanFactory
		 * @param beanName
		 * @return
		 */
		private Object createCglibProxyForFactoryBean(Object factoryBean,
				ConfigurableBeanFactory beanFactory, String beanName) {

			Enhancer enhancer = new Enhancer();
			// 设置代理类是 factoryBean 的类
			enhancer.setSuperclass(factoryBean.getClass());
			enhancer.setNamingPolicy(SpringNamingPolicy.INSTANCE);
			enhancer.setCallbackType(MethodInterceptor.class);

			// Ideally create enhanced FactoryBean proxy without constructor side effects,
			// analogous to AOP proxy creation in ObjenesisCglibAopProxy...
			Class<?> fbClass = enhancer.createClass();
			Object fbProxy = null;

			if (objenesis.isWorthTrying()) {
				try {
					fbProxy = objenesis.newInstance(fbClass, enhancer.getUseCache());
				}
				catch (ObjenesisException ex) {
					logger.debug("Unable to instantiate enhanced FactoryBean using Objenesis, " +
							"falling back to regular construction", ex);
				}
			}

			if (fbProxy == null) {
				try {
					fbProxy = ReflectionUtils.accessibleConstructor(fbClass).newInstance();
				}
				catch (Throwable ex) {
					throw new IllegalStateException("Unable to instantiate enhanced FactoryBean using Objenesis, " +
							"and regular FactoryBean instantiation via default constructor fails as well", ex);
				}
			}

			((Factory) fbProxy).setCallback(0, (MethodInterceptor) (obj, method, args, proxy) -> {
				if (method.getName().equals("getObject") && args.length == 0) {
					// 过滤 getObject 方法，改成通过 beanFactory.getBean 方法获取
					return beanFactory.getBean(beanName);
				}
				return proxy.invoke(factoryBean, args);
			});

			return fbProxy;
		}
	}

}
