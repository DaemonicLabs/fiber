package io.github.fablabsmc.fablabs.api.fiber.v1.schema.type.derived;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.function.Function;
import java.util.regex.Pattern;

import io.github.fablabsmc.fablabs.api.fiber.v1.annotation.processor.ConstraintAnnotationProcessor;
import io.github.fablabsmc.fablabs.api.fiber.v1.schema.type.StringSerializableType;

/**
 * A {@link ConfigType} for regex-defined string values.
 *
 * @param <T> The runtime type of the underlying {@link String} value.
 */
public final class StringConfigType<T> extends ConfigType<T, String, StringSerializableType> {
	public StringConfigType(StringSerializableType serializedType, Class<T> runtimeType, Function<String, T> f, Function<T, String> f0) {
		super(serializedType, runtimeType, f, f0);
	}

	@Override
	public <U> StringConfigType<U> derive(Class<? super U> runtimeType, Function<T, U> partialDeserializer, Function<U, T> partialSerializer) {
		@SuppressWarnings("unchecked") Class<U> c = (Class<U>) runtimeType;
		return new StringConfigType<>(this.getSerializedType(), c, s -> partialDeserializer.apply(this.deserializer.apply(s)), u -> this.serializer.apply(partialSerializer.apply(u)));
	}

	@Override
	public StringConfigType<T> withType(StringSerializableType newSpec) {
		this.checkTypeNarrowing(newSpec);
		return new StringConfigType<>(newSpec, this.getRuntimeType(), this.deserializer, this.serializer);
	}

	@Override
	public StringConfigType<T> constrain(ConstraintAnnotationProcessor<Annotation> processor, Annotation annotation, AnnotatedElement annotated) {
		return processor.processString(this, annotation, annotated);
	}

	/**
	 * Returns a new {@link StringConfigType} with a minimum length constraint.
	 */
	public StringConfigType<T> withMinLength(int min) {
		StringSerializableType current = this.getSerializedType();
		return this.withType(new StringSerializableType(min, current.getMaxLength(), current.getPattern()));
	}

	/**
	 * Returns a new {@link StringConfigType} with a maximum length constraint.
	 */
	public StringConfigType<T> withMaxLength(int max) {
		StringSerializableType current = this.getSerializedType();
		return this.withType(new StringSerializableType(current.getMinLength(), max, current.getPattern()));
	}

	/**
	 * Returns a new {@link StringConfigType} with a regex constraint defined by the given pattern string.
	 *
	 * @throws java.util.regex.PatternSyntaxException If regex is not a valid {@link Pattern}.
	 * @see #withPattern(Pattern)
	 */
	public StringConfigType<T> withPattern(String regex) {
		return this.withPattern(Pattern.compile(regex));
	}

	/**
	 * Returns a new {@link StringConfigType} with a regex constraint defined by the given pattern.
	 */
	public StringConfigType<T> withPattern(Pattern pattern) {
		StringSerializableType current = this.getSerializedType();
		return this.withType(new StringSerializableType(current.getMinLength(), current.getMaxLength(), pattern));
	}
}
