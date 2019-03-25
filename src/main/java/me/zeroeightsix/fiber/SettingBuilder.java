package me.zeroeightsix.fiber;

import me.zeroeightsix.fiber.constraint.Constraint;
import me.zeroeightsix.fiber.constraint.ConstraintsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SettingBuilder<S, T> {

	Class<T> type;

	T value;
	String comment = "";
	private List<BiConsumer<T, T>> consumers = new ArrayList<>();
	private List<Constraint> constraints = new ArrayList<>();
	private String name;
	private Converter<S, T> converter;
	private Settings registry;
	private boolean isFinal = false;

	public SettingBuilder(Settings registry, Class<T> type) {
		this.registry = registry;
		this.type = type;
	}

	/**
	 * Attempts to create a copy of given SettingBuilder. Will attempt to cast everything.
	 */
	protected SettingBuilder(SettingBuilder<S, Object> copy, Class<T> type) {
		this(copy.registry, type);
		this.value = (T) copy.value;
		this.comment = copy.comment;
		this.consumers = copy.consumers.stream().map(consumer -> (BiConsumer<T, T>) consumer::accept).collect(Collectors.toList());
		this.name = copy.name;
		this.converter = (Converter<S, T>) copy.converter;
	}

	public <A> SettingBuilder type(Class<? extends A> clazz) {
		return new SettingBuilder(this, clazz);
	}

	public SettingBuilder<S, T> comment(String comment) {
		if (!this.comment.isEmpty()) this.comment += "\n";
		this.comment += comment;
		return this;
	}

	public SettingBuilder<S, T> listen(BiConsumer<T, T> consumer) {
		consumers.add(consumer);
		return this;
	}

	public SettingBuilder<S, T> name(String name) {
		this.name = name;
		return this;
	}

	public SettingBuilder<S, T> defaultValue(T value) {
		this.value = value;
		return this;
	}

	public SettingBuilder<S, T> converter(Converter<S, T> converter) {
		this.converter = converter;
		return this;
	}

	public SettingBuilder<S, T> setFinal() {
		this.isFinal = true;
		return this;
	}

	public ConstraintsBuilder<S, T> constraints() {
		return new ConstraintsBuilder<>(constraints, type, this);
	}

	public Setting<T> build() {
		return registerAndSet(new Setting<>(comment, name, (a, b) -> consumers.forEach(consumer -> consumer.accept(a, b)), buildRestriction(), value, type, converter == null ? registry.provideConverter(type) : converter, this.constraints));
	}

	private Setting<T> registerAndSet(Setting<T> setting) {
		if (setting.getName() != null) {
			registry.registerAndRecover(setting);
		}
		return setting;
	}

	protected Predicate<T> buildRestriction() {
		return isFinal ? t -> true : t -> constraints.stream().anyMatch(constraint -> constraint.test(t));
		//return isFinal ? t -> true : (restrictions.isEmpty() ? t -> false : t -> restrictions.stream().anyMatch(function -> !function.apply(t)));
	}
}
