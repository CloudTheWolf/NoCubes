package io.github.cadiboo.nocubes.client.optifine;

import io.github.cadiboo.nocubes.NoCubes;
import io.github.cadiboo.nocubes.util.ModUtil;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.ReportedException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;

/**
 * @author Cadiboo
 */
public final class OptiFineLocator {

	@Nullable
	private static Class<?> findConfigClass() {
		// I'm currently putting a copy of Config in my dev workspace to work on OptiFine compatibility
		// TODO: Remove this when I get a copy of OptiFine working in my dev environment.
		if (ModUtil.isDeveloperWorkspace()) return null;
		// Config was moved around in HD_U_F
		// 1. Try to find "net.optifine.Config"
		// 2. Try to find "Config"
		Class<?> config;
		try {
			config = Class.forName("net.optifine.Config");
		} catch (ClassNotFoundException failedToFindModernConfigClass) {
			try {
				config = Class.forName("Config");
			} catch (ClassNotFoundException failedToFindLegacyConfigClass) {
				NoCubes.LOGGER.info("OptiFineCompatibility: OptiFine not detected.");
				return null;
			}
		}
		NoCubes.LOGGER.info("OptiFineCompatibility: Found OptiFine!");
		return config;
	}

	private static boolean isCompatibleOptiFineVersion(final String version) {
		return version.contains("HD_U_F");
	}

	public static boolean isOptiFineInstalledAndCompatible() {
		return isOptiFineInstalled() && isOptiFineCompatible();
	}

	public static boolean isOptiFineCompatible() {
		final Class<?> configClass = findConfigClass();
		if (configClass == null) {
			return false;
		}
		return isCompatibleOptiFineVersion(getOptiFineVersion(configClass));
	}

	@Nullable
	public static String getOptiFineVersion() {
		final Class<?> configClass = findConfigClass();
		return configClass == null ? null : getOptiFineVersion(configClass);
	}

	@Nonnull
	public static String getOptiFineVersion(final Class<?> configClass) {
		try {
			final Field versionField = configClass.getField("VERSION");
			versionField.setAccessible(true);
			return (String) versionField.get(null);
		} catch (NoSuchFieldException | IllegalAccessException | ClassCastException e) {
			final CrashReport crashReport = CrashReport.makeCrashReport(e, "Problem getting OptiFine version");
			crashReport.makeCategory("NoCubes OptiFine Locator");
			throw new ReportedException(crashReport);
		}
	}

	public static boolean isOptiFineInstalled() {
		return findConfigClass() != null;
	}

}
