package xyz.eclipseisoffline.eclipsescustomname.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Nullables;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @Unique
    private static final Comparator<PlayerListEntry> ENTRY_ORDERING = Comparator
            .comparingInt((PlayerListEntry entry) -> entry.getGameMode() == GameMode.SPECTATOR ? 1 : 0)
            .thenComparing((entry) -> {
                String lowerCaseName = Objects.requireNonNull(entry.getDisplayName()).getString().toLowerCase();
                if (lowerCaseName.contains("head") && lowerCaseName.contains("admin")) {
                    return -6;
                } else if (lowerCaseName.contains("admin")) {
                    return -5;
                } else if (lowerCaseName.contains("mod")) {
                    return -4;
                } else if (lowerCaseName.contains("dev")) {
                    return -3;
                } else if (lowerCaseName.contains("artist")) {
                    return -2;
                } else return -1;
            })
            .thenComparing((entry) -> (String) Nullables.mapOrElse(entry.getScoreboardTeam(), Team::getName, ""))
            .thenComparing((entry) -> entry.getProfile().getName(), String::compareToIgnoreCase);

    @Final
    @Shadow
    private MinecraftClient client;

    @Inject(method = "collectPlayerEntries", at = @At(value = "RETURN", target = "Lnet/minecraft/client/gui/hud/PlayerListHud;collectPlayerEntries()Ljava/util/List;"), cancellable = true)
    public void collectPlayerEntries(CallbackInfoReturnable<List<PlayerListEntry>> cir) {
        assert client.player != null;
        Collection<PlayerListEntry> entries = client.player.networkHandler.getListedPlayerListEntries();
        cir.setReturnValue(entries.stream().sorted(ENTRY_ORDERING).limit(80L).toList());
    }
}
