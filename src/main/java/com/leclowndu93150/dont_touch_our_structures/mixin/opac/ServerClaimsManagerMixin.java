package com.leclowndu93150.dont_touch_our_structures.mixin.opac;

import com.leclowndu93150.dont_touch_our_structures.config.ModConfig;
import com.leclowndu93150.dont_touch_our_structures.protection.ProtectionResult;
import com.leclowndu93150.dont_touch_our_structures.protection.StructureProtectionManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.pac.common.claims.action.api.ClaimingAction;
import xaero.pac.common.claims.player.PlayerChunkClaim;
import xaero.pac.common.claims.result.api.AreaClaimResult;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.server.claims.ServerClaimsManager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Targets Open Parties and Claims 0.33.0 (NeoForge 1.21.1).
 * Both injectors use require = 0 so a signature mismatch on another OPAC version
 * only disables the injection instead of crashing the server.
 */
@Mixin(value = ServerClaimsManager.class, remap = false)
public abstract class ServerClaimsManagerMixin {

    @Shadow
    public abstract MinecraftServer getServer();

    @Inject(
            method = "tryToClaimTyped(Lnet/minecraft/resources/ResourceLocation;Ljava/util/UUID;ILnet/minecraft/resources/ResourceLocation;IIIIZZLxaero/pac/common/claims/result/api/ClaimResult$Type;)Lxaero/pac/common/claims/result/api/ClaimResult;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void dtos_interceptSingleClaim(
            ResourceLocation dimension, UUID playerId, int subConfigIndex,
            ResourceLocation fromDimension, int fromX, int fromZ, int x, int z,
            boolean force, boolean ignoreAnchors, ClaimResult.Type claimingPermissionResult,
            CallbackInfoReturnable<ClaimResult<PlayerChunkClaim>> cir
    ) {
        if (!ModConfig.opacEnabled || !ModConfig.opacUseMixin) {
            return;
        }

        try {
            MinecraftServer server = getServer();
            if (server == null || dimension == null) {
                return;
            }

            ServerLevel level = server.getLevel(dtos$dimensionToResourceKey(dimension));
            if (level == null) {
                return;
            }

            ChunkPos chunkPos = new ChunkPos(x, z);
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);

            ProtectionResult result = StructureProtectionManager.getInstance().checkProtection(level, chunkPos, player);

            if (result.isProtected()) {
                if (player != null) {
                    player.sendSystemMessage(Component.literal(result.getDenialMessage(chunkPos)));
                }
                cir.setReturnValue(new ClaimResult<>(null, ClaimResult.Type.ADDON_FORBIDS));
            }
        } catch (LinkageError ignored) {
            // OPAC API drift: fail open rather than crash the claim tick.
        }
    }

    /**
     * Injects the long worker overload; the short overload and tryToClaimArea funnel into it.
     */
    @Inject(
            method = "tryClaimActionOverArea(Lnet/minecraft/resources/ResourceLocation;Ljava/util/UUID;ILnet/minecraft/resources/ResourceLocation;IIIIIILxaero/pac/common/claims/action/api/ClaimingAction;ZILjava/util/function/Predicate;Lxaero/pac/common/claims/result/api/ClaimResult$Type;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void dtos_interceptAreaClaim(
            ResourceLocation dimension, UUID playerId, int subConfigIndex,
            ResourceLocation fromDimension, int fromX, int fromZ,
            int left, int top, int right, int bottom,
            ClaimingAction action, boolean force, int maxChunksToAffect,
            Predicate<ChunkPos> anchorFilter, ClaimResult.Type claimingPermissionResult,
            Consumer<AreaClaimResult> listener,
            CallbackInfo ci
    ) {
        if (!ModConfig.opacEnabled || !ModConfig.opacUseMixin) {
            return;
        }

        if (action != ClaimingAction.CLAIM) {
            return;
        }

        try {
            MinecraftServer server = getServer();
            if (server == null || dimension == null) {
                return;
            }

            ServerLevel level = server.getLevel(dtos$dimensionToResourceKey(dimension));
            if (level == null) {
                return;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);

            Set<ClaimResult.Type> blockedResults = new HashSet<>();
            boolean hasProtectedChunks = false;
            String lastDenialMessage = null;

            for (int cx = left; cx <= right; cx++) {
                for (int cz = top; cz <= bottom; cz++) {
                    ChunkPos chunkPos = new ChunkPos(cx, cz);
                    ProtectionResult result = StructureProtectionManager.getInstance().checkProtection(level, chunkPos, player);

                    if (result.isProtected()) {
                        hasProtectedChunks = true;
                        lastDenialMessage = result.getDenialMessage(chunkPos);
                        blockedResults.add(ClaimResult.Type.ADDON_FORBIDS);
                    }
                }
            }

            if (hasProtectedChunks) {
                if (player != null && lastDenialMessage != null) {
                    player.sendSystemMessage(Component.literal(lastDenialMessage));
                }
                if (listener != null) {
                    listener.accept(new AreaClaimResult(
                            blockedResults,
                            new HashSet<>(),
                            dimension,
                            left, top, right, bottom
                    ));
                }
                ci.cancel();
            }
        } catch (LinkageError ignored) {
            // OPAC API drift: fail open rather than crash the claim tick.
        }
    }

    @Unique
    private static ResourceKey<Level> dtos$dimensionToResourceKey(ResourceLocation dimension) {
        return ResourceKey.create(Registries.DIMENSION, dimension);
    }
}
