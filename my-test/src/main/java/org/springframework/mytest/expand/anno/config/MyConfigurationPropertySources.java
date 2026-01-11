package org.springframework.mytest.expand.anno.config;

import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.stereotype.Component;

/**
 * @author linzherong
 * @date 2026/1/11 01:43
 */
@Component
@PropertySources({
		@PropertySource("jdbc1.properties"),
		@PropertySource("jdbc2.properties")
})
public class MyConfigurationPropertySources {
}
