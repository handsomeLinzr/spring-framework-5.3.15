package org.springframework.mytest.lookup;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author linzherong
 * @date 2026/3/9 18:34
 */
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class LookupBean2 {

	public void lookup2() {
		System.out.println(this);
	}

}
