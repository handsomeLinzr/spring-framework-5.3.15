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

package org.springframework.context.event;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletionStage;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.Ordered;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.concurrent.ListenableFuture;

/**
 * {@link GenericApplicationListener} adapter that delegates the processing of
 * an event to an {@link EventListener} annotated method.
 *
 * <p>Delegates to {@link #processEvent(ApplicationEvent)} to give subclasses
 * a chance to deviate from the default. Unwraps the content of a
 * {@link PayloadApplicationEvent} if necessary to allow a method declaration
 * to define any arbitrary event type. If a condition is defined, it is
 * evaluated prior to invoking the underlying method.
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 4.2
 */
public class ApplicationListenerMethodAdapter implements GenericApplicationListener {

	// 是否需要 reactive，通过获取 org.reactivestreams.Publisher 类来判断
	private static final boolean reactiveStreamsPresent = ClassUtils.isPresent(
			"org.reactivestreams.Publisher", ApplicationListenerMethodAdapter.class.getClassLoader());


	protected final Log logger = LogFactory.getLog(getClass());

	private final String beanName;

	private final Method method;

	private final Method targetMethod;

	private final AnnotatedElementKey methodKey;

	private final List<ResolvableType> declaredEventTypes;

	@Nullable
	private final String condition;

	private final int order;

	@Nullable
	private volatile String listenerId;

	@Nullable
	private ApplicationContext applicationContext;

	@Nullable
	private EventExpressionEvaluator evaluator;


	/**
	 * Construct a new ApplicationListenerMethodAdapter.
	 * @param beanName the name of the bean to invoke the listener method on
	 * @param targetClass the target class that the method is declared on
	 * @param method the listener method to invoke
	 */
	public ApplicationListenerMethodAdapter(String beanName, Class<?> targetClass, Method method) {
		// 来源的 bean 名称
		this.beanName = beanName;
		// 监听器处理的方法
		this.method = BridgeMethodResolver.findBridgedMethod(method);
		// 一般和 method 是一样的，如果 method 是接口的方法才会不一样，执行具体类的方法，也就是能真正执行的方法
		this.targetMethod = (!Proxy.isProxyClass(targetClass) ?
				AopUtils.getMostSpecificMethod(method, targetClass) : this.method);
		// AnnotatedElementKey 对象，targetClass 是 bean 对应的类型
		this.methodKey = new AnnotatedElementKey(this.targetMethod, targetClass);

		// 获取方法上的注解
		EventListener ann = AnnotatedElementUtils.findMergedAnnotation(this.targetMethod, EventListener.class);
		// 通过 method，解析得到对应监听的事件类型
		// 其实这里是通过获取 method 的参数，参数的类型就是监听的事件类型，参数只能是 1 个
		this.declaredEventTypes = resolveDeclaredEventTypes(method, ann);
		// condition 设置为注解的 condition 设置
		this.condition = (ann != null ? ann.condition() : null);
		this.order = resolveOrder(this.targetMethod);
		String id = (ann != null ? ann.id() : "");
		this.listenerId = (!id.isEmpty() ? id : null);
	}

	private static List<ResolvableType> resolveDeclaredEventTypes(Method method, @Nullable EventListener ann) {
		// 获取参数个数
		int count = method.getParameterCount();
		// 如果大于 1，则异常
		if (count > 1) {
			throw new IllegalStateException(
					"Maximum one parameter is allowed for event listener method: " + method);
		}

		if (ann != null) {
			// 判断如果有注解，获取注解的属性 classes，即获取到监听的事件类型
			Class<?>[] classes = ann.classes();
			if (classes.length > 0) {
				List<ResolvableType> types = new ArrayList<>(classes.length);
				for (Class<?> eventType : classes) {
					// 都加到 types 中
					types.add(ResolvableType.forClass(eventType));
				}
				// 返回对应的类型
				return types;
			}
		}

		// 走到这里，如果注解的 classes 没有指定监听的事件类型，方法也没有参数，则无法知道该监听器监听的事件类型
		// 则需要抛出异常
		if (count == 0) {
			throw new IllegalStateException(
					"Event parameter is mandatory for event listener method: " + method);
		}
		// ResolvableType.forMethodParameter(method, 0) 获取 method 的第一个参数类型
		// 如果 ann.classes() 不设置监听的事件，则通过第一个参数获取到对应的监听的事件类型
		return Collections.singletonList(ResolvableType.forMethodParameter(method, 0));
	}

	private static int resolveOrder(Method method) {
		Order ann = AnnotatedElementUtils.findMergedAnnotation(method, Order.class);
		return (ann != null ? ann.value() : Ordered.LOWEST_PRECEDENCE);
	}


	/**
	 * Initialize this instance.
	 */
	void init(ApplicationContext applicationContext, @Nullable EventExpressionEvaluator evaluator) {
		// 初始化方法，设置对应的 applicationContext 和 evaluator
		this.applicationContext = applicationContext;
		this.evaluator = evaluator;
	}


	// 调用监听器的处理，处理事件，会调用到这里
	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		// 处理监听事件
		processEvent(event);
	}

	// 判断是否支持当前事件类型
	@Override
	public boolean supportsEventType(ResolvableType eventType) {
		// 遍历当前的监听器的所有监听的类型
		for (ResolvableType declaredEventType : this.declaredEventTypes) {
			// 如果能和对应传进来的事件类型匹配上，返回 true
			if (declaredEventType.isAssignableFrom(eventType)) {
				return true;
			}
			// 判断如果当前传进来的事件类型属于 PayloadApplicationEvent，则表明当前发布的事件，不是 ApplicationEvent
			if (PayloadApplicationEvent.class.isAssignableFrom(eventType.toClass())) {
				// 将 eventType 转为 PayloadApplicationEvent 的事件类型，获取对应设置的事件类型
				ResolvableType payloadType = eventType.as(PayloadApplicationEvent.class).getGeneric();
				// 判断如果能匹配上，返回 true
				if (declaredEventType.isAssignableFrom(payloadType)) {
					return true;
				}
			}
		}
		// 返回返回 hasUnresolvableGenerics
		return eventType.hasUnresolvableGenerics();
	}

	// 事件源类型匹配，默认返回 true
	@Override
	public boolean supportsSourceType(@Nullable Class<?> sourceType) {
		return true;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public String getListenerId() {
		String id = this.listenerId;
		if (id == null) {
			id = getDefaultListenerId();
			this.listenerId = id;
		}
		return id;
	}

	/**
	 * Determine the default id for the target listener, to be applied in case of
	 * no {@link EventListener#id() annotation-specified id value}.
	 * <p>The default implementation builds a method name with parameter types.
	 * @since 5.3.5
	 * @see #getListenerId()
	 */
	protected String getDefaultListenerId() {
		Method method = getTargetMethod();
		StringJoiner sj = new StringJoiner(",", "(", ")");
		for (Class<?> paramType : method.getParameterTypes()) {
			sj.add(paramType.getName());
		}
		return ClassUtils.getQualifiedMethodName(method) + sj.toString();
	}


	/**
	 * Process the specified {@link ApplicationEvent}, checking if the condition
	 * matches and handling a non-null result, if any.
	 */
	public void processEvent(ApplicationEvent event) {
		// 参数处理，将 event 和当前监听器的监听事件类型做比较，得到对应的类型
		// 并将事件根据类型进行处理成参数数组
		Object[] args = resolveArguments(event);
		// condition 条件处理，默认没有设置 condition 则返回 true
		if (shouldHandle(event, args)) {
			// 调用 doInvoke 传入参数，返回 result 结果
			// 这里的 doInvoke 则调用对应实际的 method 方法反射调用处理逻辑
			Object result = doInvoke(args);
			if (result != null) {
				// 如果结果不为空，调用 handleResult 处理
				handleResult(result);
			}
			else {
				// result 为空，打印日志
				logger.trace("No result object given - no result to handle");
			}
		}
	}

	// 处理方法的参数，使用对应的 ApplicationEvent
	/**
	 * Resolve the method arguments to use for the specified {@link ApplicationEvent}.
	 * <p>These arguments will be used to invoke the method handled by this instance.
	 * Can return {@code null} to indicate that no suitable arguments could be resolved
	 * and therefore the method should not be invoked at all for the specified event.
	 */
	@Nullable
	protected Object[] resolveArguments(ApplicationEvent event) {
		// 获取当前监听器的监听类型中，能匹配上 event 事件的类型
		ResolvableType declaredEventType = getResolvableType(event);
		// 如果是 null，则没有能匹配上的，直接返回 null 不执行
		if (declaredEventType == null) {
			return null;
		}
		// 如果方法方法的参数个数是 0，则返回一个空数组即可
		if (this.method.getParameterCount() == 0) {
			return new Object[0];
		}
		// 获取对应匹配上的类型
		Class<?> declaredEventClass = declaredEventType.toClass();
		// 判断如果不是 ApplicationEvent 类型
		if (!ApplicationEvent.class.isAssignableFrom(declaredEventClass) &&
				// 且是当前的事件属于 PayloadApplicationEvent
				event instanceof PayloadApplicationEvent) {
			// 强转 event，后驱对应的 payload，即事件源
			Object payload = ((PayloadApplicationEvent<?>) event).getPayload();
			// 判断如果 declaredEventClass 能匹配上 payload 事件源
			if (declaredEventClass.isInstance(payload)) {
				// 则返回对应的数组
				return new Object[] {payload};
			}
		}
		// 否则，也就是对应的事件类型属于 ApplicationEvent，直接返回对应的这个事件的数组
		return new Object[] {event};
	}

	protected void handleResult(Object result) {
		// 如果是 reactive
		if (reactiveStreamsPresent && new ReactiveResultHandler().subscribeToPublisher(result)) {
			if (logger.isTraceEnabled()) {
				logger.trace("Adapted to reactive result: " + result);
			}
		}
		else if (result instanceof CompletionStage) {
			((CompletionStage<?>) result).whenComplete((event, ex) -> {
				if (ex != null) {
					handleAsyncError(ex);
				}
				else if (event != null) {
					publishEvent(event);
				}
			});
		}
		else if (result instanceof ListenableFuture) {
			((ListenableFuture<?>) result).addCallback(this::publishEvents, this::handleAsyncError);
		}
		else {
			// 调用 publishEvents 往下处理
			publishEvents(result);
		}
	}

	private void publishEvents(Object result) {
		if (result.getClass().isArray()) {
			Object[] events = ObjectUtils.toObjectArray(result);
			for (Object event : events) {
				publishEvent(event);
			}
		}
		else if (result instanceof Collection<?>) {
			Collection<?> events = (Collection<?>) result;
			for (Object event : events) {
				publishEvent(event);
			}
		}
		else {
			publishEvent(result);
		}
	}

	private void publishEvent(@Nullable Object event) {
		if (event != null) {
			Assert.notNull(this.applicationContext, "ApplicationContext must not be null");
			this.applicationContext.publishEvent(event);
		}
	}

	protected void handleAsyncError(Throwable t) {
		logger.error("Unexpected error occurred in asynchronous listener", t);
	}

	private boolean shouldHandle(ApplicationEvent event, @Nullable Object[] args) {
		// 参数为空，返回 false
		if (args == null) {
			return false;
		}
		// 获取对应的 condition
		String condition = getCondition();
		// 如果有设置了 condition
		if (StringUtils.hasText(condition)) {
			Assert.notNull(this.evaluator, "EventExpressionEvaluator must not be null");
			// 调用解析器处理条件
			return this.evaluator.condition(
					condition, event, this.targetMethod, this.methodKey, args, this.applicationContext);
		}
		// 否则没有设置 condition 返回 true
		return true;
	}

	/**
	 * Invoke the event listener method with the given argument values.
	 */
	@Nullable
	protected Object doInvoke(Object... args) {
		// 获取监听器处理的方法，所在的 method 的 bean 对象
		Object bean = getTargetBean();
		// Detect package-protected NullBean instance through equals(null) check
		// 没有则直接返回 null
		if (bean.equals(null)) {
			return null;
		}

		// 强制访问权限
		ReflectionUtils.makeAccessible(this.method);
		try {
			// 通过反射，调用对应的 bean 的 method 方法，并且将事件参数 args 传入
			// 然后就调用到了具体的方法
			return this.method.invoke(bean, args);
		}
		catch (IllegalArgumentException ex) {
			assertTargetBean(this.method, bean, args);
			throw new IllegalStateException(getInvocationErrorMessage(bean, ex.getMessage(), args), ex);
		}
		catch (IllegalAccessException ex) {
			throw new IllegalStateException(getInvocationErrorMessage(bean, ex.getMessage(), args), ex);
		}
		catch (InvocationTargetException ex) {
			// Throw underlying exception
			Throwable targetException = ex.getTargetException();
			if (targetException instanceof RuntimeException) {
				throw (RuntimeException) targetException;
			}
			else {
				String msg = getInvocationErrorMessage(bean, "Failed to invoke event listener method", args);
				throw new UndeclaredThrowableException(targetException, msg);
			}
		}
	}

	// 获取当前监听器中，beanName 对应的 bean
	/**
	 * Return the target bean instance to use.
	 */
	protected Object getTargetBean() {
		Assert.notNull(this.applicationContext, "ApplicationContext must no be null");
		return this.applicationContext.getBean(this.beanName);
	}

	/**
	 * Return the target listener method.
	 * @since 5.3
	 */
	protected Method getTargetMethod() {
		return this.targetMethod;
	}

	/**
	 * Return the condition to use.
	 * <p>Matches the {@code condition} attribute of the {@link EventListener}
	 * annotation or any matching attribute on a composed annotation that
	 * is meta-annotated with {@code @EventListener}.
	 */
	@Nullable
	protected String getCondition() {
		return this.condition;
	}

	/**
	 * Add additional details such as the bean type and method signature to
	 * the given error message.
	 * @param message error message to append the HandlerMethod details to
	 */
	protected String getDetailedErrorMessage(Object bean, String message) {
		StringBuilder sb = new StringBuilder(message).append('\n');
		sb.append("HandlerMethod details: \n");
		sb.append("Bean [").append(bean.getClass().getName()).append("]\n");
		sb.append("Method [").append(this.method.toGenericString()).append("]\n");
		return sb.toString();
	}

	/**
	 * Assert that the target bean class is an instance of the class where the given
	 * method is declared. In some cases the actual bean instance at event-
	 * processing time may be a JDK dynamic proxy (lazy initialization, prototype
	 * beans, and others). Event listener beans that require proxying should prefer
	 * class-based proxy mechanisms.
	 */
	private void assertTargetBean(Method method, Object targetBean, Object[] args) {
		Class<?> methodDeclaringClass = method.getDeclaringClass();
		Class<?> targetBeanClass = targetBean.getClass();
		if (!methodDeclaringClass.isAssignableFrom(targetBeanClass)) {
			String msg = "The event listener method class '" + methodDeclaringClass.getName() +
					"' is not an instance of the actual bean class '" +
					targetBeanClass.getName() + "'. If the bean requires proxying " +
					"(e.g. due to @Transactional), please use class-based proxying.";
			throw new IllegalStateException(getInvocationErrorMessage(targetBean, msg, args));
		}
	}

	private String getInvocationErrorMessage(Object bean, String message, Object[] resolvedArgs) {
		StringBuilder sb = new StringBuilder(getDetailedErrorMessage(bean, message));
		sb.append("Resolved arguments: \n");
		for (int i = 0; i < resolvedArgs.length; i++) {
			sb.append('[').append(i).append("] ");
			if (resolvedArgs[i] == null) {
				sb.append("[null] \n");
			}
			else {
				sb.append("[type=").append(resolvedArgs[i].getClass().getName()).append("] ");
				sb.append("[value=").append(resolvedArgs[i]).append("]\n");
			}
		}
		return sb.toString();
	}

	// 通过给定的 event，遍历当前监听器给定的监听事件类型，获取到能匹配上的事件类型
	// 并返回对应的事件类型
	@Nullable
	private ResolvableType getResolvableType(ApplicationEvent event) {
		ResolvableType payloadType = null;
		// 如果事件属于 PayloadApplicationEvent
		// 则说明发布的事件，不是属于 ApplicationEvent，只是普通的对象
		if (event instanceof PayloadApplicationEvent) {
			// 强转
			PayloadApplicationEvent<?> payloadEvent = (PayloadApplicationEvent<?>) event;
			// 获取事件类型
			ResolvableType eventType = payloadEvent.getResolvableType();
			if (eventType != null) {
				// 获取对应的事件类型
				payloadType = eventType.as(PayloadApplicationEvent.class).getGeneric();
			}
		}
		// 遍历当前监听器所监听的事件类型
		for (ResolvableType declaredEventType : this.declaredEventTypes) {
			// 获取事件的类型
			Class<?> eventClass = declaredEventType.toClass();
			// 判断如果对应的事件类型不属于 ApplicationEvent
			if (!ApplicationEvent.class.isAssignableFrom(eventClass) &&
					// 且 payloadType 不为空，说明当前被发布的事件，也不是 ApplicationEvent 类型
					// 且 payloadType 和当前监听器对应的类型是能匹配上的
					payloadType != null && declaredEventType.isAssignableFrom(payloadType)) {
				// 则返回这个事件类型
				return declaredEventType;
			}
			// 否则判断如果当前的事件类型，能匹配上当前的事件
			// 则返回当前监听器的事件类型
			if (eventClass.isInstance(event)) {
				return declaredEventType;
			}
		}
		// 否则，监听器的所有监听的事件类型都遍历过了，都没法匹配上，则返回 null
		return null;
	}


	@Override
	public String toString() {
		return this.method.toGenericString();
	}


	private class ReactiveResultHandler {

		public boolean subscribeToPublisher(Object result) {
			ReactiveAdapter adapter = ReactiveAdapterRegistry.getSharedInstance().getAdapter(result.getClass());
			if (adapter != null) {
				adapter.toPublisher(result).subscribe(new EventPublicationSubscriber());
				return true;
			}
			return false;
		}
	}


	private class EventPublicationSubscriber implements Subscriber<Object> {

		@Override
		public void onSubscribe(Subscription s) {
			s.request(Integer.MAX_VALUE);
		}

		@Override
		public void onNext(Object o) {
			publishEvents(o);
		}

		@Override
		public void onError(Throwable t) {
			handleAsyncError(t);
		}

		@Override
		public void onComplete() {
		}
	}

}
