package org.springframework.expand;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

/**
 * @author linzherong
 * @date 2025/12/28 22:26
 */
public class MyUserNamespaceHandler extends NamespaceHandlerSupport {
	@Override
	public void init() {
		registerBeanDefinitionParser("user", new UserBeanDefinitionParser());
	}
}
