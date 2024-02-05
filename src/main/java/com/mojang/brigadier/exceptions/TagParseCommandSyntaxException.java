package com.mojang.brigadier.exceptions;

import com.mojang.brigadier.LiteralMessage;
import net.minecraft.network.chat.Component;

public final class TagParseCommandSyntaxException extends CommandSyntaxException {

    private static final SimpleCommandExceptionType EXCEPTION_TYPE = new SimpleCommandExceptionType(new LiteralMessage("Error parsing NBT"));

    public TagParseCommandSyntaxException(final String message) {
        super(EXCEPTION_TYPE, Component.literal(message));
    }
}
