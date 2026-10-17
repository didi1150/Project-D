package dev.bukkit.glow;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Locale;
import java.util.Optional;

import com.comphenix.protocol.injector.StructureCache;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.BukkitConverters;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedTeamParameters;

/**
 * Builds a team-parameters handle without depending on a specific ProtocolLib
 * release's {@code WrappedTeamParameters.Builder}. The field layout changed in
 * newer Minecraft versions (the color moved from {@code ChatFormatting} to an
 * {@code Optional<TeamColor>}), so the fields are written by type with a
 * legacy fallback.
 */
final class GlowTeamParameters {

    private GlowTeamParameters() {
    }

    static WrappedTeamParameters create(EnumWrappers.ChatFormatting color) {
        Class<?> type = WrappedTeamParameters.getNmsClassOrThrow();
        Object handle = StructureCache.newInstance(type);
        StructureModifier<Object> modifier = new StructureModifier<>(type).withTarget(handle);

        StructureModifier<WrappedChatComponent> components = modifier.withType(
                MinecraftReflection.getIChatBaseComponentClass(),
                BukkitConverters.getWrappedChatComponentConverter());
        for (int i = 0; i < Math.min(3, components.size()); i++) {
            components.write(i, WrappedChatComponent.fromText(""));
        }

        writeVisibility(modifier);
        writeCollision(modifier);
        writeColor(modifier, color);
        writeOptions(modifier);

        return new WrappedTeamParameters(handle);
    }

    private static void writeVisibility(StructureModifier<Object> modifier) {
        try {
            StructureModifier<EnumWrappers.TeamVisibility> visibilities = modifier.withType(
                    EnumWrappers.getTeamVisibilityClass(), EnumWrappers.getTeamVisibilityConverter());
            if (visibilities.size() > 0) {
                visibilities.writeSafely(0, EnumWrappers.TeamVisibility.ALWAYS);
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            StructureModifier<String> strings = modifier.withType(String.class);
            if (strings.size() > 0) {
                strings.writeSafely(0, "always");
            }
        } catch (Exception ignored) {
        }
    }

    private static void writeCollision(StructureModifier<Object> modifier) {
        try {
            StructureModifier<EnumWrappers.TeamCollisionRule> rules = modifier.withType(
                    EnumWrappers.getTeamCollisionRuleClass(), EnumWrappers.getTeamCollisionRuleConverter());
            if (rules.size() > 0) {
                rules.writeSafely(0, EnumWrappers.TeamCollisionRule.NEVER);
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            StructureModifier<String> strings = modifier.withType(String.class);
            if (strings.size() > 1) {
                strings.writeSafely(1, "never");
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void writeColor(StructureModifier<Object> modifier, EnumWrappers.ChatFormatting color) {
        try {
            StructureModifier<EnumWrappers.ChatFormatting> colors = modifier.withType(
                    EnumWrappers.getChatFormattingClass(), EnumWrappers.getChatFormattingConverter());
            if (colors.size() > 0) {
                colors.write(0, color);
                return;
            }
        } catch (Exception ignored) {
        }

        try {
            StructureModifier<Optional> optionals = modifier.withType(Optional.class);
            if (optionals.size() == 0) {
                return;
            }
            Class<?> teamColorClass = resolveTeamColorClass(optionals);
            Object teamColor = resolveTeamColor(teamColorClass, color);
            if (teamColor != null) {
                optionals.write(0, Optional.of(teamColor));
            }
        } catch (Exception ignored) {
        }
    }

    private static void writeOptions(StructureModifier<Object> modifier) {
        try {
            StructureModifier<Integer> ints = modifier.withType(int.class);
            if (ints.size() > 0) {
                ints.writeSafely(0, 0);
            }
        } catch (Exception ignored) {
        }
        try {
            StructureModifier<Byte> bytes = modifier.withType(byte.class);
            if (bytes.size() > 0) {
                bytes.writeSafely(0, (byte) 0);
            }
        } catch (Exception ignored) {
        }
    }

    private static Class<?> resolveTeamColorClass(StructureModifier<?> optionals) {
        try {
            Type genericType = optionals.getField(0).getGenericType();
            if (genericType instanceof ParameterizedType parameterized) {
                Type[] arguments = parameterized.getActualTypeArguments();
                if (arguments.length == 1 && arguments[0] instanceof Class<?> clazz) {
                    return clazz;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            return MinecraftReflection.getMinecraftClass("world.scores.TeamColor");
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object resolveTeamColor(Class<?> teamColorClass, EnumWrappers.ChatFormatting color) {
        if (teamColorClass == null) {
            return null;
        }
        try {
            Method byName = teamColorClass.getMethod("byName", String.class);
            return byName.invoke(null, color.name().toLowerCase(Locale.ROOT));
        } catch (Exception ignored) {
        }
        try {
            return Enum.valueOf((Class<? extends Enum>) teamColorClass, color.name());
        } catch (Exception ignored) {
            return null;
        }
    }
}
