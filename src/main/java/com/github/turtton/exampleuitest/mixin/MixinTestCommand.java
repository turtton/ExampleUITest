package com.github.turtton.exampleuitest.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.TestCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TestCommand.class)
public class MixinTestCommand {
    /**
     * デバッグ環境の場合に登録されるコマンドだが，謎にパース失敗するしs2mcも混乱するしでいいことないので無効化
     */
    @Inject(
            method = "register",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void disableTestCommand(CommandDispatcher<ServerCommandSource> dispatcher, CallbackInfo cir) {
        cir.cancel();
    }
}
