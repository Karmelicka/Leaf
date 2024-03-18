package net.minecraft.world.item.crafting;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.chars.CharArraySet;
import it.unimi.dsi.fastutil.chars.CharSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.inventory.CraftingContainer;

public record ShapedRecipePattern(int width, int height, NonNullList<Ingredient> ingredients, Optional<ShapedRecipePattern.Data> data) {
    private static final int MAX_SIZE = 3;
    public static final MapCodec<ShapedRecipePattern> MAP_CODEC = ShapedRecipePattern.Data.MAP_CODEC.flatXmap(ShapedRecipePattern::unpack, (recipe) -> {
        return recipe.data().map(DataResult::success).orElseGet(() -> {
            return DataResult.error(() -> {
                return "Cannot encode unpacked recipe";
            });
        });
    });

    public static ShapedRecipePattern of(Map<Character, Ingredient> key, String... pattern) {
        return of(key, List.of(pattern));
    }

    public static ShapedRecipePattern of(Map<Character, Ingredient> key, List<String> pattern) {
        ShapedRecipePattern.Data data = new ShapedRecipePattern.Data(key, pattern);
        return Util.getOrThrow(unpack(data), IllegalArgumentException::new);
    }

    private static DataResult<ShapedRecipePattern> unpack(ShapedRecipePattern.Data data) {
        String[] strings = shrink(data.pattern);
        int i = strings[0].length();
        int j = strings.length;
        NonNullList<Ingredient> nonNullList = NonNullList.withSize(i * j, Ingredient.EMPTY);
        CharSet charSet = new CharArraySet(data.key.keySet());

        for(int k = 0; k < strings.length; ++k) {
            String string = strings[k];

            for(int l = 0; l < string.length(); ++l) {
                char c = string.charAt(l);
                Ingredient ingredient = c == ' ' ? Ingredient.EMPTY : data.key.get(c);
                if (ingredient == null) {
                    return DataResult.error(() -> {
                        return "Pattern references symbol '" + c + "' but it's not defined in the key";
                    });
                }

                charSet.remove(c);
                nonNullList.set(l + i * k, ingredient);
            }
        }

        return !charSet.isEmpty() ? DataResult.error(() -> {
            return "Key defines symbols that aren't used in pattern: " + charSet;
        }) : DataResult.success(new ShapedRecipePattern(i, j, nonNullList, Optional.of(data)));
    }

    @VisibleForTesting
    static String[] shrink(List<String> pattern) {
        int i = Integer.MAX_VALUE;
        int j = 0;
        int k = 0;
        int l = 0;

        for(int m = 0; m < pattern.size(); ++m) {
            String string = pattern.get(m);
            i = Math.min(i, firstNonSpace(string));
            int n = lastNonSpace(string);
            j = Math.max(j, n);
            if (n < 0) {
                if (k == m) {
                    ++k;
                }

                ++l;
            } else {
                l = 0;
            }
        }

        if (pattern.size() == l) {
            return new String[0];
        } else {
            String[] strings = new String[pattern.size() - l - k];

            for(int o = 0; o < strings.length; ++o) {
                strings[o] = pattern.get(o + k).substring(i, j + 1);
            }

            return strings;
        }
    }

    private static int firstNonSpace(String line) {
        int i;
        for(i = 0; i < line.length() && line.charAt(i) == ' '; ++i) {
        }

        return i;
    }

    private static int lastNonSpace(String line) {
        int i;
        for(i = line.length() - 1; i >= 0 && line.charAt(i) == ' '; --i) {
        }

        return i;
    }

    public boolean matches(CraftingContainer inventory) {
        for(int i = 0; i <= inventory.getWidth() - this.width; ++i) {
            for(int j = 0; j <= inventory.getHeight() - this.height; ++j) {
                if (this.matches(inventory, i, j, true)) {
                    return true;
                }

                if (this.matches(inventory, i, j, false)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean matches(CraftingContainer inventory, int offsetX, int offsetY, boolean flipped) {
        for(int i = 0; i < inventory.getWidth(); ++i) {
            for(int j = 0; j < inventory.getHeight(); ++j) {
                int k = i - offsetX;
                int l = j - offsetY;
                Ingredient ingredient = Ingredient.EMPTY;
                if (k >= 0 && l >= 0 && k < this.width && l < this.height) {
                    if (flipped) {
                        ingredient = this.ingredients.get(this.width - k - 1 + l * this.width);
                    } else {
                        ingredient = this.ingredients.get(k + l * this.width);
                    }
                }

                if (!ingredient.test(inventory.getItem(i + j * inventory.getWidth()))) {
                    return false;
                }
            }
        }

        return true;
    }

    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeVarInt(this.width);
        buf.writeVarInt(this.height);

        for(Ingredient ingredient : this.ingredients) {
            ingredient.toNetwork(buf);
        }

    }

    public static ShapedRecipePattern fromNetwork(FriendlyByteBuf buf) {
        int i = buf.readVarInt();
        int j = buf.readVarInt();
        NonNullList<Ingredient> nonNullList = NonNullList.withSize(i * j, Ingredient.EMPTY);
        nonNullList.replaceAll((ingredient) -> {
            return Ingredient.fromNetwork(buf);
        });
        return new ShapedRecipePattern(i, j, nonNullList, Optional.empty());
    }

    public static record Data(Map<Character, Ingredient> key, List<String> pattern) {
        private static final Codec<List<String>> PATTERN_CODEC = Codec.STRING.listOf().comapFlatMap((pattern) -> {
            if (pattern.size() > 3) {
                return DataResult.error(() -> {
                    return "Invalid pattern: too many rows, 3 is maximum";
                });
            } else if (pattern.isEmpty()) {
                return DataResult.error(() -> {
                    return "Invalid pattern: empty pattern not allowed";
                });
            } else {
                int i = pattern.get(0).length();

                for(String string : pattern) {
                    if (string.length() > 3) {
                        return DataResult.error(() -> {
                            return "Invalid pattern: too many columns, 3 is maximum";
                        });
                    }

                    if (i != string.length()) {
                        return DataResult.error(() -> {
                            return "Invalid pattern: each row must be the same width";
                        });
                    }
                }

                return DataResult.success(pattern);
            }
        }, Function.identity());
        private static final Codec<Character> SYMBOL_CODEC = Codec.STRING.comapFlatMap((keyEntry) -> {
            if (keyEntry.length() != 1) {
                return DataResult.error(() -> {
                    return "Invalid key entry: '" + keyEntry + "' is an invalid symbol (must be 1 character only).";
                });
            } else {
                return " ".equals(keyEntry) ? DataResult.error(() -> {
                    return "Invalid key entry: ' ' is a reserved symbol.";
                }) : DataResult.success(keyEntry.charAt(0));
            }
        }, String::valueOf);
        public static final MapCodec<ShapedRecipePattern.Data> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(ExtraCodecs.strictUnboundedMap(SYMBOL_CODEC, Ingredient.CODEC_NONEMPTY).fieldOf("key").forGetter((data) -> {
                return data.key;
            }), PATTERN_CODEC.fieldOf("pattern").forGetter((data) -> {
                return data.pattern;
            })).apply(instance, ShapedRecipePattern.Data::new);
        });
    }
}
