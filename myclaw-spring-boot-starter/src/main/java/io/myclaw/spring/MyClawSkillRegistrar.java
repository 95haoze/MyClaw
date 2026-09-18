package io.myclaw.spring;

import io.myclaw.core.skill.SimpleSkill;
import io.myclaw.core.skill.Skill;
import io.myclaw.core.skill.SkillRegistry;
import io.myclaw.spring.annotation.MyClawSkill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** 收集 Skill Bean 和带 {@link MyClawSkill} 的方法并注册。 */
public final class MyClawSkillRegistrar implements SmartInitializingSingleton {
    private static final Logger log = LoggerFactory.getLogger(MyClawSkillRegistrar.class);
    private final ApplicationContext applicationContext;
    private final SkillRegistry registry;

    public MyClawSkillRegistrar(ApplicationContext applicationContext, SkillRegistry registry) {
        this.applicationContext = applicationContext;
        this.registry = registry;
    }

    @Override
    public void afterSingletonsInstantiated() {
        applicationContext.getBeansOfType(Skill.class).values().forEach(registry::register);
        int annotated = 0;
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Object bean;
            try { bean = applicationContext.getBean(beanName); }
            catch (RuntimeException e) { continue; }
            for (Method method : ClassUtils.getUserClass(bean).getMethods()) {
                MyClawSkill annotation = method.getAnnotation(MyClawSkill.class);
                if (annotation == null) continue;
                try {
                    validate(method);
                    String instructions = (String) method.invoke(bean);
                    String name = annotation.name().isBlank() ? method.getName() : annotation.name().strip();
                    registry.register(new SimpleSkill(name, annotation.description(), instructions));
                    annotated++;
                } catch (ReflectiveOperationException | RuntimeException e) {
                    log.warn("注册 @MyClawSkill 方法 {} 失败: {}", method, e.getMessage());
                }
            }
        }
        log.info("MyClaw 技能注册完成：{} 个（其中注解技能 {} 个）", registry.size(), annotated);
    }

    private static void validate(Method method) {
        if (!Modifier.isPublic(method.getModifiers()) || method.getParameterCount() != 0
                || method.getReturnType() != String.class) {
            throw new IllegalArgumentException("@MyClawSkill 方法必须是 public、无参并返回 String");
        }
    }
}
