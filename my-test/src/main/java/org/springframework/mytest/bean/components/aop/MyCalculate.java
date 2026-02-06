package org.springframework.mytest.bean.components.aop;

import org.springframework.mytest.proxy.Calculate;
import org.springframework.stereotype.Component;

/**
 * @author linzherong
 * @date 2026/2/2 20:42
 */
@Component
public class MyCalculate {
	public int add(int a, int b) {
		return a+b;
	}

	public int subtract(int a, int b) {
		return a-b;
	}

	public int multiply(int a, int b) {
		return a*b;
	}

	public int divide(int a, int b) {
		return a/b;
	}
}
