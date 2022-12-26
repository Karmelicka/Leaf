package net.minecraft.network.chat;

import com.mojang.logging.LogUtils;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.util.SignatureUpdater;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.Signer;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.galemc.gale.configuration.GaleGlobalConfiguration;
import org.slf4j.Logger;

public class SignedMessageChain {
    private static final Logger LOGGER = LogUtils.getLogger();
    @Nullable
    private SignedMessageLink nextLink;
    private Instant lastTimeStamp = Instant.EPOCH;

    public SignedMessageChain(UUID sender, UUID sessionId) {
        this.nextLink = SignedMessageLink.root(sender, sessionId);
    }

    public SignedMessageChain.Encoder encoder(Signer signer) {
        return (body) -> {
            SignedMessageLink signedMessageLink = this.advanceLink();
            return signedMessageLink == null ? null : new MessageSignature(signer.sign((SignatureUpdater)((updatable) -> {
                PlayerChatMessage.updateSignature(updatable, signedMessageLink, body);
            })));
        };
    }

    public SignedMessageChain.Decoder decoder(ProfilePublicKey playerPublicKey) {
        SignatureValidator signatureValidator = playerPublicKey.createSignatureValidator();
        return (signature, body) -> {
            SignedMessageLink signedMessageLink = this.advanceLink();
            if (signedMessageLink == null) {
                throw new SignedMessageChain.DecodeException(Component.translatable("chat.disabled.chain_broken"), false); // Paper - diff on change (if disconnects, need a new kick event cause)
            } else if (playerPublicKey.data().hasExpired()) {
                throw new SignedMessageChain.DecodeException(Component.translatable("chat.disabled.expiredProfileKey"), false, org.bukkit.event.player.PlayerKickEvent.Cause.EXPIRED_PROFILE_PUBLIC_KEY); // Paper - kick event causes
            } else if (body.timeStamp().isBefore(this.lastTimeStamp) && GaleGlobalConfiguration.get().misc.verifyChatOrder) { // Gale - Pufferfish - make chat order verification configurable
                throw new SignedMessageChain.DecodeException(Component.translatable("multiplayer.disconnect.out_of_order_chat"), true, org.bukkit.event.player.PlayerKickEvent.Cause.OUT_OF_ORDER_CHAT); // Paper - kick event causes
            } else {
                this.lastTimeStamp = body.timeStamp();
                PlayerChatMessage playerChatMessage = new PlayerChatMessage(signedMessageLink, signature, body, (Component)null, FilterMask.PASS_THROUGH);
                if (!playerChatMessage.verify(signatureValidator)) {
                    throw new SignedMessageChain.DecodeException(Component.translatable("multiplayer.disconnect.unsigned_chat"), true, org.bukkit.event.player.PlayerKickEvent.Cause.UNSIGNED_CHAT); // Paper - kick event causes
                } else {
                    if (playerChatMessage.hasExpiredServer(Instant.now())) {
                        LOGGER.warn("Received expired chat: '{}'. Is the client/server system time unsynchronized?", (Object)body.content());
                    }

                    return playerChatMessage;
                }
            }
        };
    }

    @Nullable
    private SignedMessageLink advanceLink() {
        SignedMessageLink signedMessageLink = this.nextLink;
        if (signedMessageLink != null) {
            this.nextLink = signedMessageLink.advance();
        }

        return signedMessageLink;
    }

    public static class DecodeException extends ThrowingComponent {
        private final boolean shouldDisconnect;
        public final org.bukkit.event.player.PlayerKickEvent.Cause kickCause; // Paper - kick event causes

        public DecodeException(Component message, boolean shouldDisconnect) {
            // Paper start - kick event causes
            this(message, shouldDisconnect, org.bukkit.event.player.PlayerKickEvent.Cause.UNKNOWN);
        }
        public DecodeException(Component message, boolean shouldDisconnect, org.bukkit.event.player.PlayerKickEvent.Cause kickCause) {
            // Paper end - kick event causes
            super(message);
            this.shouldDisconnect = shouldDisconnect;
            this.kickCause = kickCause; // Paper - kick event causes
        }

        public boolean shouldDisconnect() {
            return this.shouldDisconnect;
        }
    }

    @FunctionalInterface
    public interface Decoder {
        static SignedMessageChain.Decoder unsigned(UUID sender, BooleanSupplier secureProfileEnforced) {
            return (signature, body) -> {
                if (secureProfileEnforced.getAsBoolean()) {
                    throw new SignedMessageChain.DecodeException(Component.translatable("chat.disabled.missingProfileKey"), false);
                } else {
                    return PlayerChatMessage.unsigned(sender, body.content());
                }
            };
        }

        PlayerChatMessage unpack(@Nullable MessageSignature signature, SignedMessageBody body) throws SignedMessageChain.DecodeException;
    }

    @FunctionalInterface
    public interface Encoder {
        SignedMessageChain.Encoder UNSIGNED = (body) -> {
            return null;
        };

        @Nullable
        MessageSignature pack(SignedMessageBody body);
    }
}
