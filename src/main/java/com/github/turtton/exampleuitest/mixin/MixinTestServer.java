package com.github.turtton.exampleuitest.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.test.TestServer;
import net.minecraft.world.GameMode;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.net.InetAddress;

@Mixin(TestServer.class)
public class MixinTestServer {
    @Shadow @Final private static Logger LOGGER;

    @Inject(
            method = "setupServer",
            at = @At("HEAD"),
            cancellable = true
    )
    private void enableLocalPlayerConnecting(CallbackInfoReturnable<Boolean> cir) throws IOException {
        var server = (TestServer)(Object)this;
        server.setServerIp("127.0.0.1");
        server.setPvpEnabled(true);
        server.setFlightEnabled(true);
//        server.setResourcePack();
//        server.setMotd();
        server.setPlayerIdleTimeout(0);
        var gamemode = GameMode.DEFAULT;
        server.getSaveProperties().setGameMode(gamemode);
        LOGGER.info("Default game type: {}", gamemode);

        var inetAddress = InetAddress.getByName(server.getServerIp());
        server.setServerPort(25565);
        server.generateKeyPair();
        LOGGER.info("Starting Minecraft server on {}:{}", server.getServerIp().isEmpty() ? "*" : server.getServerIp(), server.getServerPort());

        try{
            server.getNetworkIo().bind(inetAddress, server.getServerPort());
        } catch (Exception exception) {
            LOGGER.warn("**** FAILED TO BIND TO PORT!");
            LOGGER.warn("The exception was: {}", exception.toString());
            LOGGER.warn("Perhaps a server is already running on that port?");
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
