package xyz.eclipseisoffline.eclipsescustomname;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import me.lucko.fabric.api.permissions.v0.Options;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

public class PlayerNameManager extends PersistentState {

    private static final Type<PlayerNameManager> DATA_TYPE = new Type<>(PlayerNameManager::new,
            PlayerNameManager::loadFromNbt, null);
    private final Map<UUID, Text> playerPrefixes = new HashMap<>();
    private final Map<UUID, Text> playerSuffixes = new HashMap<>();
    private final Map<UUID, Text> playerNicknames = new HashMap<>();
    private final Map<UUID, Text> fullPlayerNames = new HashMap<>();

    public void updatePlayerName(ServerPlayerEntity player, Text name, NameType type) {
        switch (type) {
            case PREFIX -> playerPrefixes.put(player.getUuid(), name);
            case SUFFIX -> playerSuffixes.put(player.getUuid(), name);
            case NICKNAME -> playerNicknames.put(player.getUuid(), name);
        }
        markDirty(player);
    }

    public Text getFullPlayerName(ServerPlayerEntity player) {
        if (!fullPlayerNames.containsKey(player.getUuid())) {
            updateFullPlayerName(player);
        }
        return fullPlayerNames.get(player.getUuid());
    }

    private void markDirty(ServerPlayerEntity player) {
        updateFullPlayerName(player);
        markDirty();
    }

    private void updateFullPlayerName(ServerPlayerEntity player) {
        Optional<String> permissionsPrefix = Options.get(player, "prefix");
        Text prefix = playerPrefixes.get(player.getUuid());
        Text suffix = playerSuffixes.get(player.getUuid());
        Optional<String> permissionsSuffix = Options.get(player, "suffix");
        Text nickname = playerNicknames.get(player.getUuid());

        MutableText name = Text.literal("");
        if (permissionsPrefix.isPresent()) {
            name.append(CustomName
                    .argumentToText(permissionsPrefix.orElseThrow(),
                            CustomNameConfig.getInstance().formattingEnabled(),
                            true, false));
            name.append(" ");
        }
        if (prefix != null) {
            name.append(prefix);
            name.append(" ");
        }
        if (nickname != null) {
            name.append(nickname);
        } else {
            name.append(player.getName());
        }
        if (suffix != null) {
            name.append(" ");
            name.append(suffix);
        }
        if (permissionsSuffix.isPresent()) {
            name.append(" ");
            name.append(CustomName
                    .argumentToText(permissionsSuffix.orElseThrow(),
                            CustomNameConfig.getInstance().formattingEnabled(),
                            true, false));
        }

        fullPlayerNames.put(player.getUuid(), name);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        nbt.put("prefixes", writeNames(playerPrefixes, registries));
        nbt.put("suffixes", writeNames(playerSuffixes, registries));
        nbt.put("nicknames", writeNames(playerNicknames, registries));
        return nbt;
    }

    private static NbtCompound writeNames(Map<UUID, Text> names, RegistryWrapper.WrapperLookup registries) {
        NbtCompound namesTag = new NbtCompound();
        names.forEach((uuid, text) -> {
            if (text != null) {
                namesTag.putString(uuid.toString(), Text.Serialization.toJsonString(text, registries));
            }
        });
        return namesTag;
    }

    public static PlayerNameManager loadFromNbt(NbtCompound nbtCompound, RegistryWrapper.WrapperLookup registries) {
        PlayerNameManager playerNameManager = new PlayerNameManager();

        NbtCompound prefixes = nbtCompound.getCompound("prefixes");
        readNames(prefixes, playerNameManager.playerPrefixes, registries);
        NbtCompound suffixes = nbtCompound.getCompound("suffixes");
        readNames(suffixes, playerNameManager.playerSuffixes, registries);
        NbtCompound nicknames = nbtCompound.getCompound("nicknames");
        readNames(nicknames, playerNameManager.playerNicknames, registries);

        return playerNameManager;
    }

    private static void readNames(NbtCompound compound, Map<UUID, Text> nameMap,
            RegistryWrapper.WrapperLookup registries) {
        compound.getKeys().forEach(key -> {
            Text name;
            String raw = compound.getString(key);
            boolean old;
            try {
                old = !JsonParser.parseString(raw).isJsonObject();
            } catch (JsonParseException exception) {
                old = true;
            }
            if (old) {
                CustomName.LOGGER.info("Converting old name of " + key + " to new format");
                name = CustomName.argumentToText(raw.replaceAll("\"", "").replaceAll(
                                String.valueOf(Formatting.FORMATTING_CODE_PREFIX), "&"), true, false,
                        false);
            } else {
                name = Text.Serialization.fromJson(compound.getString(key), registries);
            }
            nameMap.put(UUID.fromString(key), name);
        });
    }

    public static PlayerNameManager getPlayerNameManager(MinecraftServer server) {
        PersistentStateManager persistentStateManager = server.getWorld(World.OVERWORLD)
                .getPersistentStateManager();
        return persistentStateManager.getOrCreate(DATA_TYPE, CustomName.MOD_ID);
    }

    public enum NameType {
        PREFIX("Prefix"),
        SUFFIX("Suffix"),
        NICKNAME("Nickname");

        private final String displayName;

        NameType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
