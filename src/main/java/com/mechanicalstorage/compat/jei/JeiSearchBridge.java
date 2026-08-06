package com.mechanicalstorage.compat.jei;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class JeiSearchBridge {
	private static Supplier<String> filterTextGetter;
	private static Consumer<String> filterTextSetter;

	private JeiSearchBridge() {
	}

	public static void connect(Supplier<String> getter, Consumer<String> setter) {
		filterTextGetter = getter;
		filterTextSetter = setter;
	}

	public static void disconnect() {
		filterTextGetter = null;
		filterTextSetter = null;
	}

	public static boolean isAvailable() {
		return filterTextGetter != null && filterTextSetter != null;
	}

	public static String getFilterText() {
		return filterTextGetter == null ? "" : filterTextGetter.get();
	}

	public static void setFilterText(String filterText) {
		if (filterTextSetter != null) {
			filterTextSetter.accept(filterText);
		}
	}
}
