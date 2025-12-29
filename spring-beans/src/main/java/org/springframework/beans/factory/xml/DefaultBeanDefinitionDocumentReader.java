/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.beans.factory.xml;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

/**
 * Default implementation of the {@link BeanDefinitionDocumentReader} interface that
 * reads bean definitions according to the "spring-beans" DTD and XSD format
 * (Spring's default XML bean definition format).
 *
 * <p>The structure, elements, and attribute names of the required XML document
 * are hard-coded in this class. (Of course a transform could be run if necessary
 * to produce this format). {@code <beans>} does not need to be the root
 * element of the XML document: this class will parse all bean definition elements
 * in the XML file, regardless of the actual root element.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Erik Wiersma
 * @since 18.12.2003
 */
public class DefaultBeanDefinitionDocumentReader implements BeanDefinitionDocumentReader {

	public static final String BEAN_ELEMENT = BeanDefinitionParserDelegate.BEAN_ELEMENT;

	public static final String NESTED_BEANS_ELEMENT = "beans";

	public static final String ALIAS_ELEMENT = "alias";

	public static final String NAME_ATTRIBUTE = "name";

	public static final String ALIAS_ATTRIBUTE = "alias";

	public static final String IMPORT_ELEMENT = "import";

	public static final String RESOURCE_ATTRIBUTE = "resource";

	public static final String PROFILE_ATTRIBUTE = "profile";


	protected final Log logger = LogFactory.getLog(getClass());

	// 构造函数传进来的，readerContext = XmlReaderContext
	@Nullable
	private XmlReaderContext readerContext;

	@Nullable
	private BeanDefinitionParserDelegate delegate;


	/**
	 * This implementation parses bean definitions according to the "spring-beans" XSD
	 * (or DTD, historically).
	 * <p>Opens a DOM Document; then initializes the default settings
	 * specified at the {@code <beans/>} level; then parses the contained bean definitions.
	 */
	@Override
	public void registerBeanDefinitions(Document doc, XmlReaderContext readerContext) {
		// XmlReaderContext
		this.readerContext = readerContext;
		// 解析xml doc 并注册
		doRegisterBeanDefinitions(doc.getDocumentElement());
	}

	/**
	 * Return the descriptor for the XML resource that this parser works on.
	 */
	protected final XmlReaderContext getReaderContext() {
		Assert.state(this.readerContext != null, "No XmlReaderContext available");
		return this.readerContext;
	}

	/**
	 * Invoke the {@link org.springframework.beans.factory.parsing.SourceExtractor}
	 * to pull the source metadata from the supplied {@link Element}.
	 */
	@Nullable
	protected Object extractSource(Element ele) {
		return getReaderContext().extractSource(ele);
	}


	// 根据给定的 beans 根标签，进行 bd 的注册
	/**
	 * Register each bean definition within the given root {@code <beans/>} element.
	 */
	@SuppressWarnings("deprecation")  // for Environment.acceptsProfiles(String...)
	protected void doRegisterBeanDefinitions(Element root) {
		// Any nested <beans> elements will cause recursion in this method. In
		// order to propagate and preserve <beans> default-* attributes correctly,
		// keep track of the current (parent) delegate, which may be null. Create
		// the new (child) delegate with a reference to the parent for fallback purposes,
		// then ultimately reset this.delegate back to its original (parent) reference.
		// this behavior emulates a stack of delegates without actually necessitating one.

		// 所有嵌套了 <beans> 标签的 doc 都会被递归调用，为了保存和传递 beans 标签的默认参数，需要将 delegate 作为 parent 进行关联。
		// 创建一个关联了上一个delegate委派作为parent的child delegate，parent 可以作为兜底作用。
		// 创建一个 BeanDefinitionParserDelegate，将原先的 delegate 作为parent和当前新建的关联
		BeanDefinitionParserDelegate parent = this.delegate;
		// getReaderContext = XmlReaderContext，前边已经设置过了
		// 创建一个委派delegate
		this.delegate = createDelegate(getReaderContext(), root, parent);

		// 判断是否是默认的命名空间，即 http://www.springframework.org/schema/beans 的，这里是的，会跑进去这个分支
		if (this.delegate.isDefaultNamespace(root)) {
			// 先获取属性 profile，看是否有引向其他的配置环境
			String profileSpec = root.getAttribute(PROFILE_ATTRIBUTE);
			// 如果有这个配置
			if (StringUtils.hasText(profileSpec)) {
				// 解析出所有的配置文件，这里 profile 可以设置多个
				String[] specifiedProfiles = StringUtils.tokenizeToStringArray(
						profileSpec, BeanDefinitionParserDelegate.MULTI_VALUE_ATTRIBUTE_DELIMITERS);
				// We cannot use Profiles.of(...) since profile expressions are not supported
				// in XML config. See SPR-12458 for details.
				if (!getReaderContext().getEnvironment().acceptsProfiles(specifiedProfiles)) {
					if (logger.isDebugEnabled()) {
						logger.debug("Skipped XML bean definition file due to specified profiles [" + profileSpec +
								"] not matching: " + getReaderContext().getResource());
					}
					return;
				}
			}
		}

		// 这类的 preProcessXml 和 postProcessXml 又是一个扩展点，默认都是空实现
		// 解析前置处理
		preProcessXml(root);
		// 真正解析的地方
		parseBeanDefinitions(root, this.delegate);
		// 解析后置处理
		postProcessXml(root);

		this.delegate = parent;
	}

	protected BeanDefinitionParserDelegate createDelegate(
			XmlReaderContext readerContext, Element root, @Nullable BeanDefinitionParserDelegate parentDelegate) {
		// 创建一个新的 BeanDefinitionParserDelegate，readerContext 是传进来的 XmlReaderContext
		BeanDefinitionParserDelegate delegate = new BeanDefinitionParserDelegate(readerContext);
		// 初始化
		// 设置默认的属性
		delegate.initDefaults(root, parentDelegate);
		// 返回创建的新对象
		return delegate;
	}

	// 从给定的文档中解析 “import”、“alias”、“bean”
	/**
	 * Parse the elements at the root level in the document:
	 * "import", "alias", "bean".
	 * @param root the DOM root element of the document
	 */
	protected void parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate) {
		// 判读是否属于默认的命名空间解析，即 http://www.springframework.org/schema/beans
		if (delegate.isDefaultNamespace(root)) {
			// 获取子节点
			NodeList nl = root.getChildNodes();
			for (int i = 0; i < nl.getLength(); i++) {
				Node node = nl.item(i);
				if (node instanceof Element) {
					Element ele = (Element) node;
					if (delegate.isDefaultNamespace(ele)) {
						// 解析默认元素
						// 这里其实只会解析4个标签，即：import、alias、bean、beans
						parseDefaultElement(ele, delegate);
					}
					else {
						// 解析自定义配置
						delegate.parseCustomElement(ele);
					}
				}
			}
		}
		else {
			delegate.parseCustomElement(root);
		}
	}

	/**
	 * 解析默认标签的具体实现
	 * 这里的默认命名空间的标签就是以下4种
	 * @param ele
	 * @param delegate
	 */
	private void parseDefaultElement(Element ele, BeanDefinitionParserDelegate delegate) {
		// 解析 import
		if (delegate.nodeNameEquals(ele, IMPORT_ELEMENT)) {
			// 这里其实就是读取 import 标签的 resource 属性，然后递归调用 loadBeanDefinitions，进行解析配置文件
			importBeanDefinitionResource(ele);
		}
		// 解析 alias
		else if (delegate.nodeNameEquals(ele, ALIAS_ELEMENT)) {
			// 其实就是即将别名 alias ==>> name 映射到 beanFactory.aliasMap 中
			processAliasRegistration(ele);
		}
		// 解析 bean
		else if (delegate.nodeNameEquals(ele, BEAN_ELEMENT)) {
			processBeanDefinition(ele, delegate);
		}
		// 解析 beans
		else if (delegate.nodeNameEquals(ele, NESTED_BEANS_ELEMENT)) {
			// recurse
			// 递归调用到前边的方法 doRegisterBeanDefinitions，继续循环
			doRegisterBeanDefinitions(ele);
		}
	}


	// 解析 import 标签
	/**
	 * Parse an "import" element and load the bean definitions
	 * from the given resource into the bean factory.
	 */
	protected void importBeanDefinitionResource(Element ele) {
		// 获取 import 标签中的 resource 属性，即这里对应的引用外部配置的位置
		// 一般 jdbc 的连接信息都是靠这种方式导入的
		String location = ele.getAttribute(RESOURCE_ATTRIBUTE);
		if (!StringUtils.hasText(location)) {
			getReaderContext().error("Resource location must not be empty", ele);
			return;
		}

		// Resolve system properties: e.g. "${user.dir}"
		// 这里 getReaderContext()，一般xml的话拿到的是 XmlReaderContext
		// 这里 getEnv 拿到的则是 beanFactory 的 env
		// 用 env 先对导入的文件位置进行解析，如果路径用了引用，则这里会通过变量去进行替换，得到真实的路径
		location = getReaderContext().getEnvironment().resolveRequiredPlaceholders(location);

		Set<Resource> actualResources = new LinkedHashSet<>(4);

		// 判断路径是绝对路径还是相对路径
		// Discover whether the location is an absolute or relative URI
		boolean absoluteLocation = false;
		try {
			absoluteLocation = ResourcePatternUtils.isUrl(location) || ResourceUtils.toURI(location).isAbsolute();
		}
		catch (URISyntaxException ex) {
			// cannot convert to an URI, considering the location relative
			// unless it is the well-known Spring prefix "classpath*:"
		}

		// 绝对路径的情况，即 路径是通过 classpath*: 或 classpath: 开头的文件
		// 这类用的例子是 classpath:application.xml 所以是
		// Absolute or relative?
		if (absoluteLocation) {
			try {
				// getReaderContext().getReader() = XmlBeanDefinitionReader， 前边也设置进去了
				// 这里调用 XmlBeanDefinitionReader.loadBeanDefinitions 加载bd，
				// 所以这里其实就是递归调用了，传进去了对应的绝对位置，进行解析
				int importCount = getReaderContext().getReader().loadBeanDefinitions(location, actualResources);
				if (logger.isTraceEnabled()) {
					logger.trace("Imported " + importCount + " bean definitions from URL location [" + location + "]");
				}
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error(
						"Failed to import bean definitions from URL location [" + location + "]", ele, ex);
			}
		}
		else {
			// 相对路径的情况
			// No URL -> considering resource location as relative to the current file.
			try {
				int importCount;
				// 解析location，转成 resource 对象
				Resource relativeResource = getReaderContext().getResource().createRelative(location);
				if (relativeResource.exists()) {
					// 又是递归调用，加载bd
					importCount = getReaderContext().getReader().loadBeanDefinitions(relativeResource);
					actualResources.add(relativeResource);
				}
				else {
					// 获取绝对路径
					String baseLocation = getReaderContext().getResource().getURL().toString();
					// 继续递归调用
					importCount = getReaderContext().getReader().loadBeanDefinitions(
							StringUtils.applyRelativePath(baseLocation, location), actualResources);
				}
				if (logger.isTraceEnabled()) {
					logger.trace("Imported " + importCount + " bean definitions from relative location [" + location + "]");
				}
			}
			catch (IOException ex) {
				getReaderContext().error("Failed to resolve current resource location", ele, ex);
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error(
						"Failed to import bean definitions from relative location [" + location + "]", ele, ex);
			}
		}

		// 将实际的资源对象转为数组
		Resource[] actResArray = actualResources.toArray(new Resource[0]);
		// 又是一个扩展点，触发一个import处理的通知事件，默认是空实现
		getReaderContext().fireImportProcessed(location, actResArray, extractSource(ele));
	}

	/**
	 * Process the given alias element, registering the alias with the registry.
	 */
	protected void processAliasRegistration(Element ele) {
		// name 属性
		String name = ele.getAttribute(NAME_ATTRIBUTE);
		// alias 属性
		String alias = ele.getAttribute(ALIAS_ATTRIBUTE);
		boolean valid = true;
		// 检验必填性
		if (!StringUtils.hasText(name)) {
			// 记录异常信息
			getReaderContext().error("Name must not be empty", ele);
			valid = false;
		}
		if (!StringUtils.hasText(alias)) {
			getReaderContext().error("Alias must not be empty", ele);
			valid = false;
		}
		if (valid) {
			try {
				// getReaderContext() 得到 XmlReaderContext
				// XmlReaderContext.getRegistry() 得到的是 beanFactory 对象
				// registerAlias 方法进行注册别名，所以这里实际上调用到了 DefaultListableBeanFactory 父类 SimpleAliasRegistry 中
				getReaderContext().getRegistry().registerAlias(name, alias);
			}
			catch (Exception ex) {
				getReaderContext().error("Failed to register alias '" + alias +
						"' for bean with name '" + name + "'", ele, ex);
			}
			getReaderContext().fireAliasRegistered(name, alias, extractSource(ele));
		}
	}

	/**
	 * Process the given bean element, parsing the bean definition
	 * and registering it with the registry.
	 */
	protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate) {
		// 解析bean标签，解析成一个 bdHolder，吧属性都设置进来了
		BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
		if (bdHolder != null) {
			bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);
			try {
				// Register the final decorated instance.
				BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error("Failed to register bean definition with name '" +
						bdHolder.getBeanName() + "'", ele, ex);
			}
			// Send registration event.
			// 发送组件注册事件
			getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
		}
	}


	/**
	 * Allow the XML to be extensible by processing any custom element types first,
	 * before we start to process the bean definitions. This method is a natural
	 * extension point for any other custom pre-processing of the XML.
	 * <p>The default implementation is empty. Subclasses can override this method to
	 * convert custom elements into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 * @see #getReaderContext()
	 */
	protected void preProcessXml(Element root) {
	}

	/**
	 * Allow the XML to be extensible by processing any custom element types last,
	 * after we finished processing the bean definitions. This method is a natural
	 * extension point for any other custom post-processing of the XML.
	 * <p>The default implementation is empty. Subclasses can override this method to
	 * convert custom elements into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 * @see #getReaderContext()
	 */
	protected void postProcessXml(Element root) {
	}

}
