package me.zeroeightsix.fiber.builder;

import me.zeroeightsix.fiber.annotation.AnnotatedSettings;
import me.zeroeightsix.fiber.builder.constraint.AggregateConstraintsBuilder;
import me.zeroeightsix.fiber.exception.RuntimeFiberException;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.function.BiConsumer;

/**
 * A {@code ConfigValueBuilder} that produces aggregate {@code ConfigValue}s.
 *
 * <p>Aggregate types are those that hold multiple values, such as {@code List} or arrays.
 * Settings with scalar types, such as {@code Integer} or {@code String}, are created using {@link ConfigValueBuilder}.
 *
 * @param <A> the type of aggregate value
 * @param <E> the type of values held by {@code <A>}
 * @see #create
 */
public final class ConfigAggregateBuilder<A, E> extends ConfigValueBuilder<A> {
    /**
     * Determines if a {@code Class} object represents an aggregate type,
     * ie. if it is an {@linkplain Class#isArray() Array} or a {@linkplain Collection}.
     *
     * @param type the type to check
     * @return {@code true} if {@code type} is an aggregate type;
     * {@code false} otherwise
     */
    public static boolean isAggregate(Class<?> type) {
        return type.isArray() || Collection.class.isAssignableFrom(type);
    }

    /**
     * Creates and returns an {@link ConfigAggregateBuilder aggregate builder} for an array type.
     *
     * @param arrayType the class of the array used for this aggregate builder
     * @param <E>       the type of values held by {@code arrayType}
     * @return the newly created builder
     * @see #isAggregate
     */
    @SuppressWarnings("unchecked")
    public static <E> ConfigAggregateBuilder<E[], E> create(@Nonnull String name, @Nonnull Class<E[]> arrayType) {
        if (!arrayType.isArray()) throw new RuntimeFiberException(arrayType + " is not a valid array type");
        return new ConfigAggregateBuilder<>(name, arrayType, (Class<E>) AnnotatedSettings.wrapPrimitive(arrayType.getComponentType()));
    }

    /**
     * Creates and returns an {@link ConfigAggregateBuilder aggregate builder} for a collection type.
     *
     * @param collectionType the class of the collection used for this aggregate builder
     * @param componentType  the class of the type of elements {@code collectionType} holds
     * @param <C>            the type {@code collectionType} represents. eg. {@code List}
     * @param <E>            the type {@code componentType} represents. eg. {@code Integer}
     * @return the newly created builder
     */
    @SuppressWarnings("unchecked")
    public static <C extends Collection<E>, E> ConfigAggregateBuilder<C, E> create(@Nonnull String name, @Nonnull Class<? super C> collectionType, @Nonnull Class<E> componentType) {
        if (!Collection.class.isAssignableFrom(collectionType))
            throw new RuntimeFiberException(collectionType + " is not a valid Collection type");
        return new ConfigAggregateBuilder<>(name, (Class<C>) collectionType, componentType);
    }

    @Nonnull
    private final Class<E> componentType;

    private ConfigAggregateBuilder(@Nonnull String name, @Nonnull Class<A> type, @Nonnull Class<E> componentType) {
        super(name, type);
        this.componentType = componentType;
    }

    @Override
    public ConfigAggregateBuilder<A, E> withName(String name) {
        super.withName(name);
        return this;
    }

    @Override
    public ConfigAggregateBuilder<A, E> withComment(String comment) {
        super.withComment(comment);
        return this;
    }

    @Override
    public ConfigAggregateBuilder<A, E> withListener(BiConsumer<A, A> consumer) {
        super.withListener(consumer);
        return this;
    }

    @Override
    public ConfigAggregateBuilder<A, E> withDefaultValue(A defaultValue) {
        super.withDefaultValue(defaultValue);
        return this;
    }

    @Override
    public ConfigAggregateBuilder<A, E> setFinal() {
        super.setFinal();
        return this;
    }

    @Override
    public ConfigAggregateBuilder<A, E> setFinal(boolean isFinal) {
        super.setFinal(isFinal);
        return this;
    }

    @Override
    public ConfigAggregateBuilder<A, E> withParent(ConfigNodeBuilder node) {
        super.withParent(node);
        return this;
    }

    @Override
    public AggregateConstraintsBuilder<ConfigAggregateBuilder<A, E>, A, E> constraints() {
        return new AggregateConstraintsBuilder<>(this, constraintList, type, componentType);
    }

}
