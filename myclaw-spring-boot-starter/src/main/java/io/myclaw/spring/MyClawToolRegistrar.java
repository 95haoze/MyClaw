package io.myclaw.spring;

import io.myclaw.core.tool.ToolRegistry;
import io.myclaw.spring.annotation.MyClawTool;
import io.myclaw.spring.tool.MethodTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;

/**
 * 扫描 Spring 容器里所有标注了 {@link MyClawTool} 的方法，并注册成 Agent 可调用的工具。
 *
 * <p>实现 {@link SmartInitializingSingleton}，在所有单例 Bean 实例化完成之后才执行，
 * 因此既能拿到完整的 Bean 列表，又不会触发提前初始化。
 *
 * <p>注册进的是同一个 {@link ToolRegistry} 实例，而 {@code Agent} 持有的是该实例的引用，
 * 所以"先建 Agent、后注册工具"不会导致工具丢失。
 */
public class MyClawToolRegistrar implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(MyClawToolRegistrar.class);

    private final ApplicationContext applicationContext;
    private final ToolRegistry toolRegistry;

    public MyClawToolRegistrar(ApplicationContext applicationContext, ToolRegistry toolRegistry) {
        this.applicationContext = applicationContext;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public void afterSingletonsInstantiated() {
        int registered = 0;
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> type;
            try {
                type = applicationContext.getType(beanName);
            } catch (RuntimeException e) {
                continue;
            }
            if (type == null || type.getName().startsWith("org.springframework.") || !hasToolMethod(type)) {
                continue;
            }

            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (RuntimeException e) {
                log.warn("Bean `{}` 上有 @MyClawTool 方法，但无法实例化，已跳过: {}", beanName, e.toString());
                continue;
            }

            Class<?> targetClass = ClassUtils.getUserClass(bean);
            for (Method method : targetClass.getMethods()) {
                if (method.getAnnotation(MyClawTool.class) == null) {
                    continue;
                }
                try {
                    MethodTool tool = MethodTool.from(bean, method);
                    toolRegistry.register(tool);
                    registered++;
                } catch (RuntimeException e) {
                    log.warn("注册 @MyClawTool 方法 {} 失败: {}", method, e.toString());
                }
            }
        }

        if (registered > 0) {
            log.info("MyClaw 注解扫描完成：新注册 {} 个工具，当前可用工具 {}", registered, toolRegistry.names());
        } else {
            log.debug("未发现任何 @MyClawTool 方法");
        }
    }

    /** 类本身或其接口上是否存在 @MyClawTool 方法（不实例化 Bean 即可判断）。 */
    private static boolean hasToolMethod(Class<?> type) {
        for (Method method : type.getMethods()) {
            if (method.isAnnotationPresent(MyClawTool.class)) {
                return true;
            }
        }
        for (Class<?> iface : type.getInterfaces()) {
            if (hasToolMethod(iface)) {
                return true;
            }
        }
        return false;
    }
}
