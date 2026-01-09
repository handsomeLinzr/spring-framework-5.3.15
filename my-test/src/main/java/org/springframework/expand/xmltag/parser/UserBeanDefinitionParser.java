package org.springframework.expand.xmltag.parser;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.context.config.AbstractPropertyLoadingBeanDefinitionParser;
import org.springframework.expand.xmltag.bean.MyUser;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import java.util.List;

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
		// 解析 username 和 password 属性
		String username = element.getAttribute("username");
		builder.addPropertyValue("username", username);
		String password = element.getAttribute("password");
		builder.addPropertyValue("password", password);

		// 解析 property 子标签
		List<Element> propertyElements = DomUtils.getChildElementsByTagName(element, "property");
		if (!propertyElements.isEmpty()) {
			for (Element propertyElement : propertyElements) {
				builder.addPropertyValue(propertyElement.getAttribute("key"), propertyElement.getAttribute("value"));
			}
		}
	}
}
