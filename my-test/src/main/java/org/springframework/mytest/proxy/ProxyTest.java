package org.springframework.mytest.proxy;

import org.springframework.mytest.bean.components.aop.MyCalculate;

/**
 * @author linzherong
 * @date 2026/2/2 20:38
 */
public class ProxyTest {

	public static void main(String[] args) {
//		System.getProperties().setProperty("jdk.proxy.ProxyGenerator.saveGeneratedFiles", "true");
//		Calculate calculate = JdkProxy.proxyInstance(new MyCalculate());
//		System.out.println(calculate.add(1, 2));


//		System.getProperties().setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "/Users/lzr/sources/spring/spring-framework-5.3.15/my-test/src/main/java");
		MyCalculate calculate = CglibProxy.proxyInstance(new MyCalculate());
		System.out.println(calculate.add(1, 2));

	}

}
