package net.swiftsmp.swiftclient;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;

/**
 * Small API shims so one source tree builds for every 26.x version.
 *
 * <p>26.2 changed two calls SwiftClient makes: {@code InputConstants.isKeyDown} takes a Window
 * and {@code OptionsScreen} takes a boolean, while 26.3 has the plain forms again. Resolving
 * them once through reflection keeps the mod buildable across the line without a branch per
 * version. Both paths are cold (a key release, opening Options), so reflection costs nothing.
 */
public final class Compat {
	private static Method isKeyDown;
	private static boolean isKeyDownResolved;
	private static Constructor<?> optionsConstructor;
	private static boolean optionsResolved;

	private Compat() {}

	/** InputConstants.isKeyDown(key), for a 26.2+ window or the older plain form. */
	public static boolean isKeyDown(int keyCode) {
		if (!isKeyDownResolved) {
			isKeyDownResolved = true;
			try {
				isKeyDown = InputConstants.class.getMethod("isKeyDown", int.class);
			} catch (NoSuchMethodException plain) {
				try {
					isKeyDown = InputConstants.class.getMethod("isKeyDown", Window.class, int.class);
				} catch (NoSuchMethodException ignored) {
					isKeyDown = null;
				}
			}
		}
		if (isKeyDown == null) return false;
		try {
			Object result = isKeyDown.getParameterCount() == 2
				? isKeyDown.invoke(null, Minecraft.getInstance().getWindow(), keyCode)
				: isKeyDown.invoke(null, keyCode);
			return Boolean.TRUE.equals(result);
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}

	/** The vanilla Options screen, whichever constructor this version has. */
	public static Screen optionsScreen(Screen parent, Options options) {
		if (!optionsResolved) {
			optionsResolved = true;
			for (Constructor<?> candidate : OptionsScreen.class.getConstructors()) {
				Class<?>[] types = candidate.getParameterTypes();
				boolean plain = types.length == 2 && types[0] == Screen.class && types[1] == Options.class;
				boolean withFlag = types.length == 3 && types[0] == Screen.class && types[1] == Options.class && types[2] == boolean.class;
				if (plain || withFlag) {
					optionsConstructor = candidate;
					break;
				}
			}
		}
		if (optionsConstructor == null) return null;
		try {
			return optionsConstructor.getParameterCount() == 3
				? (Screen) optionsConstructor.newInstance(parent, options, false)
				: (Screen) optionsConstructor.newInstance(parent, options);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}
}
