/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.transaction.config;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.NoRollbackRuleAttribute;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;

/**
 * {@link org.springframework.beans.factory.xml.BeanDefinitionParser
 * BeanDefinitionParser} for the {@code <tx:advice/>} tag.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Adrian Colyer
 * @author Chris Beams
 * @since 2.0
 */
class TxAdviceBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

	private static final String METHOD_ELEMENT = "method";

	private static final String METHOD_NAME_ATTRIBUTE = "name";

	private static final String ATTRIBUTES_ELEMENT = "attributes";

	private static final String TIMEOUT_ATTRIBUTE = "timeout";

	private static final String READ_ONLY_ATTRIBUTE = "read-only";

	private static final String PROPAGATION_ATTRIBUTE = "propagation";

	private static final String ISOLATION_ATTRIBUTE = "isolation";

	private static final String ROLLBACK_FOR_ATTRIBUTE = "rollback-for";

	private static final String NO_ROLLBACK_FOR_ATTRIBUTE = "no-rollback-for";

	// 当前这个解析的 xml 对应的 beanClass
	@Override
	protected Class<?> getBeanClass(Element element) {
		return TransactionInterceptor.class;
	}

	// 事务的xml配置解析
	// 总结：
	// 解析成 bd：transactionManager
	//		transactionManager.transactionAttributeSource = NameMatchTransactionAttributeSource
	//		NameMatchTransactionAttributeSource.nameMap = method 和 对应配置属性的绑定map
	@Override
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		// 给这个 bd 添加属性 transactionManager = transactionManager，或者 transaction-manager 对应的值
		builder.addPropertyReference("transactionManager", TxNamespaceHandler.getTransactionManagerName(element));

		// 获取子标签 attributes
		List<Element> txAttributes = DomUtils.getChildElementsByTagName(element, ATTRIBUTES_ELEMENT);
		// 一个 tx:advice 的子标签 attributes 只能有一个
		if (txAttributes.size() > 1) {
			parserContext.getReaderContext().error(
					"Element <attributes> is allowed at most once inside element <advice>", element);
		}
		else if (txAttributes.size() == 1) {
			// Using attributes source.
			// 获取当前这个 attributes 子标签
			Element attributeSourceElement = txAttributes.get(0);
			// 解析 attributeSourceElement，解析成 NameMatchTransactionAttributeSource 对应的 bd
			RootBeanDefinition attributeSourceDefinition = parseAttributeSource(attributeSourceElement, parserContext);
			// 将上边得到的 bd 添加到 builder 这个 bd 的属性 transactionAttributeSource 中
			// 也就是 transactionManager 这个 bd 对应的属性 transactionAttributeSource 中
			builder.addPropertyValue("transactionAttributeSource", attributeSourceDefinition);
		}
		else {
			// Assume annotations source.
			// 如果没有 attributes 属性，则添加属性 transactionAttributeSource 为 RootBeanDefinition，指向 AnnotationTransactionAttributeSource
			// 应该是解析注解
			builder.addPropertyValue("transactionAttributeSource",
					new RootBeanDefinition("org.springframework.transaction.annotation.AnnotationTransactionAttributeSource"));
		}
	}

	// 解析 <tx:attributes>
	// 最后解析得到的 bd 是 NameMatchTransactionAttributeSource 的 rootBD
	// 里边有个属性 nameMap，存放的是所有 attributes 下的 method 和 其对应的属性配置
	private RootBeanDefinition parseAttributeSource(Element attrEle, ParserContext parserContext) {
		// 获取子标签  tx:method
		List<Element> methods = DomUtils.getChildElementsByTagName(attrEle, METHOD_ELEMENT);
		// 创建一个 map，用来绑定 attributes 中 method 和对应的属性的配置
		ManagedMap<TypedStringValue, RuleBasedTransactionAttribute> transactionAttributeMap =
				new ManagedMap<>(methods.size());
		transactionAttributeMap.setSource(parserContext.extractSource(attrEle));

		// 遍历所有的 method
		for (Element methodEle : methods) {
			// 获取属性 name
			String name = methodEle.getAttribute(METHOD_NAME_ATTRIBUTE);
			// 创建 TypedStringValue，包含了对应的 name
			TypedStringValue nameHolder = new TypedStringValue(name);
			nameHolder.setSource(parserContext.extractSource(methodEle));

			// 创建一个 RuleBasedTransactionAttribute 对象
			RuleBasedTransactionAttribute attribute = new RuleBasedTransactionAttribute();
			// 传播属性
			String propagation = methodEle.getAttribute(PROPAGATION_ATTRIBUTE);
			// 隔离级别
			String isolation = methodEle.getAttribute(ISOLATION_ATTRIBUTE);
			// 超时时间
			String timeout = methodEle.getAttribute(TIMEOUT_ATTRIBUTE);
			// 是否只读
			String readOnly = methodEle.getAttribute(READ_ONLY_ATTRIBUTE);
			// 分别将配置设置到 attribute 上
			if (StringUtils.hasText(propagation)) {
				attribute.setPropagationBehaviorName(RuleBasedTransactionAttribute.PREFIX_PROPAGATION + propagation);
			}
			if (StringUtils.hasText(isolation)) {
				attribute.setIsolationLevelName(RuleBasedTransactionAttribute.PREFIX_ISOLATION + isolation);
			}
			if (StringUtils.hasText(timeout)) {
				attribute.setTimeoutString(timeout);
			}
			if (StringUtils.hasText(readOnly)) {
				attribute.setReadOnly(Boolean.parseBoolean(methodEle.getAttribute(READ_ONLY_ATTRIBUTE)));
			}

			// 回滚规则
			List<RollbackRuleAttribute> rollbackRules = new ArrayList<>(1);
			// 设置 rollback-for 属性，对应是一个异常类
			if (methodEle.hasAttribute(ROLLBACK_FOR_ATTRIBUTE)) {
				// 获取回滚规则的值
				String rollbackForValue = methodEle.getAttribute(ROLLBACK_FOR_ATTRIBUTE);
				// 根据逗号切分，添加到 rollbackRules 中
				addRollbackRuleAttributesTo(rollbackRules, rollbackForValue);
			}

			// 设置 no-rollback-for 属性，和上边一样，封装成 NoRollbackRuleAttribute，添加到 rollbackRules 中
			if (methodEle.hasAttribute(NO_ROLLBACK_FOR_ATTRIBUTE)) {
				String noRollbackForValue = methodEle.getAttribute(NO_ROLLBACK_FOR_ATTRIBUTE);
				addNoRollbackRuleAttributesTo(rollbackRules, noRollbackForValue);
			}
			// 设置回滚滚则
			attribute.setRollbackRules(rollbackRules);

			// 将这个 method 对应的 name，和以上解析出来的属性，绑定一起，添加到 map 中，map 是 transactionAttributeMap
			transactionAttributeMap.put(nameHolder, attribute);
		}

		// 创建一个 rootBD，对应的类指向 NameMatchTransactionAttributeSource.class
		RootBeanDefinition attributeSourceDefinition = new RootBeanDefinition(NameMatchTransactionAttributeSource.class);
		// 设置属性 source
		attributeSourceDefinition.setSource(parserContext.extractSource(attrEle));
		// 添加属性 nameMap，对应的值是上边封装得到的 transactionAttributeMap
		attributeSourceDefinition.getPropertyValues().add("nameMap", transactionAttributeMap);
		// 返回这个 rootBD
		return attributeSourceDefinition;
	}

	// 根据回滚规则到给定的列表中
	private void addRollbackRuleAttributesTo(List<RollbackRuleAttribute> rollbackRules, String rollbackForValue) {
		// 按逗号切分
		String[] exceptionTypeNames = StringUtils.commaDelimitedListToStringArray(rollbackForValue);
		// 遍历所有值，添加到集合中，封装成 RollbackRuleAttribute
		for (String typeName : exceptionTypeNames) {
			rollbackRules.add(new RollbackRuleAttribute(StringUtils.trimWhitespace(typeName)));
		}
	}

	// 添加 NoRollbackRuleAttribute
	private void addNoRollbackRuleAttributesTo(List<RollbackRuleAttribute> rollbackRules, String noRollbackForValue) {
		String[] exceptionTypeNames = StringUtils.commaDelimitedListToStringArray(noRollbackForValue);
		for (String typeName : exceptionTypeNames) {
			rollbackRules.add(new NoRollbackRuleAttribute(StringUtils.trimWhitespace(typeName)));
		}
	}

}
