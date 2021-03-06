package io.github.fablabsmc.fablabs.api.fiber.v1.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.github.fablabsmc.fablabs.api.fiber.v1.annotation.convention.NoNamingConvention;
import io.github.fablabsmc.fablabs.api.fiber.v1.tree.ConfigTree;

/**
 * Indicates a type represents a structure in a configuration file.
 *
 * <p>While it not necessary to use this annotation to generate {@link ConfigTree}s from a POJO class,
 * it can be used to specify other metadata.
 *
 * @see Settings#onlyAnnotated()
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Settings {
	/**
	 * Specifies whether or not all fields in this class should be serialised, or only those annotated with {@link Setting}.
	 *
	 * <p>If you want to exclude one field without having to mark all others with the {@link Setting} annotation, the field can be marked as {@code transient} instead.
	 * All transient fields are ignored by default.
	 *
	 * @return whether or not only annotated fields should be serialised
	 * @see AnnotatedSettings.Builder#collectOnlyAnnotatedMembers()
	 */
	boolean onlyAnnotated() default false;

	/**
	 * Returns the naming convention used for (re)naming the fields in this class during serialisation.
	 *
	 * @return the {@link SettingNamingConvention naming convention} for this class
	 * @deprecated use {@link AnnotatedSettings.Builder#useNamingConvention(SettingNamingConvention)}
	 */
	@Deprecated
	Class<? extends SettingNamingConvention> namingConvention() default NoNamingConvention.class;
}
