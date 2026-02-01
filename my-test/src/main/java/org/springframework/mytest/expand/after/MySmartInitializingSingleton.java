package org.springframework.mytest.expand.after;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * @author linzherong
 * @date 2026/1/24 14:06
 */
//@Component
public class MySmartInitializingSingleton implements SmartInitializingSingleton {

	@Override
	public void afterSingletonsInstantiated() {
		System.out.println("===========所有单例 bean 都处理完了==============");
	}

}
