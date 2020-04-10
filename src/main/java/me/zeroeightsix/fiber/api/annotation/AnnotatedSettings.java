package me.zeroeightsix.fiber.api.annotation;

import me.zeroeightsix.fiber.NodeOperations;
import me.zeroeightsix.fiber.api.annotation.convention.SettingNamingConvention;
import me.zeroeightsix.fiber.api.annotation.exception.MalformedFieldException;
import me.zeroeightsix.fiber.impl.annotation.convention.NoNamingConvention;
import me.zeroeightsix.fiber.api.builder.ConfigAggregateBuilder;
import me.zeroeightsix.fiber.api.builder.ConfigTreeBuilder;
import me.zeroeightsix.fiber.api.builder.ConfigLeafBuilder;
import me.zeroeightsix.fiber.api.builder.constraint.AbstractConstraintsBuilder;
import me.zeroeightsix.fiber.api.exception.FiberException;
import me.zeroeightsix.fiber.impl.annotation.magic.TypeMagic;
import me.zeroeightsix.fiber.tree.ConfigGroupImpl;

import javax.annotation.Nonnull;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AnnotatedSettings {

    public static ConfigGroupImpl asNode(Object pojo) throws FiberException {
        ConfigTreeBuilder builder = new ConfigTreeBuilder();
        applyToNode(builder, pojo);
        return builder.build();
    }

    public static <P> void applyToNode(ConfigTreeBuilder mergeTo, P pojo) throws FiberException {
        @SuppressWarnings("unchecked")
        Class<P> pojoClass = (Class<P>) pojo.getClass();

        boolean onlyAnnotated;
        SettingNamingConvention convention;

        if (pojoClass.isAnnotationPresent(Settings.class)) {
            Settings settingsAnnotation = pojoClass.getAnnotation(Settings.class);
            onlyAnnotated = settingsAnnotation.onlyAnnotated();
            convention = createConvention(settingsAnnotation.namingConvention());
        } else { // Assume defaults
            onlyAnnotated = false;
            convention = new NoNamingConvention();
        }

        NodeOperations.mergeTo(constructNode(pojoClass, pojo, onlyAnnotated, convention), mergeTo);
    }

    private static <P> ConfigTreeBuilder constructNode(Class<P> pojoClass, P pojo, boolean onlyAnnotated, SettingNamingConvention convention) throws FiberException {
        ConfigTreeBuilder node = new ConfigTreeBuilder();

        List<Member> defaultEmpty = new ArrayList<>();
        Map<String, List<Member>> listenerMap = findListeners(pojoClass);

        for (Field field : pojoClass.getDeclaredFields()) {
            if (field.isSynthetic() || !isIncluded(field, onlyAnnotated)) continue;
            checkViolation(field);
            String name = findName(field, convention);
            if (field.isAnnotationPresent(Setting.Group.class)) {
                ConfigTreeBuilder sub = node.fork(name);
                try {
                    field.setAccessible(true);
                    AnnotatedSettings.applyToNode(sub, field.get(pojo));
                    sub.build();
                } catch (IllegalAccessException e) {
                    throw new FiberException("Couldn't fork and apply sub-node", e);
                }
            } else {
                fieldToItem(node, field, pojo, name, listenerMap.getOrDefault(name, defaultEmpty));
            }
        }

        return node;
    }

    private static Map<String, List<Member>> findListeners(Class<?> pojoClass) {
        return Stream.concat(Arrays.stream(pojoClass.getDeclaredFields()), Arrays.stream(pojoClass.getDeclaredMethods()))
                .filter(accessibleObject -> accessibleObject.isAnnotationPresent(Listener.class))
                .collect(Collectors.groupingBy(accessibleObject -> ((AccessibleObject) accessibleObject).getAnnotation(Listener.class).value()));
    }

    private static boolean isIncluded(Field field, boolean onlyAnnotated) {
        if (isIgnored(field)) return false;
        return !onlyAnnotated || field.isAnnotationPresent(Setting.class);
    }

    private static boolean isIgnored(Field field) {
        return getSettingAnnotation(field).map(Setting::ignore).orElse(false) || Modifier.isTransient(field.getModifiers());
    }

    private static void checkViolation(Field field) throws FiberException {
        if (Modifier.isFinal(field.getModifiers())) throw new FiberException("Field '" + field.getName() + "' can not be final");
    }

    private static Optional<Setting> getSettingAnnotation(Field field) {
        return field.isAnnotationPresent(Setting.class) ? Optional.of(field.getAnnotation(Setting.class)) : Optional.empty();
    }

    private static <T> void fieldToItem(ConfigTreeBuilder node, Field field, Object pojo, String name, List<Member> listeners) throws FiberException {
        Class<T> type = getSettingTypeFromField(field);

        ConfigLeafBuilder<T> builder = createConfigLeafBuilder(node, name, type, field)
                .withComment(findComment(field))
                .withDefaultValue(findDefaultValue(field, pojo))
                .withFinality(getSettingAnnotation(field).map(Setting::constant).orElse(false));

        constrain(builder.beginConstraints(), field.getAnnotatedType()).finishConstraints();

        for (Member listener : listeners) {
            BiConsumer<T, T> consumer = constructListener(listener, pojo, type);
            if (consumer == null) continue;
            builder.withListener(consumer);
        }

        builder.withListener((t, newValue) -> {
            try {
                field.setAccessible(true);
                field.set(pojo, newValue);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        });

        builder.build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nonnull
    private static <N extends ConfigTreeBuilder, T, E> ConfigLeafBuilder<T> createConfigLeafBuilder(N parent, String name, Class<T> type, Field field) {
        AnnotatedType annotatedType = field.getAnnotatedType();
        if (ConfigAggregateBuilder.isAggregate(type)) {
            if (Collection.class.isAssignableFrom(type)) {
                if (annotatedType instanceof AnnotatedParameterizedType) {
                    AnnotatedType[] typeArgs = ((AnnotatedParameterizedType) annotatedType).getAnnotatedActualTypeArguments();
                    if (typeArgs.length == 1) { // assume that the only type parameter is the Collection type parameter
                        AnnotatedType typeArg = typeArgs[0];
                        Class<E> componentType = (Class<E>) TypeMagic.classForType(typeArg.getType());
                        if (componentType != null) {
                            // coerce to a collection class and configure as such
                            ConfigAggregateBuilder<T, E> aggregate = ConfigAggregateBuilder.create(parent, name, (Class) type, componentType);
                            // element constraints are on the type argument (eg. List<@Regex String>), so we setup constraints from it
                            constrain(aggregate.beginConstraints().component(), typeArg).finishComponent().finishConstraints();
                            return aggregate;
                        }
                    }
                    return ConfigAggregateBuilder.create(parent, name, (Class) type, null);
                }
            } else {
                assert type.isArray();
                if (annotatedType instanceof AnnotatedArrayType) {
                    // coerce to an array class
                    Class<E[]> arrayType = (Class<E[]>) type;
                    ConfigAggregateBuilder<T, E> aggregate = (ConfigAggregateBuilder<T, E>) ConfigAggregateBuilder.create(parent, name, arrayType);
                    // take the component constraint information from the special annotated type
                    constrain(aggregate.beginConstraints().component(), ((AnnotatedArrayType) annotatedType).getAnnotatedGenericComponentType()).finishComponent().finishConstraints();
                    return aggregate;
                }
            }
        }
        return new ConfigLeafBuilder<>(parent, name, type);
    }

    @SuppressWarnings("unchecked")
    private static <T, B extends AbstractConstraintsBuilder<?, ?, T>> B constrain(B constraints, AnnotatedElement field) {
        if (field.isAnnotationPresent(Setting.Constrain.Range.class)) {
            Setting.Constrain.Range annotation = field.getAnnotation(Setting.Constrain.Range.class);
            if (annotation.min() > Double.NEGATIVE_INFINITY) {
                constraints.atLeast((T) Double.valueOf(annotation.min()));
            }
            if (annotation.max() < Double.POSITIVE_INFINITY) {
                constraints.atMost((T) Double.valueOf(annotation.max()));
            }
        }
        if (field.isAnnotationPresent(Setting.Constrain.BigRange.class)) {
            Setting.Constrain.BigRange annotation = field.getAnnotation(Setting.Constrain.BigRange.class);
            if (!annotation.min().isEmpty()) {
                constraints.atLeast((T) new BigDecimal(annotation.min()));
            }
            if (!annotation.max().isEmpty()) {
                constraints.atMost((T) new BigDecimal(annotation.max()));
            }
        }
        if (field.isAnnotationPresent(Setting.Constrain.MinLength.class)) constraints.minLength(field.getAnnotation(Setting.Constrain.MinLength.class).value());
        if (field.isAnnotationPresent(Setting.Constrain.MaxLength.class)) constraints.maxLength(field.getAnnotation(Setting.Constrain.MaxLength.class).value());
        if (field.isAnnotationPresent(Setting.Constrain.Regex.class)) constraints.regex(field.getAnnotation(Setting.Constrain.Regex.class).value());
        return constraints;
    }

    @SuppressWarnings("unchecked")
    private static <T, P> T findDefaultValue(Field field, P pojo) throws FiberException {
        boolean accessible = field.isAccessible();
        field.setAccessible(true);
        T value;
        try {
            value = (T) field.get(pojo);
        } catch (IllegalAccessException e) {
            throw new FiberException("Couldn't get value for field '" + field.getName() + "'", e);
        }
        field.setAccessible(accessible);
        return value;
    }

    private static <T, P, A> BiConsumer<T,T> constructListener(Member listener, P pojo, Class<A> wantedType) throws FiberException {
        if (listener instanceof Field) {
            return constructListenerFromField((Field) listener, pojo, wantedType);
        } else if (listener instanceof Method) {
            return constructListenerFromMethod((Method) listener, pojo, wantedType);
        } else {
            throw new FiberException("Cannot create listener from " + listener + ": must be a field or method");
        }
    }

    private static <T, P, A> BiConsumer<T,T> constructListenerFromMethod(Method method, P pojo, Class<A> wantedType) throws FiberException {
        int i = checkListenerMethod(method, wantedType);
        method.setAccessible(true);
        final boolean staticMethod = Modifier.isStatic(method.getModifiers());
        switch (i) {
            case 1:
                return (oldValue, newValue) -> {
                    try {
                        method.invoke(staticMethod ? null : pojo, newValue);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
                };
            case 2:
                return (oldValue, newValue) -> {
                    try {
                        method.invoke(staticMethod ? null : pojo, oldValue, newValue);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
                };
            default:
                throw new FiberException("Listener failed due to an invalid number of arguments.");
        }
    }

    private static <A> int checkListenerMethod(Method method, Class<A> wantedType) throws FiberException {
        if (!method.getReturnType().equals(void.class)) throw new FiberException("Listener method must return void");
        int paramCount = method.getParameterCount();
        if ((paramCount != 1 && paramCount != 2) || !method.getParameterTypes()[0].equals(wantedType)) throw new FiberException("Listener method must have exactly two parameters of type that it listens for");
        return paramCount;
    }

    private static <T, P, A> BiConsumer<T,T> constructListenerFromField(Field field, P pojo, Class<A> wantedType) throws FiberException {
        checkListenerField(field, wantedType);

        boolean isAccessible = field.isAccessible();
        field.setAccessible(true);
        BiConsumer<T, T> consumer;
        try {
            @SuppressWarnings({ "unchecked", "unused" })
            BiConsumer<T, T> suppress = consumer = (BiConsumer<T, T>) field.get(pojo);
        } catch (IllegalAccessException e) {
            throw new FiberException("Couldn't construct listener", e);
        }
        field.setAccessible(isAccessible);

        return consumer;
    }

    private static <A> void checkListenerField(Field field, Class<A> wantedType) throws MalformedFieldException {
        if (!field.getType().equals(BiConsumer.class)) {
            throw new MalformedFieldException("Field " + field.getDeclaringClass().getCanonicalName() + "#" + field.getName() + " must be a BiConsumer");
        }

        ParameterizedType genericTypes = (ParameterizedType) field.getGenericType();
        if (genericTypes.getActualTypeArguments().length != 2) {
            throw new MalformedFieldException("Listener " + field.getDeclaringClass().getCanonicalName() + "#" + field.getName() + " must have 2 generic types");
        } else if (genericTypes.getActualTypeArguments()[0] != genericTypes.getActualTypeArguments()[1]) {
            throw new MalformedFieldException("Listener " + field.getDeclaringClass().getCanonicalName() + "#" + field.getName() + " must have 2 identical generic types");
        } else if (!genericTypes.getActualTypeArguments()[0].equals(wantedType)) {
            throw new MalformedFieldException("Listener " + field.getDeclaringClass().getCanonicalName() + "#" + field.getName() + " must have the same generic type as the field it's listening for");
        }
    }

    private static <T> Class<T> getSettingTypeFromField(Field field) {
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) field.getType();
        return wrapPrimitive(type);
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> wrapPrimitive(Class<T> type) {
        if (type.equals(boolean.class)) return (Class<T>) Boolean.class;
        if (type.equals(byte.class)) return (Class<T>) Byte.class;
        if (type.equals(char.class)) return (Class<T>) Character.class;
        if (type.equals(short.class)) return (Class<T>) Short.class;
        if (type.equals(int.class)) return (Class<T>) Integer.class;
        if (type.equals(double.class)) return (Class<T>) Double.class;
        if (type.equals(float.class)) return (Class<T>) Float.class;
        if (type.equals(long.class)) return (Class<T>) Long.class;
        return type;
    }

    private static String findComment(Field field) {
        return getSettingAnnotation(field).map(Setting::comment).filter(s -> !s.isEmpty()).orElse(null);
    }

    private static String findName(Field field, SettingNamingConvention convention) {
        return Optional.ofNullable(
                field.isAnnotationPresent(Setting.Group.class) ?
                        field.getAnnotation(Setting.Group.class).name() :
                        getSettingAnnotation(field).map(Setting::name).orElse(null))
                .filter(s -> !s.isEmpty())
                .orElse(convention.name(field.getName()));
    }

    private static SettingNamingConvention createConvention(Class<? extends SettingNamingConvention> namingConvention) throws FiberException {
        try {
            return namingConvention.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new FiberException("Could not initialise naming convention", e);
        }
    }

}