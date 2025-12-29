package org.springframework.expand;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.context.config.AbstractPropertyLoadingBeanDefinitionParser;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

/**
 * @author linzherong
 * @date 2025/12/28 22:27
 */
public class UserBeanDefinitionParser extends AbstractPropertyLoadingBeanDefinitionParser {

	@Override
	protected Class<?> getBeanClass(Element element) {
		return MyUser.class;
	}

	@Override
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		String username = element.getAttribute("username");
		builder.addPropertyValue("username", username);
		String password = element.getAttribute("password");
		builder.addPropertyValue("password", password);
	}
}
