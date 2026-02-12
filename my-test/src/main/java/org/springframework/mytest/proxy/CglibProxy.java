package org.springframework.mytest.proxy;

import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.mytest.bean.aop.MyCalculate;

import java.lang.reflect.Method;

/**
 * @author linzherong
 * @date 2026/2/3 12:40
 */
public class CglibProxy {

	public static MyCalculate proxyInstance(MyCalculate calculate) {
		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(calculate.getClass());
		enhancer.setCallback(new MethodInterceptor() {
			@Override
			public Object intercept(Object o, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
				System.out.println("cglib 调用");
				return method.invoke(calculate, args);
			}
		});
		return (MyCalculate) enhancer.create();
	}


}
