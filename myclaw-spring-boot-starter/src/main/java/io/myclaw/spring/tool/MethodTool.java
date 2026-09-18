package io.myclaw.spring.tool;

import io.myclaw.core.json.Json;
import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import io.myclaw.spring.annotation.MyClawParam;
import io.myclaw.spring.annotation.MyClawTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 把一个普通的 Java 方法适配成 {@link Tool}。
 *
 * <p>承担三件事：
 * <ol>
 *   <li>从方法签名 + {@link MyClawParam} 生成 JSON Schema</li>
 *   <li>把模型给的 JSON 参数按类型转换成 Java 实参（含必填校验与友好报错）</li>
 *   <li>反射调用并把返回值转成回填给模型的字符串</li>
 * </ol>
 */
public final class MethodTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(MethodTool.class);

    private final Object target;
    private final Method method;
    private final ToolDefinition definition;
    private final List<ParamSpec> parameters;

    private MethodTool(Object target, Method method, ToolDefinition definition, List<ParamSpec> parameters) {
        this.target = target;
        this.method = method;
        this.definition = definition;
        this.parameters = parameters;
    }

    /** 解析一个标注了 {@link MyClawTool} 的方法。 */
    public static MethodTool from(Object target, Method method) {
        MyClawTool annotation = method.getAnnotation(MyClawTool.class);
        if (annotation == null) {
            throw new IllegalArgumentException("方法 " + method + " 上没有 @MyClawTool 注解");
        }
        method.setAccessible(true);

        String name = annotation.name().isBlank() ? method.getName() : annotation.name().strip();
        String description = annotation.description().isBlank() ? method.getName() : annotation.description().strip();
        if (annotation.description().isBlank()) {
            log.warn("@MyClawTool 方法 {} 没有填写 description，模型的调用准确率会明显下降，建议补上", method);
        }

        List<ParamSpec> parameters = describeParameters(method);

        JsonSchema.Builder schema = JsonSchema.object();
        for (ParamSpec parameter : parameters) {
            Class<?> type = parameter.type();
            if (type.isEnum()) {
                List<String> values = Arrays.stream(type.getEnumConstants())
                        .map(constant -> ((Enum<?>) constant).name())
                        .toList();
                schema.enumOf(parameter.name(), parameter.description(), values, parameter.required());
            } else {
                switch (parameter.kind()) {
                    case INTEGER, LONG -> schema.integer(parameter.name(), parameter.description(), parameter.required());
                    case NUMBER -> schema.number(parameter.name(), parameter.description(), parameter.required());
                    case BOOLEAN -> schema.bool(parameter.name(), parameter.description(), parameter.required());
                    case STRING -> schema.string(parameter.name(), parameter.description(), parameter.required());
                }
            }
        }

        ToolDefinition definition = ToolDefinition.builder(name)
                .description(description)
                .parameters(schema.build())
                .build();

        return new MethodTool(target, method, definition, parameters);
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public String call(JsonNode arguments, ToolContext context) throws Exception {
        Object[] args = new Object[parameters.size()];
        for (int i = 0; i < parameters.size(); i++) {
            args[i] = parameters.get(i).read(arguments);
        }
        try {
            return stringify(method.invoke(target, args));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    /** 被包装的目标 Bean 与方法，供调试使用。 */
    public Method method() {
        return method;
    }

    private static List<ParamSpec> describeParameters(Method method) {
        Parameter[] declared = method.getParameters();
        List<ParamSpec> specs = new ArrayList<>(declared.length);
        for (Parameter parameter : declared) {
            MyClawParam annotation = parameter.getAnnotation(MyClawParam.class);
            String name = annotation != null && !annotation.value().isBlank()
                    ? annotation.value().strip()
                    : parameter.getName();
            if (name.startsWith("arg")) {
                log.warn("方法 {} 的参数 {} 拿不到真实名字（缺少 -parameters 编译开关），请在 @MyClawParam 中显式指定",
                        method.getName(), name);
            }
            String description = annotation != null ? annotation.description() : "";
            boolean required = annotation == null || annotation.required();
            specs.add(new ParamSpec(name, description, required, kindOf(parameter.getType()), parameter.getType()));
        }
        return List.copyOf(specs);
    }

    private static Kind kindOf(Class<?> type) {
        if (type == int.class || type == Integer.class
                || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class) {
            return Kind.INTEGER;
        }
        if (type == long.class || type == Long.class) {
            return Kind.LONG;
        }
        if (type == double.class || type == Double.class
                || type == float.class || type == Float.class
                || type == BigDecimal.class) {
            return Kind.NUMBER;
        }
        if (type == boolean.class || type == Boolean.class) {
            return Kind.BOOLEAN;
        }
        return Kind.STRING;
    }

    private static String stringify(Object result) {
        if (result == null) {
            return "";
        }
        if (result instanceof CharSequence || result instanceof Number
                || result instanceof Boolean || result instanceof Character) {
            return String.valueOf(result);
        }
        if (result instanceof Path path) {
            return path.toString();
        }
        return Json.write(result);
    }

    /** 参数类型分类，决定生成的 Schema 类型与 JSON -> Java 的转换方式。 */
    private enum Kind {
        STRING, INTEGER, LONG, NUMBER, BOOLEAN
    }

    /** 一个方法参数的完整描述。 */
    private record ParamSpec(String name, String description, boolean required, Kind kind, Class<?> type) {

        /** 从模型给的 JSON 里取出并转换成本参数的值。 */
        Object read(JsonNode arguments) {
            JsonNode value = arguments == null ? null : arguments.path(name);
            if (value == null || value.isMissingNode() || value.isNull()) {
                if (required) {
                    throw new IllegalArgumentException("缺少必填参数 `" + name + "`");
                }
                return zeroValue();
            }
            return switch (kind) {
                case STRING -> readString(value);
                case INTEGER -> value.isNumber() ? value.asInt() : Integer.parseInt(value.asString().strip());
                case LONG -> value.isNumber() ? value.asLong() : Long.parseLong(value.asString().strip());
                case NUMBER -> value.isNumber() ? value.asDouble() : Double.parseDouble(value.asString().strip());
                case BOOLEAN -> value.isBoolean() ? value.asBoolean() : Boolean.parseBoolean(value.asString().strip());
            };
        }

        private Object readString(JsonNode value) {
            String text = value.isString() ? value.asString() : Json.write(value);
            if (type == String.class || type == CharSequence.class || type == Object.class) {
                return text;
            }
            if (type == char.class || type == Character.class) {
                if (text.isEmpty()) {
                    throw new IllegalArgumentException("参数 `" + name + "` 需要单个字符");
                }
                return text.charAt(0);
            }
            if (type == Path.class) {
                return Path.of(text);
            }
            if (type.isEnum()) {
                for (Object constant : type.getEnumConstants()) {
                    if (((Enum<?>) constant).name().equalsIgnoreCase(text.strip())) {
                        return constant;
                    }
                }
                String allowed = Arrays.stream(type.getEnumConstants())
                        .map(constant -> ((Enum<?>) constant).name())
                        .collect(Collectors.joining(", "));
                throw new IllegalArgumentException(
                        "参数 `" + name + "` 的值 `" + text + "` 非法，可选值为: " + allowed);
            }
            return Json.mapper().convertValue(value, type);
        }

        /** 可选参数缺失时传给方法的默认值。 */
        private Object zeroValue() {
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == int.class) {
                return 0;
            }
            if (type == long.class) {
                return 0L;
            }
            if (type == double.class) {
                return 0d;
            }
            if (type == float.class) {
                return 0f;
            }
            if (type == short.class) {
                return (short) 0;
            }
            if (type == byte.class) {
                return (byte) 0;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == char.class) {
                return '\0';
            }
            return null;
        }
    }
}
