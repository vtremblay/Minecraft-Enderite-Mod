package net.enderitemc.enderitemod.mixin.fabric;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.enderitemc.enderitemod.EnderiteMod;
import net.enderitemc.enderitemod.tools.EnderiteCrossbow;
import net.enderitemc.enderitemod.tools.EnderiteTools;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In 26.3 the old {@code ItemInHandRenderer} was split: the first-person hand/item state and
 * selection logic moved to {@link FirstPersonHandsAndItems}, while the drawing moved to
 * {@code FirstPersonHandsAndItemsRenderer}. The hooks that teach vanilla to treat the enderite
 * bow and crossbow like their vanilla counterparts live on the state half, so they target this
 * class now. {@code selectionUsingItemWhileHoldingBowLike} no longer exists as a separate
 * method - its logic was inlined into {@code evaluateWhichHandsToRender}, so one wrap covers both.
 */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class EnderiteFirstPersonHandsAndItemsMixin {

    @Inject(
        at = @At("HEAD"),
        method = "isChargedCrossbow(Lnet/minecraft/world/item/ItemStack;)Z",
        cancellable = true)
    private static void enderitemod$chargedEnderiteCrossbow(ItemStack stack, CallbackInfoReturnable<Boolean> info) {
        if (stack.is(EnderiteTools.ENDERITE_CROSSBOW.get()) && EnderiteCrossbow.isCharged(stack)) {
            info.setReturnValue(true);
        }
    }

    @WrapOperation(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"),
        method = "evaluateWhichHandsToRender(Lnet/minecraft/client/player/LocalPlayer;)Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState$HandRenderSelection;")
    private static boolean enderitemod$isBowOrCrossbow(ItemStack instance, Object item, Operation<Boolean> original) {
        if (item == Items.BOW && instance.is(EnderiteTools.ENDERITE_BOW.get())) {
            return true;
        } else if (item == Items.CROSSBOW && instance.is(EnderiteTools.ENDERITE_CROSSBOW.get())) {
            return true;
        } else {
            return original.call(instance, item);
        }
    }

    /**
     * The enderite bow's custom pull curve. 26.3 precomputes the use duration into
     * {@code FirstPersonHandsAndItemsRenderState.mainHandUseDuration} during extraction instead of
     * calling {@code getUseDuration} inside the arm-rendering method, so the wrap moved here.
     */
    @WrapOperation(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getUseDuration(Lnet/minecraft/world/entity/LivingEntity;)I"),
        method = "extractRenderState(Lnet/minecraft/client/player/LocalPlayer;FLnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V")
    private int enderitemod$changeBowTime(ItemStack instance, LivingEntity user, Operation<Integer> original) {
        if (instance.is(EnderiteTools.ENDERITE_BOW.get())) {
            int maxTime = original.call(instance, user);
            int useTime = (maxTime - user.getUseItemRemainingTicks());
            int dx = (int) (EnderiteMod.CONFIG.tools.enderiteBowChargeTime - 20);
            if (dx <= 0) {
                // Faster than vanilla bow, fast pulling
                int dt = (int) (dx / EnderiteMod.CONFIG.tools.enderiteBowChargeTime * useTime);
                dt = Math.max(dt, dx);
                return maxTime - dt;
            } else {
                // Slower than vanilla bow, cyclic pulling
                int p = 14;
                int thr = 13;
                if (useTime < thr) {
                    return maxTime;
                } else if (useTime < EnderiteMod.CONFIG.tools.enderiteBowChargeTime) {
                    int dt = -enderitemod$triangleWave(useTime - thr, p);
                    return maxTime - dt;
                } else {
                    return maxTime - enderitemod$triangleWave((int) (EnderiteMod.CONFIG.tools.enderiteBowChargeTime - thr), p);
                }
            }
        } else {
            return original.call(instance, user);
        }
    }

    @Unique
    private static int enderitemod$triangleWave(int x, float p) {
        float h_p = p / 2;
        if (x % p <= h_p) return (int) (Math.floor((-x + 3 * p / 4) / p) * p);
        return (int) ((-x % p) - x + p);
    }
}
