package net.minecraft.network.chat.contents;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

public class TranslatableContents implements ComponentContents {
    public static final Object[] NO_ARGS = new Object[0];
    private static final Codec<Object> PRIMITIVE_ARG_CODEC = ExtraCodecs.validate(ExtraCodecs.JAVA, TranslatableContents::filterAllowedArguments);
    private static final Codec<Object> ARG_CODEC = Codec.either(PRIMITIVE_ARG_CODEC, ComponentSerialization.CODEC).xmap((either) -> {
        return either.map((object) -> {
            return object;
        }, (text) -> {
            return Objects.requireNonNullElse(text.tryCollapseToString(), text);
        });
    }, (argument) -> {
        Either var10000;
        if (argument instanceof Component component) {
            var10000 = Either.right(component);
        } else {
            var10000 = Either.left(argument);
        }

        return var10000;
    });
    public static final MapCodec<TranslatableContents> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.STRING.fieldOf("translate").forGetter((content) -> {
            return content.key;
        }), Codec.STRING.optionalFieldOf("fallback").forGetter((content) -> {
            return Optional.ofNullable(content.fallback);
        }), ExtraCodecs.strictOptionalField(ARG_CODEC.listOf(), "with").forGetter((content) -> {
            return adjustArgs(content.args);
        })).apply(instance, TranslatableContents::create);
    });
    public static final ComponentContents.Type<TranslatableContents> TYPE = new ComponentContents.Type<>(CODEC, "translatable");
    private static final FormattedText TEXT_PERCENT = FormattedText.of("%");
    private static final FormattedText TEXT_NULL = FormattedText.of("null");
    private final String key;
    @Nullable
    private final String fallback;
    private final Object[] args;
    @Nullable
    private Language decomposedWith;
    private List<FormattedText> decomposedParts = ImmutableList.of();
    private static final Pattern FORMAT_PATTERN = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    public static DataResult<Object> filterAllowedArguments(@Nullable Object object) {
        return !isAllowedPrimitiveArgument(object) ? DataResult.error(() -> {
            return "This value needs to be parsed as component";
        }) : DataResult.success(object);
    }

    public static boolean isAllowedPrimitiveArgument(@Nullable Object argument) {
        return argument instanceof Number || argument instanceof Boolean || argument instanceof String;
    }

    private static Optional<List<Object>> adjustArgs(Object[] args) {
        return args.length == 0 ? Optional.empty() : Optional.of(Arrays.asList(args));
    }

    private static Object[] adjustArgs(Optional<List<Object>> args) {
        return args.map((list) -> {
            return list.isEmpty() ? NO_ARGS : list.toArray();
        }).orElse(NO_ARGS);
    }

    private static TranslatableContents create(String key, Optional<String> fallback, Optional<List<Object>> args) {
        return new TranslatableContents(key, fallback.orElse((String)null), adjustArgs(args));
    }

    public TranslatableContents(String key, @Nullable String fallback, Object[] args) {
        this.key = key;
        this.fallback = fallback;
        this.args = args;
    }

    @Override
    public ComponentContents.Type<?> type() {
        return TYPE;
    }

    private void decompose() {
        Language language = Language.getInstance();
        if (language != this.decomposedWith) {
            this.decomposedWith = language;
            String string = this.fallback != null ? language.getOrDefault(this.key, this.fallback) : language.getOrDefault(this.key);

            try {
                ImmutableList.Builder<FormattedText> builder = ImmutableList.builder();
                this.decomposeTemplate(string, builder::add);
                this.decomposedParts = builder.build();
            } catch (TranslatableFormatException var4) {
                this.decomposedParts = ImmutableList.of(FormattedText.of(string));
            }

        }
    }

    private void decomposeTemplate(String translation, Consumer<FormattedText> partsConsumer) {
        Matcher matcher = FORMAT_PATTERN.matcher(translation);

        try {
            int i = 0;

            int j;
            int l;
            for(j = 0; matcher.find(j); j = l) {
                int k = matcher.start();
                l = matcher.end();
                if (k > j) {
                    String string = translation.substring(j, k);
                    if (string.indexOf(37) != -1) {
                        throw new IllegalArgumentException();
                    }

                    partsConsumer.accept(FormattedText.of(string));
                }

                String string2 = matcher.group(2);
                String string3 = translation.substring(k, l);
                if ("%".equals(string2) && "%%".equals(string3)) {
                    partsConsumer.accept(TEXT_PERCENT);
                } else {
                    if (!"s".equals(string2)) {
                        throw new TranslatableFormatException(this, "Unsupported format: '" + string3 + "'");
                    }

                    String string4 = matcher.group(1);
                    int m = string4 != null ? Integer.parseInt(string4) - 1 : i++;
                    partsConsumer.accept(this.getArgument(m));
                }
            }

            if (j < translation.length()) {
                String string5 = translation.substring(j);
                if (string5.indexOf(37) != -1) {
                    throw new IllegalArgumentException();
                }

                partsConsumer.accept(FormattedText.of(string5));
            }

        } catch (IllegalArgumentException var12) {
            throw new TranslatableFormatException(this, var12);
        }
    }

    private FormattedText getArgument(int index) {
        if (index >= 0 && index < this.args.length) {
            Object object = this.args[index];
            if (object instanceof Component) {
                return (Component)object;
            } else {
                return object == null ? TEXT_NULL : FormattedText.of(object.toString());
            }
        } else {
            throw new TranslatableFormatException(this, index);
        }
    }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> visitor, Style style) {
        this.decompose();

        for(FormattedText formattedText : this.decomposedParts) {
            Optional<T> optional = formattedText.visit(visitor, style);
            if (optional.isPresent()) {
                return optional;
            }
        }

        return Optional.empty();
    }

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> visitor) {
        // Paper start
        try {
            return this.visit(new TranslatableContentConsumer<>(visitor));
        } catch (IllegalArgumentException var5) {
            return Optional.empty();
        }
    }
    private <T> Optional<T> visit(TranslatableContentConsumer<T> visitor) {
        // Paper end
        this.decompose();

        for(FormattedText formattedText : this.decomposedParts) {
            Optional<T> optional = formattedText.visit(visitor);
            if (optional.isPresent()) {
                return optional;
            }
        }

        return Optional.empty();
    }
    // Paper start
    private static final class TranslatableContentConsumer<T> implements FormattedText.ContentConsumer<T> {
        private final FormattedText.ContentConsumer<T> visitor;
        private int visited;

        private TranslatableContentConsumer(FormattedText.ContentConsumer<T> visitor) {
            this.visitor = visitor;
        }

        @Override
        public Optional<T> accept(final String asString) {
            if (visited++ > 32) {
                throw new IllegalArgumentException("Too long");
            }
            return this.visitor.accept(asString);
        }
    }
    // Paper end

    @Override
    public MutableComponent resolve(@Nullable CommandSourceStack source, @Nullable Entity sender, int depth) throws CommandSyntaxException {
        Object[] objects = new Object[this.args.length];

        for(int i = 0; i < objects.length; ++i) {
            Object object = this.args[i];
            if (object instanceof Component component) {
                objects[i] = ComponentUtils.updateForEntity(source, component, sender, depth);
            } else {
                objects[i] = object;
            }
        }

        return MutableComponent.create(new TranslatableContents(this.key, this.fallback, objects));
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else {
            if (object instanceof TranslatableContents) {
                TranslatableContents translatableContents = (TranslatableContents)object;
                if (Objects.equals(this.key, translatableContents.key) && Objects.equals(this.fallback, translatableContents.fallback) && Arrays.equals(this.args, translatableContents.args)) {
                    return true;
                }
            }

            return false;
        }
    }

    @Override
    public int hashCode() {
        int i = Objects.hashCode(this.key);
        i = 31 * i + Objects.hashCode(this.fallback);
        return 31 * i + Arrays.hashCode(this.args);
    }

    @Override
    public String toString() {
        return "translation{key='" + this.key + "'" + (this.fallback != null ? ", fallback='" + this.fallback + "'" : "") + ", args=" + Arrays.toString(this.args) + "}";
    }

    public String getKey() {
        return this.key;
    }

    @Nullable
    public String getFallback() {
        return this.fallback;
    }

    public Object[] getArgs() {
        return this.args;
    }
}
