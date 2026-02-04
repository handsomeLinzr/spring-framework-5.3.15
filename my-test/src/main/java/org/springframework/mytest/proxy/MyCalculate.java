package org.springframework.mytest.proxy;

/**
 * @author linzherong
 * @date 2026/2/2 20:42
 */
public class MyCalculate implements Calculate{
	@Override
	public int add(int a, int b) {
		return a+b;
	}

	@Override
	public int subtract(int a, int b) {
		return a-b;
	}

	@Override
	public int multiply(int a, int b) {
		return a*b;
	}

	@Override
	public int divide(int a, int b) {
		return a/b;
	}
}
