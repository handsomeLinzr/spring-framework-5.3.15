package org.springframework.mytest.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author linzherong
 * @date 2026/2/2 20:38
 */
public class JdkProxy {

	public static Calculate proxyInstance(Calculate calculate) {
		ClassLoader classLoader = calculate.getClass().getClassLoader();
		Class<?>[] interfaces = calculate.getClass().getInterfaces();
		InvocationHandler handler = new InvocationHandler() {

			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				System.out.println("jdkProxy");
				return method.invoke(calculate, args);
			}
		};
		return (Calculate) Proxy.newProxyInstance(classLoader, interfaces, handler);
	}

}
